
package com.insurance.quotation.service.automation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.IncomingEmail;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.RequestFieldValue;
import com.insurance.quotation.entity.RequiredField;
import com.insurance.quotation.entity.enums.FieldDataType;
import com.insurance.quotation.entity.enums.FieldSource;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.mailbox.EmailMessage;
import com.insurance.quotation.repository.IncomingEmailRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.repository.RequestFieldValueRepository;
import com.insurance.quotation.repository.RequiredFieldRepository;
import com.insurance.quotation.service.client.ClientMasterService;
import com.insurance.quotation.service.email.EmailClassificationService;
import com.insurance.quotation.service.extraction.EmailExtractionService;
import com.insurance.quotation.service.extraction.ExtractedFieldData;
import com.insurance.quotation.service.ingestion.EmailIngestionService;
import com.insurance.quotation.service.master.InsuranceTypeDef;
import com.insurance.quotation.service.master.MasterDataService;
import com.insurance.quotation.service.quotation.QuotationEmailService;
import com.insurance.quotation.service.quotation.QuotationWorkflowService;
import com.insurance.quotation.service.validation.ValidationService;

@Service
public class AutomationService {

    private static final Logger log =
            LoggerFactory.getLogger(AutomationService.class);

    public static final int MAX_RETRIES = 3;

    /*
     * A reply must contain an explicit request reference:
     *
     * IQAS-REQUEST-318
     * IQAS-REQ-318
     */
    private static final Pattern REQUEST_REFERENCE_PATTERN =
            Pattern.compile(
                    "IQAS-(?:REQUEST|REQ)-(\\d+)",
                    Pattern.CASE_INSENSITIVE
            );

    private final EmailIngestionService ingestion;
    private final EmailExtractionService extraction;
    private final EmailClassificationService classifier;
    private final ClientMasterService clientMaster;
    private final MasterDataService masterData;
    private final QuotationWorkflowService workflow;
    private final QuotationEmailService emailService;
    private final RequiredFieldRepository requiredFieldRepository;
    private final IncomingEmailRepository incomingEmailRepository;
    private final QuotationRequestRepository requestRepository;
    private final RequestFieldValueRepository fieldValueRepository;

    private final ReentrantLock running = new ReentrantLock();

    public AutomationService(
            EmailIngestionService ingestion,
            EmailExtractionService extraction,
            EmailClassificationService classifier,
            ClientMasterService clientMaster,
            MasterDataService masterData,
            QuotationWorkflowService workflow,
            QuotationEmailService emailService,
            RequiredFieldRepository requiredFieldRepository,
            IncomingEmailRepository incomingEmailRepository,
            QuotationRequestRepository requestRepository,
            RequestFieldValueRepository fieldValueRepository) {

        this.ingestion = ingestion;
        this.extraction = extraction;
        this.classifier = classifier;
        this.clientMaster = clientMaster;
        this.masterData = masterData;
        this.workflow = workflow;
        this.emailService = emailService;
        this.requiredFieldRepository = requiredFieldRepository;
        this.incomingEmailRepository = incomingEmailRepository;
        this.requestRepository = requestRepository;
        this.fieldValueRepository = fieldValueRepository;
    }

    // ---------------------------------------------------------
    // PROCESS INCOMING EMAILS
    // ---------------------------------------------------------

    public void processIncomingEmails() {

        if (!running.tryLock()) {
            log.info("Email processing already running - skipped");
            return;
        }

        try {
            List<EmailMessage> emails =
                    ingestion.fetchIncomingEmails();

            for (EmailMessage email : emails) {

                Optional<IncomingEmail> existing =
                        incomingEmailRepository.findByMessageId(
                                email.getMessageId());

                if (existing.isPresent()) {

                    String status =
                            existing.get().getProcessingStatus();

                    boolean done =
                            "PROCESSED".equals(status)
                                    || "IGNORED".equals(status);

                    boolean exhausted =
                            "FAILED".equals(status)
                                    && existing.get().getRetryCount()
                                    >= MAX_RETRIES;

                    if (done || exhausted) {
                        markProcessedQuietly(email.getMessageId());
                        continue;
                    }
                }

                try {
                    processEmail(email, existing.orElse(null));

                } catch (Exception e) {
                    handleProcessingFailure(email, e);
                }
            }

        } finally {
            running.unlock();
        }
    }

