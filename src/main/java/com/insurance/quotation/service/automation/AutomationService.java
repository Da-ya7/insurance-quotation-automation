package com.insurance.quotation.service.automation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

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

/**
 * Reads new mail and drives it through:
 *   classify (subject filter) -> identify client -> extract -> validate ->
 *   missing-details reply | quotation PDF + email.
 *
 * Safe to run repeatedly: an email is stored once (unique Message-ID), failed
 * emails are retried up to MAX_RETRIES times, and a request never gets a
 * second quotation.
 */
@Service
public class AutomationService {

    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);

    public static final int MAX_RETRIES = 3;

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

    /** Not one big transaction: one broken email must not undo or block the others. */
    public void processIncomingEmails() {

        // the scheduler and the manual button must never run at the same time
        if (!running.tryLock()) {
            log.info("Email processing already running - skipped");
            return;
        }
        try {
            List<EmailMessage> emails = ingestion.fetchIncomingEmails();

            for (EmailMessage email : emails) {
                Optional<IncomingEmail> existing =
                        incomingEmailRepository.findByMessageId(email.getMessageId());

                if (existing.isPresent()) {
                    String status = existing.get().getProcessingStatus();
                    boolean done = "PROCESSED".equals(status) || "IGNORED".equals(status);
                    boolean exhausted = "FAILED".equals(status)
                            && existing.get().getRetryCount() >= MAX_RETRIES;
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

    // ------------------------------------------------------------------

    private void processEmail(EmailMessage email, IncomingEmail existingRow) {

        String sender = email.getSenderEmail() == null
                ? "" : email.getSenderEmail().trim().toLowerCase();

        List<RequiredField> allActive =
                requiredFieldRepository.findByIsActiveTrueOrderBySortOrderAscIdAsc();

        // 1. extract once over every configured field to see what the mail contains
        List<ExtractedFieldData> firstPass = extraction.extractFields(email, allActive);
        boolean useful = firstPass.stream().anyMatch(f ->
                !f.isMissing() && f.getFieldValue() != null && !f.getFieldValue().isBlank()
                        && !"policyType".equalsIgnoreCase(f.getFieldName()));

        ClientMasterService.ClientDetails details = toClientDetails(email, sender, firstPass);
        Optional<Client> knownClient = clientMaster.find(details);
        Optional<QuotationRequest> pending = knownClient.flatMap(this::findPendingRequest);

        // 2. first-level filter: subject words, reply or unrelated
        EmailClassificationService.Classification classification = classifier.classify(
                email.getSubject(), pending.isPresent(), useful,
                masterData.activeSubjectKeywords());

        IncomingEmail row = existingRow != null ? existingRow : newRow(email, sender);
        row.setEmailKind(classification.kind().name());

        if (classification.kind() == EmailClassificationService.Kind.UNRELATED) {
            row.setProcessingStatus("IGNORED");
            row.setLastError(null);
            incomingEmailRepository.save(row);
            markProcessedQuietly(email.getMessageId());
            log.info("Email from {} ignored: {}", sender, classification.reason());
            return;
        }

        // 3. which insurance type?
        String type = resolveType(firstPass, email, pending.orElse(null));

        // 4. a quotation-related mail with no details (or no insurance type): reply with the form link
        if (!useful || type == null) {
            if (pending.isEmpty()) {
                handleIncomplete(email, sender, row, knownClient.orElse(null), type, existingRow == null);
                return;
            }
        }

        // 5. identify / create the client, reuse the open request on a reply
        ClientMasterService.ApplyResult applied =
                clientMaster.findOrCreateAndApply(details, "EMAIL", sender, false);
        Client client = applied.client();
        if (applied.hasBlockedChanges()) {
            log.info("Client {}: email had different values for {} - kept saved values (OTP needed to change)",
                    client.getClientCode(), applied.blocked());
        }

        Optional<QuotationRequest> openRequest = findPendingRequest(client);
        boolean isReply = openRequest.isPresent();

        QuotationRequest request;
        if (isReply) {
            request = openRequest.get();
            request.setStatus(RequestStatus.CLIENT_REPLY_RECEIVED);
            row.setEmailKind(EmailClassificationService.Kind.CLIENT_REPLY.name());
            if (request.getPolicyType() != null && !request.getPolicyType().isBlank()) {
                type = request.getPolicyType();
            }
        } else {
            request = new QuotationRequest();
            request.setClient(client);
            request.setStatus(RequestStatus.EMAIL_RECEIVED);
            request.setCreatedAt(LocalDateTime.now());
        }
        request.setPolicyType(type);
        request.setUpdatedAt(LocalDateTime.now());
        request = requestRepository.save(request);

        row.setQuotationRequest(request);
        row.setProcessingStatus("RECEIVED");
        row = incomingEmailRepository.save(row);

        // insurance type still unknown -> ask the client to choose it in the form
        if (type == null) {
            request.setStatus(RequestStatus.MISSING_INFORMATION);
            requestRepository.save(request);
            emailService.sendFormLinkEmail(sender, client, null, request.getId());
            finish(row, email);
            return;
        }

        // 6. extract the fields of THIS insurance type and merge with earlier answers
        final String finalType = type;
        List<RequiredField> forType = allActive.stream()
                .filter(f -> ValidationService.appliesTo(f, finalType)).toList();

        List<ExtractedFieldData> fields = extraction.extractFields(email, forType);
        if (isReply) {
            fields = mergeExistingValues(request, fields);
        }
        fields = withPolicyType(fields, type);

        // 7. validate -> missing-details reply, or quotation + PDF + email
        QuotationWorkflowService.Outcome outcome =
                workflow.process(request, client, fields, false);

        log.info("Email {} -> request {} -> {}", email.getMessageId(), request.getId(), outcome.result());

        if (outcome.result() == QuotationWorkflowService.Result.EMAIL_SEND_FAILED
                || outcome.result() == QuotationWorkflowService.Result.QUOTATION_GENERATION_FAILED) {
            // keep it retryable
            throw new IllegalStateException("Request " + request.getId() + ": " + outcome.result());
        }

        finish(row, email);
    }

    /** Quotation-related email without enough details: thank-you + personal form link (once per day). */
    private void handleIncomplete(
            EmailMessage email, String sender, IncomingEmail row,
            Client knownClient, String type, boolean firstAttempt) {

        boolean recentlyAnswered = firstAttempt
                && incomingEmailRepository
                .existsBySenderEmailAndEmailKindAndQuotationRequestIsNullAndCreatedAtAfter(
                        sender, EmailClassificationService.Kind.NEW_REQUEST.name(),
                        LocalDateTime.now().minusHours(24));

        row.setQuotationRequest(null);
        row.setProcessingStatus("RECEIVED");
        row = incomingEmailRepository.save(row);

        if (!recentlyAnswered && isHumanSender(sender)) {
            emailService.sendFormLinkEmail(sender, knownClient, type, null);
        }
        finish(row, email);
        log.info("Incomplete quotation email from {} answered with the form link", sender);
    }

    private void finish(IncomingEmail row, EmailMessage email) {
        row.setProcessingStatus("PROCESSED");
        row.setLastError(null);
        incomingEmailRepository.save(row);
        markProcessedQuietly(email.getMessageId());
    }

    private void handleProcessingFailure(EmailMessage email, Exception e) {
        log.error("Failed to process email {}", email.getMessageId(), e);

        IncomingEmail row = incomingEmailRepository.findByMessageId(email.getMessageId())
                .orElseGet(() -> newRow(email, email.getSenderEmail() == null
                        ? "" : email.getSenderEmail().trim().toLowerCase()));

        row.setProcessingStatus("FAILED");
        row.setRetryCount(row.getRetryCount() + 1);
        row.setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
        incomingEmailRepository.save(row);

        QuotationRequest req = row.getQuotationRequest();
        if (req != null && req.getStatus() != RequestStatus.EMAIL_SEND_FAILED
                && req.getStatus() != RequestStatus.QUOTATION_GENERATION_FAILED) {
            req.setStatus(RequestStatus.EMAIL_PROCESSING_FAILED);
            req.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(req);
        }

        // Leave it unread so the next run retries; give up after MAX_RETRIES.
        if (row.getRetryCount() >= MAX_RETRIES) {
            markProcessedQuietly(email.getMessageId());
        }
    }

    // ------------------------------------------------------------------

    private IncomingEmail newRow(EmailMessage email, String sender) {
        IncomingEmail row = new IncomingEmail();
        row.setMessageId(email.getMessageId());
        row.setInReplyTo(email.getInReplyTo());
        row.setSenderEmail(sender.isBlank() ? "unknown" : sender);
        row.setSubject(email.getSubject() == null ? null
                : email.getSubject().length() > 255 ? email.getSubject().substring(0, 255) : email.getSubject());
        row.setBody(email.getBody());
        row.setProcessingStatus("RECEIVED");
        row.setReceivedAt(email.getReceivedAt() == null ? LocalDateTime.now() : email.getReceivedAt());
        return row;
    }

    private void markProcessedQuietly(String messageId) {
        try {
            ingestion.markEmailAsProcessed(messageId);
        } catch (Exception e) {
            log.warn("Could not mark {} as processed in the mailbox: {}", messageId, e.getMessage());
        }
    }

    private ClientMasterService.ClientDetails toClientDetails(
            EmailMessage email, String sender, List<ExtractedFieldData> fields) {

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
                idNumber);
    }

    private static java.time.LocalDate parseDate(String v) {
        try {
            return v == null ? null : java.time.LocalDate.parse(v);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** policyType value in the mail, else the open request, else keywords in subject/body. */
    private String resolveType(
            List<ExtractedFieldData> fields, EmailMessage email, QuotationRequest pending) {

        if (pending != null && pending.getPolicyType() != null && !pending.getPolicyType().isBlank()) {
            return pending.getPolicyType();
        }
        List<InsuranceTypeDef> types = masterData.insuranceTypes(true);

        String stated = get(fields, "policyType");
        if (stated != null) {
            for (InsuranceTypeDef t : types) {
                if (t.code().equalsIgnoreCase(stated.trim()) || t.label().equalsIgnoreCase(stated.trim())
                        || (t.label().toLowerCase().startsWith(stated.trim().toLowerCase())
                        && stated.trim().length() >= 4)) {
                    return t.code();
                }
            }
        }
        return classifier.detectInsuranceType(email.getSubject(), email.getBody(), types);
    }

    private Optional<QuotationRequest> findPendingRequest(Client client) {
        return requestRepository.findFirstByClientIdAndStatusInOrderByIdDesc(
                client.getId(),
                List.of(RequestStatus.MISSING_INFORMATION, RequestStatus.WAITING_FOR_CLIENT));
    }

    private List<ExtractedFieldData> withPolicyType(List<ExtractedFieldData> fields, String type) {
        List<ExtractedFieldData> out = new ArrayList<>();
        for (ExtractedFieldData f : fields) {
            if (!"policyType".equalsIgnoreCase(f.getFieldName())) {
                out.add(f);
            }
        }
        out.add(new ExtractedFieldData("policyType", type, FieldDataType.TEXT,
                FieldSource.EMAIL_EXTRACTED, 1.0, false));
        return out;
    }

    /** Earlier answers stay; a new non-blank value in the latest email replaces them. */
    private List<ExtractedFieldData> mergeExistingValues(
            QuotationRequest request, List<ExtractedFieldData> latest) {

        Map<String, ExtractedFieldData> combined = new LinkedHashMap<>();

        for (RequestFieldValue saved : fieldValueRepository.findByQuotationRequestId(request.getId())) {
            if (saved.getRequiredField() == null || saved.getFieldValue() == null) {
                continue;
            }
            String name = saved.getRequiredField().getFieldName();
            combined.put(normalize(name), new ExtractedFieldData(
                    name, saved.getFieldValue(), saved.getRequiredField().getDataType(),
                    saved.getSource() != null ? saved.getSource() : FieldSource.EMAIL_EXTRACTED,
                    1.0, false));
        }

        for (ExtractedFieldData field : latest) {
            if (field.getFieldName() == null) {
                continue;
            }
            String key = normalize(field.getFieldName());
            boolean hasValue = !field.isMissing()
                    && field.getFieldValue() != null && !field.getFieldValue().isBlank();
            if (hasValue || !combined.containsKey(key)) {
                combined.put(key, field);
            }
        }
        return new ArrayList<>(combined.values());
    }

    private static String get(List<ExtractedFieldData> fields, String name) {
        return fields.stream()
                .filter(f -> name.equalsIgnoreCase(f.getFieldName()))
                .filter(f -> !f.isMissing() && f.getFieldValue() != null && !f.getFieldValue().isBlank())
                .map(ExtractedFieldData::getFieldValue)
                .findFirst().orElse(null);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    /** Never auto-reply to robots or bounce messages (avoids mail loops). */
    static boolean isHumanSender(String sender) {
        if (sender == null || sender.isBlank()) {
            return false;
        }
        String s = sender.toLowerCase();
        return !(s.contains("no-reply") || s.contains("noreply")
                || s.contains("donotreply") || s.contains("do-not-reply")
                || s.contains("mailer-daemon") || s.contains("postmaster"));
    }
}
