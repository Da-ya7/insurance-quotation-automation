
package com.insurance.quotation.service.automation;

import java.time.LocalDate;
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

        // FIRST EXTRACTION PASS
        List<ExtractedFieldData> firstPass =
                extraction.extractFields(email, allActive);

        if (firstPass == null) {
            firstPass = new ArrayList<>();
        }

        log.info(
                "Extracted {} fields from email {}",
                firstPass.size(),
                email.getMessageId()
        );

        logPersonalFieldStatus(firstPass, email.getMessageId());

        boolean useful = firstPass.stream().anyMatch(f ->
                f != null
                        && !f.isMissing()
                        && f.getFieldValue() != null
                        && !f.getFieldValue().isBlank()
                        && !"policyType".equalsIgnoreCase(
                                f.getFieldName()));

        // MATCH REQUEST REFERENCE
        Optional<QuotationRequest> pending =
                findPendingRequestByReference(email, sender);

        Optional<Client> knownClient;

        if (pending.isPresent()) {

            knownClient = Optional.of(pending.get().getClient());

            log.info(
                    "Explicit request reference matched. "
                            + "Request ID: {}, Client ID: {}",
                    pending.get().getId(),
                    pending.get().getClient().getId()
            );

        } else {

            ClientMasterService.ClientDetails details =
                    toClientDetails(sender, firstPass);

            knownClient = clientMaster.find(details);

            log.info(
                    "Existing client found: {}",
                    knownClient.isPresent()
            );
        }

        // CLASSIFY EMAIL
        EmailClassificationService.Classification classification =
                classifier.classify(
                        email.getSubject(),
                        email.getBody(),
                        pending.isPresent(),
                        useful,
                        masterData.activeSubjectKeywords());

        IncomingEmail row = existingRow != null
                ? existingRow
                : newRow(email, sender);

        row.setEmailKind(classification.kind().name());

        // -----------------------------------------------------
        // RESOLVE INSURANCE TYPE
        // -----------------------------------------------------

        String type =
                resolveType(firstPass, email, pending.orElse(null));

        // -----------------------------------------------------
        // RECOGNIZED INSURANCE TYPE MUST NOT BE IGNORED
        // -----------------------------------------------------

        if (type != null
                && classification.kind()
                        == EmailClassificationService.Kind.UNRELATED) {

            classification =
                    new EmailClassificationService.Classification(
                            EmailClassificationService.Kind.NEW_REQUEST,
                            classification.subjectMatched(),
                            "Insurance type detected: " + type
                    );

            row.setEmailKind(classification.kind().name());

            log.info(
                    "Email recognized as {} insurance request. "
                            + "Classification updated to NEW_REQUEST.",
                    type
            );
        }

        // -----------------------------------------------------
        // SAVE CLIENT DETAILS BEFORE ANY EARLY RETURN
        // -----------------------------------------------------

        Client client = knownClient.orElse(null);

        boolean hasProfileData =
                hasClientProfileData(firstPass);

        boolean shouldSaveClient =
                pending.isPresent()
                        || hasProfileData
                        || (useful
                                && classification.kind()
                                != EmailClassificationService.Kind.UNRELATED);

        if (shouldSaveClient) {

            ClientMasterService.ClientDetails details =
                    toClientDetails(sender, firstPass);

            ClientMasterService.ApplyResult applied;

            if (pending.isPresent()) {

                applied = clientMaster.apply(
                        pending.get().getClient(),
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

            client = applied.client();

            if (applied.hasBlockedChanges()) {

                log.info(
                        "Client {}: different values received for {}. "
                                + "Existing saved values were retained.",
                        client.getClientCode(),
                        applied.blocked()
                );
            }

            log.info(
                    "Client details applied. Client ID: {}, "
                            + "Profile data present: {}",
                    client.getId(),
                    hasProfileData
            );

        } else {

            log.info(
                    "No customer profile data found. "
                            + "Skipping client creation or update."
            );
        }

        // -----------------------------------------------------
        // UNRELATED EMAIL
        // -----------------------------------------------------

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
        // INCOMPLETE EMAIL
        // -----------------------------------------------------

        if ((!useful || type == null) && pending.isEmpty()) {

            handleIncomplete(
                    email,
                    sender,
                    row,
                    client,
                    type
            );

            return;
        }

        // -----------------------------------------------------
        // FIND OR CREATE QUOTATION REQUEST
        // -----------------------------------------------------

        boolean isReply = pending.isPresent();

        QuotationRequest request;

        if (isReply) {

            request = pending.get();

            log.info(
                    "Customer reply matched request ID: {}",
                    request.getId()
            );

            request.setStatus(
                    RequestStatus.CLIENT_REPLY_RECEIVED);

            row.setEmailKind(
                    EmailClassificationService.Kind.CLIENT_REPLY.name());

            if (request.getPolicyType() != null
                    && !request.getPolicyType().isBlank()) {

                type = request.getPolicyType();
            }

            client = request.getClient();

        } else {

            if (client == null) {
                throw new IllegalStateException(
                        "Cannot create quotation request without a client."
                );
            }

            request = new QuotationRequest();
            request.setClient(client);
            request.setStatus(RequestStatus.EMAIL_RECEIVED);
            request.setCreatedAt(LocalDateTime.now());

            log.info(
                    "Creating quotation request for client {}",
                    client.getId()
            );
        }

        request.setPolicyType(type);
        request.setUpdatedAt(LocalDateTime.now());

        request = requestRepository.save(request);

        row.setQuotationRequest(request);
        row.setProcessingStatus("RECEIVED");

        row = incomingEmailRepository.save(row);

        // INSURANCE TYPE UNKNOWN
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

        if (fields == null) {
            fields = new ArrayList<>();
        }

        log.info(
                "Extracted {} fields for insurance type {}",
                fields.size(),
                type
        );

        // MERGE PREVIOUS ANSWERS WITH CUSTOMER REPLY
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

        // VALIDATE AND GENERATE QUOTATION
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

        finish(row, email);
    }

    // ---------------------------------------------------------
    // CHECK WHETHER EMAIL CONTAINS PERSONAL PROFILE DATA
    // ---------------------------------------------------------

    private boolean hasClientProfileData(
            List<ExtractedFieldData> fields) {

        return getAny(
                fields,
                "name",
                "fullName",
                "clientName",
                "phone",
                "phoneNumber",
                "mobile",
                "mobileNumber",
                "dateOfBirth",
                "dob",
                "birthDate",
                "gender",
                "sex",
                "nationality",
                "nationalOrigin",
                "countryOfResidence",
                "countryOfResidency",
                "residenceCountry",
                "identityType",
                "identityDocumentType",
                "documentType",
                "idType",
                "identityCountry",
                "issuingCountry",
                "countryOfIssue",
                "identityNumber",
                "passportNumber",
                "documentNumber",
                "idNumber"
        ) != null;
    }

    // ---------------------------------------------------------
    // LOG PERSONAL FIELD EXTRACTION STATUS
    // ---------------------------------------------------------

    private void logPersonalFieldStatus(
            List<ExtractedFieldData> fields,
            String messageId) {

        log.info(
                "Personal field extraction status for email {}:",
                messageId
        );

        logFieldStatus(fields, "name", "fullName", "clientName");
        logFieldStatus(fields, "email", "emailAddress");
        logFieldStatus(fields, "phone", "phoneNumber", "mobile", "mobileNumber");
        logFieldStatus(fields, "dateOfBirth", "dob", "birthDate");
        logFieldStatus(fields, "gender", "sex");
        logFieldStatus(fields, "nationality", "nationalOrigin");

        logFieldStatus(
                fields,
                "countryOfResidence",
                "countryOfResidency",
                "residenceCountry"
        );

        logFieldStatus(
                fields,
                "identityType",
                "identityDocumentType",
                "documentType",
                "idType"
        );

        logFieldStatus(
                fields,
                "identityCountry",
                "issuingCountry",
                "countryOfIssue"
        );

        logFieldStatus(
                fields,
                "identityNumber",
                "passportNumber",
                "documentNumber",
                "idNumber"
        );
    }

    private void logFieldStatus(
            List<ExtractedFieldData> fields,
            String... names) {

        String matchedName = null;
        boolean found = false;

        if (fields != null) {

            for (String name : names) {

                String normalizedName = normalize(name);

                for (ExtractedFieldData field : fields) {

                    if (field == null
                            || field.getFieldName() == null) {
                        continue;
                    }

                    if (normalizedName.equals(
                            normalize(field.getFieldName()))) {

                        matchedName = field.getFieldName();

                        found = !field.isMissing()
                                && field.getFieldValue() != null
                                && !field.getFieldValue().isBlank();

                        break;
                    }
                }

                if (matchedName != null) {
                    break;
                }
            }
        }

        if (matchedName == null) {

            log.info(
                    "Personal field [{}]: NOT RETURNED BY EXTRACTION",
                    names[0]
            );

        } else {

            log.info(
                    "Personal field [{}]: returned=true, hasValue={}",
                    matchedName,
                    found
            );
        }
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
            String sender,
            List<ExtractedFieldData> fields) {

        String idNumber = getAny(
                fields,
                "identityNumber",
                "passportNumber",
                "documentNumber",
                "idNumber"
        );

        String idType = getAny(
                fields,
                "identityType",
                "identityDocumentType",
                "documentType",
                "idType"
        );

        if ((idType == null || idType.isBlank())
                && getAny(fields, "passportNumber") != null) {

            idType = "PASSPORT";
        }

        String extractedEmail = getAny(
                fields,
                "email",
                "emailAddress"
        );

        String email = sender;

        if (email == null || email.isBlank()) {
            email = extractedEmail;
        }

        return new ClientMasterService.ClientDetails(
                getAny(fields, "name", "fullName", "clientName"),
                email,
                getAny(fields, "phone", "phoneNumber", "mobile", "mobileNumber"),
                parseDate(getAny(fields, "dateOfBirth", "dob", "birthDate")),
                getAny(fields, "gender", "sex"),
                getAny(fields, "nationality", "nationalOrigin"),
                getAny(
                        fields,
                        "countryOfResidence",
                        "countryOfResidency",
                        "residenceCountry"
                ),
                getAny(
                        fields,
                        "identityCountry",
                        "issuingCountry",
                        "countryOfIssue"
                ),
                idType,
                idNumber
        );
    }

    private static LocalDate parseDate(String value) {

        try {
            return value == null
                    ? null
                    : LocalDate.parse(value.trim());

        } catch (RuntimeException e) {
            log.warn("Could not parse extracted date of birth.");
            return null;
        }
    }

    // ---------------------------------------------------------
    // INSURANCE TYPE DETECTION
    // ---------------------------------------------------------

    private String resolveType(
            List<ExtractedFieldData> fields,
            EmailMessage email,
            QuotationRequest pending) {

        // 1. Existing request: retain its insurance type.
        if (pending != null
                && pending.getPolicyType() != null
                && !pending.getPolicyType().isBlank()) {

            return pending.getPolicyType()
                    .trim()
                    .toUpperCase(Locale.ROOT);
        }

        List<InsuranceTypeDef> types =
                masterData.insuranceTypes(true);

        // 2. Check extracted policy type.
        String stated = get(fields, "policyType");

        if (stated != null && !stated.isBlank()) {

            String normalized = stated.trim();

            for (InsuranceTypeDef t : types) {

                if (t.code().equalsIgnoreCase(normalized)
                        || t.label().equalsIgnoreCase(normalized)
                        || (t.label().toLowerCase(Locale.ROOT)
                                .startsWith(normalized.toLowerCase(Locale.ROOT))
                                && normalized.length() >= 4)) {

                    return t.code()
                            .trim()
                            .toUpperCase(Locale.ROOT);
                }
            }
        }

        // 3. Search both subject and body.
        String content =
                (email.getSubject() == null ? "" : email.getSubject())
                        + " "
                        + (email.getBody() == null ? "" : email.getBody());

        // 4. Check database keywords BEFORE the classifier.
        String matchedType = null;
        int highestScore = 0;
        boolean ambiguous = false;

        for (InsuranceTypeDef t : types) {

            if (t.keywords() == null || t.keywords().isBlank()) {
                continue;
            }

            int score = 0;

            if (containsInsuranceKeyword(content, t.code())) {
                score += 10;
            }

            if (containsInsuranceKeyword(content, t.label())) {
                score += 10;
            }

            for (String keyword : t.keywords().split(",")) {

                String normalizedKeyword = keyword.trim();

                if (!normalizedKeyword.isEmpty()
                        && containsInsuranceKeyword(
                                content,
                                normalizedKeyword)) {

                    score += normalizedKeyword.length();

                    log.info(
                            "Insurance keyword matched: type={}, keyword={}",
                            t.code(),
                            normalizedKeyword
                    );
                }
            }

            if (score > highestScore) {

                highestScore = score;
                matchedType = t.code();
                ambiguous = false;

            } else if (score > 0
                    && score == highestScore
                    && matchedType != null
                    && !t.code().equalsIgnoreCase(matchedType)) {

                ambiguous = true;
            }
        }

        if (matchedType != null && !ambiguous) {

            log.info(
                    "Insurance type detected from subject/body keywords: {}",
                    matchedType
            );

            return matchedType
                    .trim()
                    .toUpperCase(Locale.ROOT);
        }

        if (ambiguous) {

            log.warn(
                    "Multiple insurance types matched with equal scores. "
                            + "Subject: {}",
                    email.getSubject()
            );

            return null;
        }

        // 5. Existing classifier is the final fallback.
        String detected = classifier.detectInsuranceType(
                email.getSubject(),
                email.getBody(),
                types
        );

        if (detected != null && !detected.isBlank()) {

            log.info(
                    "Insurance type detected by classifier: {}",
                    detected
            );

            return detected.trim().toUpperCase(Locale.ROOT);
        }

        log.warn(
                "Could not detect insurance type from email subject: {}",
                email.getSubject()
        );

        return null;
    }

    // ---------------------------------------------------------
    // MATCH INSURANCE KEYWORDS
    // ---------------------------------------------------------

    private static boolean containsInsuranceKeyword(
            String content,
            String keyword) {

        if (content == null || content.isBlank()
                || keyword == null || keyword.isBlank()) {

            return false;
        }

        String normalizedKeyword =
                keyword.trim().toLowerCase(Locale.ROOT);

        Pattern pattern = Pattern.compile(
                "(?<![a-z0-9])"
                        + Pattern.quote(normalizedKeyword)
                        + "(?![a-z0-9])",
                Pattern.CASE_INSENSITIVE
        );

        return pattern.matcher(content).find();
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

    private static String getAny(
            List<ExtractedFieldData> fields,
            String... names) {

        if (fields == null || names == null) {
            return null;
        }

        for (String name : names) {

            String value = get(fields, name);

            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

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