    // ---------------------------------------------------------
    // PROCESS ONE EMAIL
    // ---------------------------------------------------------

    private void processEmail(
            EmailMessage email,
            IncomingEmail existingRow) {

        String sender = email.getSenderEmail() == null
                ? ""
                : email.getSenderEmail().trim().toLowerCase(Locale.ROOT);

        List<RequiredField> allActive =
                requiredFieldRepository
                        .findByIsActiveTrueOrderBySortOrderAscIdAsc();

        // Extract the information available in the email.
        List<ExtractedFieldData> firstPass =
                extraction.extractFields(email, allActive);

        log.info(
                "Extracted {} fields from email {}",
                firstPass.size(),
                email.getMessageId()
        );

        boolean useful = firstPass.stream().anyMatch(f ->
                !f.isMissing()
                        && f.getFieldValue() != null
                        && !f.getFieldValue().isBlank()
                        && !"policyType".equalsIgnoreCase(
                                f.getFieldName()));

        // -----------------------------------------------------
        // MATCH ONLY USING AN EXPLICIT REQUEST REFERENCE
        // -----------------------------------------------------

        Optional<QuotationRequest> pending =
                findPendingRequestByReference(email, sender);

        Optional<Client> knownClient;

        if (pending.isPresent()) {

            knownClient = Optional.of(
                    pending.get().getClient());

            log.info(
                    "Explicit request reference matched. "
                            + "Request ID: {}, Client ID: {}",
                    pending.get().getId(),
                    pending.get().getClient().getId()
            );

        } else {

            ClientMasterService.ClientDetails details =
                    toClientDetails(email, sender, firstPass);

            knownClient = clientMaster.find(details);

            log.info(
                    "No request reference found. "
                            + "Processing as a new request. "
                            + "Existing client found: {}",
                    knownClient.isPresent()
            );
        }

        // -----------------------------------------------------
        // CLASSIFY EMAIL
        // -----------------------------------------------------

        EmailClassificationService.Classification classification =
                classifier.classify(
                        email.getSubject(),
                        pending.isPresent(),
                        useful,
                        masterData.activeSubjectKeywords());

        IncomingEmail row = existingRow != null
                ? existingRow
                : newRow(email, sender);

        row.setEmailKind(classification.kind().name());

        if (classification.kind()
                == EmailClassificationService.Kind.UNRELATED) {

            row.setProcessingStatus("IGNORED");
            row.setLastError(null);

            incomingEmailRepository.save(row);
            markProcessedQuietly(email.getMessageId());

            log.info(
                    "Email from {} ignored: {}",
                    sender,
                    classification.reason()
            );

            return;
        }

        // -----------------------------------------------------
        // RESOLVE INSURANCE TYPE
        // -----------------------------------------------------

        String type =
                resolveType(firstPass, email, pending.orElse(null));

        // If there are no useful details and no existing request,
        // send the form link.
        if ((!useful || type == null) && pending.isEmpty()) {

            handleIncomplete(
                    email,
                    sender,
                    row,
                    knownClient.orElse(null),
                    type
            );

            return;
        }

        // -----------------------------------------------------
        // FIND OR APPLY CUSTOMER DETAILS
        // -----------------------------------------------------

        ClientMasterService.ClientDetails details =
                toClientDetails(email, sender, firstPass);

        ClientMasterService.ApplyResult applied;

        if (pending.isPresent()) {

            /*
             * Use the client attached to the explicitly referenced
             * request. Do not search for another pending request.
             */

            Client matchedClient =
                    pending.get().getClient();

            applied = clientMaster.apply(
                    matchedClient,
                    details,
                    "EMAIL",
                    sender,
                    false
            );

        } else if (knownClient.isPresent()) {

            applied = clientMaster.apply(
                    knownClient.get(),
                    details,
                    "EMAIL",
                    sender,
                    false
            );

        } else {

            applied = clientMaster.findOrCreateAndApply(
                    details,
                    "EMAIL",
                    sender,
                    false
            );
        }

        Client client = applied.client();

        if (applied.hasBlockedChanges()) {

            log.info(
                    "Client {}: different values received for {}. "
                            + "Saved values were retained.",
                    client.getClientCode(),
                    applied.blocked()
            );
        }

        // -----------------------------------------------------
        // FIND OR CREATE QUOTATION REQUEST
        // -----------------------------------------------------

        /*
         * Important:
         *
         * If an explicit request reference was supplied and validated,
         * update that request.
         *
         * Otherwise, ALWAYS create a new quotation request.
         *
         * Do not find an old request using sender email or client ID.
         */

        boolean isReply = pending.isPresent();

        QuotationRequest request;

        if (isReply) {

            request = pending.get();

            log.info(
                    "Customer reply matched explicitly referenced "
                            + "request ID: {}",
                    request.getId()
            );

            request.setStatus(
                    RequestStatus.CLIENT_REPLY_RECEIVED);

            row.setEmailKind(
                    EmailClassificationService.Kind.CLIENT_REPLY.name());

            // Preserve the insurance type of the original request.
            if (request.getPolicyType() != null
                    && !request.getPolicyType().isBlank()) {

                type = request.getPolicyType();
            }

        } else {

            request = new QuotationRequest();
            request.setClient(client);
            request.setStatus(RequestStatus.EMAIL_RECEIVED);
            request.setCreatedAt(LocalDateTime.now());

            log.info(
                    "Creating a new quotation request for client {}",
                    client.getId()
            );
        }

        request.setPolicyType(type);
        request.setUpdatedAt(LocalDateTime.now());

        request = requestRepository.save(request);

        row.setQuotationRequest(request);
        row.setProcessingStatus("RECEIVED");

        row = incomingEmailRepository.save(row);

        // -----------------------------------------------------
        // INSURANCE TYPE UNKNOWN
        // -----------------------------------------------------

        if (type == null) {

            request.setStatus(RequestStatus.MISSING_INFORMATION);

            requestRepository.save(request);

            emailService.sendFormLinkEmail(
                    sender,
                    client,
                    null,
                    request.getId()
            );

            finish(row, email);
            return;
        }

        // -----------------------------------------------------
        // EXTRACT FIELDS FOR SELECTED INSURANCE TYPE
        // -----------------------------------------------------

        final String finalType = type;

        List<RequiredField> forType =
                allActive.stream()
                        .filter(f ->
                                ValidationService.appliesTo(
                                        f,
                                        finalType))
                        .toList();

        List<ExtractedFieldData> fields =
                extraction.extractFields(email, forType);

        log.info(
                "Extracted {} fields for insurance type {}",
                fields.size(),
                type
        );

        // -----------------------------------------------------
        // MERGE PREVIOUS ANSWERS WITH CUSTOMER REPLY
        // -----------------------------------------------------

        if (isReply) {

            log.info(
                    "Merging previous answers for request {}",
                    request.getId()
            );

            fields = mergeExistingValues(request, fields);
        }

        fields = withPolicyType(fields, type);

        log.info(
                "Total fields sent for validation: {}",
                fields.size()
        );

        // -----------------------------------------------------
        // VALIDATE AND GENERATE QUOTATION
        // -----------------------------------------------------

        QuotationWorkflowService.Outcome outcome =
                workflow.process(
                        request,
                        client,
                        fields,
                        false
                );

        log.info(
                "Email {} -> request {} -> result {}",
                email.getMessageId(),
                request.getId(),
                outcome.result()
        );

        if (outcome.result()
                == QuotationWorkflowService.Result.EMAIL_SEND_FAILED
                || outcome.result()
                == QuotationWorkflowService.Result.QUOTATION_GENERATION_FAILED) {

            throw new IllegalStateException(
                    "Request " + request.getId()
                            + ": " + outcome.result()
            );
        }

        /*
         * MISSING_INFORMATION:
         * QuotationWorkflowService sends the personal form link.
         *
         * QUOTATION_SENT:
         * QuotationWorkflowService generates and sends the PDF.
         */

        finish(row, email);
    }

    // ---------------------------------------------------------
    // FIND REQUEST USING EXPLICIT REQUEST REFERENCE
    // ---------------------------------------------------------

    private Optional<QuotationRequest> findPendingRequestByReference(
            EmailMessage email,
            String sender) {

        String subject = email.getSubject() == null
                ? ""
                : email.getSubject();

        String body = email.getBody() == null
                ? ""
                : email.getBody();

        String content = subject + "\n" + body;

        Matcher matcher =
                REQUEST_REFERENCE_PATTERN.matcher(content);

        if (!matcher.find()) {

            log.info(
                    "No explicit quotation request reference found "
                            + "in email from {}",
                    sender
            );

            return Optional.empty();
        }

        Long requestId;

        try {

            requestId = Long.parseLong(matcher.group(1));

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "Invalid quotation request reference in email.",
                    e
            );
        }

        QuotationRequest request =
                requestRepository.findById(requestId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Referenced quotation request "
                                                + requestId
                                                + " was not found."
                                ));

        if (request.getClient() == null
                || request.getClient().getEmail() == null
                || !request.getClient().getEmail()
                        .trim()
                        .equalsIgnoreCase(sender)) {

            throw new IllegalArgumentException(
                    "The sender email does not match the client "
                            + "of referenced request " + requestId
            );
        }

        RequestStatus status = request.getStatus();

        boolean validStatus =
                status == RequestStatus.MISSING_INFORMATION
                        || status == RequestStatus.WAITING_FOR_CLIENT;

        if (!validStatus) {

            throw new IllegalArgumentException(
                    "Request " + requestId
                            + " cannot receive a reply because its "
                            + "current status is " + status
            );
        }

        log.info(
                "Validated explicit request reference {} for sender {}",
                requestId,
                sender
        );

        return Optional.of(request);
    }

    // ---------------------------------------------------------
    // INCOMPLETE EMAIL
    // ---------------------------------------------------------

    private void handleIncomplete(
            EmailMessage email,
            String sender,
            IncomingEmail row,
            Client knownClient,
            String type) {

        row.setQuotationRequest(null);
        row.setProcessingStatus("RECEIVED");

        row = incomingEmailRepository.save(row);

        /*
         * Every incomplete new quotation email from a human sender
         * receives a form link.
         *
         * No quotation request is created at this stage.
         */

        if (isHumanSender(sender)) {

            emailService.sendFormLinkEmail(
                    sender,
                    knownClient,
                    type,
                    null
            );

            log.info(
                    "Form link sent to {} because information is incomplete.",
                    sender
            );

        } else {

            log.info(
                    "Form link not sent because sender {} is automated.",
                    sender
            );
        }

        finish(row, email);
    }

    // ---------------------------------------------------------
    // COMPLETE EMAIL PROCESSING
    // ---------------------------------------------------------

    private void finish(
            IncomingEmail row,
            EmailMessage email) {

        row.setProcessingStatus("PROCESSED");
        row.setLastError(null);

        incomingEmailRepository.save(row);

        markProcessedQuietly(email.getMessageId());
    }

    // ---------------------------------------------------------
    // HANDLE FAILURE
    // ---------------------------------------------------------

    private void handleProcessingFailure(
            EmailMessage email,
            Exception e) {

        log.error(
                "Failed to process email {}",
                email.getMessageId(),
                e
        );

        IncomingEmail row =
                incomingEmailRepository
                        .findByMessageId(email.getMessageId())
                        .orElseGet(() ->
                                newRow(
                                        email,
                                        email.getSenderEmail() == null
                                                ? ""
                                                : email.getSenderEmail()
                                                        .trim()
                                                        .toLowerCase(Locale.ROOT)
                                ));

        row.setProcessingStatus("FAILED");
        row.setRetryCount(row.getRetryCount() + 1);

        row.setLastError(
                e.getClass().getSimpleName()
                        + ": " + e.getMessage()
        );

        incomingEmailRepository.save(row);

        QuotationRequest req = row.getQuotationRequest();

        if (req != null
                && req.getStatus()
                != RequestStatus.EMAIL_SEND_FAILED
                && req.getStatus()
                != RequestStatus.QUOTATION_GENERATION_FAILED) {

            req.setStatus(RequestStatus.EMAIL_PROCESSING_FAILED);
            req.setUpdatedAt(LocalDateTime.now());

            requestRepository.save(req);
        }

        if (row.getRetryCount() >= MAX_RETRIES) {
            markProcessedQuietly(email.getMessageId());
        }
    }

    // ---------------------------------------------------------
    // CREATE INCOMING EMAIL ROW
    // ---------------------------------------------------------

    private IncomingEmail newRow(
            EmailMessage email,
            String sender) {

        IncomingEmail row = new IncomingEmail();

        row.setMessageId(email.getMessageId());
        row.setInReplyTo(email.getInReplyTo());

        row.setSenderEmail(
                sender.isBlank() ? "unknown" : sender);

        row.setSubject(
                email.getSubject() == null
                        ? null
                        : email.getSubject().length() > 255
                                ? email.getSubject().substring(0, 255)
                                : email.getSubject()
        );

        row.setBody(email.getBody());
        row.setProcessingStatus("RECEIVED");

        row.setReceivedAt(
                email.getReceivedAt() == null
                        ? LocalDateTime.now()
                        : email.getReceivedAt()
        );

        return row;
    }

    // ---------------------------------------------------------
    // MARK GMAIL EMAIL AS PROCESSED
    // ---------------------------------------------------------

    private void markProcessedQuietly(String messageId) {

        try {
            ingestion.markEmailAsProcessed(messageId);

        } catch (Exception e) {

            log.warn(
                    "Could not mark {} as processed in mailbox: {}",
                    messageId,
                    e.getMessage()
            );
        }
    }

    // ---------------------------------------------------------
    // CLIENT DETAILS
    // ---------------------------------------------------------

    private ClientMasterService.ClientDetails toClientDetails(
            EmailMessage email,
            String sender,
            List<ExtractedFieldData> fields) {

        String idType = get(fields, "identityType");
        String idNumber = get(fields, "identityNumber");

        return new ClientMasterService.ClientDetails(
                get(fields, "name"),
                sender,
                get(fields, "phone"),
                parseDate(get(fields, "dateOfBirth")),
                null,
                get(fields, "nationality"),
                null,
                get(fields, "identityCountry"),
                idType,
                idNumber
        );
    }

    private static java.time.LocalDate parseDate(String value) {

        try {
            return value == null
                    ? null
                    : java.time.LocalDate.parse(value);

        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---------------------------------------------------------
    // INSURANCE TYPE
    // ---------------------------------------------------------

    private String resolveType(
            List<ExtractedFieldData> fields,
            EmailMessage email,
            QuotationRequest pending) {

        if (pending != null
                && pending.getPolicyType() != null
                && !pending.getPolicyType().isBlank()) {

            return pending.getPolicyType();
        }

        List<InsuranceTypeDef> types =
                masterData.insuranceTypes(true);

        String stated = get(fields, "policyType");

        if (stated != null) {

            for (InsuranceTypeDef t : types) {

                if (t.code().equalsIgnoreCase(stated.trim())
                        || t.label().equalsIgnoreCase(stated.trim())
                        || (t.label().toLowerCase(Locale.ROOT)
                                .startsWith(stated.trim().toLowerCase(Locale.ROOT))
                                && stated.trim().length() >= 4)) {

                    return t.code();
                }
            }
        }

        return classifier.detectInsuranceType(
                email.getSubject(),
                email.getBody(),
                types
        );
    }

    // ---------------------------------------------------------
    // ADD POLICY TYPE
    // ---------------------------------------------------------

    private List<ExtractedFieldData> withPolicyType(
            List<ExtractedFieldData> fields,
            String type) {

        List<ExtractedFieldData> out = new ArrayList<>();

        for (ExtractedFieldData f : fields) {

            if (!"policyType".equalsIgnoreCase(f.getFieldName())) {
                out.add(f);
            }
        }

        out.add(
                new ExtractedFieldData(
                        "policyType",
                        type,
                        FieldDataType.TEXT,
                        FieldSource.EMAIL_EXTRACTED,
                        1.0,
                        false
                )
        );

        return out;
    }

    // ---------------------------------------------------------
    // MERGE OLD VALUES AND NEW REPLY VALUES
    // ---------------------------------------------------------

    private List<ExtractedFieldData> mergeExistingValues(
            QuotationRequest request,
            List<ExtractedFieldData> latest) {

        Map<String, ExtractedFieldData> combined =
                new LinkedHashMap<>();

        List<RequestFieldValue> savedValues =
                fieldValueRepository.findByQuotationRequestId(
                        request.getId()
                );

        log.info(
                "Request {} has {} previously saved field values",
                request.getId(),
                savedValues.size()
        );

        // First, load the previously saved values.
        for (RequestFieldValue saved : savedValues) {

            if (saved.getRequiredField() == null
                    || saved.getFieldValue() == null
                    || saved.getFieldValue().isBlank()) {

                continue;
            }

            String name =
                    saved.getRequiredField().getFieldName();

            String key = normalize(name);

            combined.put(
                    key,
                    new ExtractedFieldData(
                            name,
                            saved.getFieldValue(),
                            saved.getRequiredField().getDataType(),
                            saved.getSource() != null
                                    ? saved.getSource()
                                    : FieldSource.EMAIL_EXTRACTED,
                            1.0,
                            false
                    )
            );

            log.info("Previous value found: {}", name);
        }

        // Next, apply values from the customer's latest reply.
        if (latest != null) {

            for (ExtractedFieldData field : latest) {

                if (field == null
                        || field.getFieldName() == null) {

                    continue;
                }

                String key =
                        normalize(field.getFieldName());

                String value = field.getFieldValue();

                boolean hasValue =
                        !field.isMissing()
                                && value != null
                                && !value.isBlank();

                if (hasValue) {

                    combined.put(key, field);

                    log.info(
                            "New reply value received for field: {}",
                            field.getFieldName()
                    );

                } else if (!combined.containsKey(key)) {

                    combined.put(key, field);
                }
            }
        }

        log.info(
                "After merging, request {} contains {} fields",
                request.getId(),
                combined.size()
        );

        return new ArrayList<>(combined.values());
    }

    // ---------------------------------------------------------
    // GET FIELD VALUE
    // ---------------------------------------------------------

    private static String get(
            List<ExtractedFieldData> fields,
            String name) {

        if (fields == null || name == null) {
            return null;
        }

        String requiredKey = normalize(name);

        return fields.stream()
                .filter(f -> f != null)
                .filter(f ->
                        requiredKey.equals(
                                normalize(f.getFieldName())))
                .filter(f ->
                        !f.isMissing()
                                && f.getFieldValue() != null
                                && !f.getFieldValue().isBlank())
                .map(ExtractedFieldData::getFieldValue)
                .findFirst()
                .orElse(null);
    }

    // ---------------------------------------------------------
    // NORMALIZE FIELD NAMES
    // ---------------------------------------------------------

    private static String normalize(String value) {

        if (value == null) {
            return "";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    // ---------------------------------------------------------
    // CHECK HUMAN SENDER
    // ---------------------------------------------------------

    static boolean isHumanSender(String sender) {

        if (sender == null || sender.isBlank()) {
            return false;
        }

        String value = sender.toLowerCase(Locale.ROOT);

        return !(value.contains("no-reply")
                || value.contains("noreply")
                || value.contains("donotreply")
                || value.contains("do-not-reply")
                || value.contains("mailer-daemon")
                || value.contains("postmaster"));
    }
}
