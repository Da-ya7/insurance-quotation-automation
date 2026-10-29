
package com.insurance.quotation.service.automation;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.IncomingEmail;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.RequestFieldValue;
import com.insurance.quotation.entity.enums.FieldSource;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.mailbox.EmailMessage;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.IncomingEmailRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.repository.RequestFieldValueRepository;
import com.insurance.quotation.service.extraction.EmailExtractionService;
import com.insurance.quotation.service.extraction.ExtractedFieldData;
import com.insurance.quotation.service.ingestion.EmailIngestionService;
import com.insurance.quotation.service.quotation.QuotationEmailService;
import com.insurance.quotation.service.quotation.QuotationGenerationService;
import com.insurance.quotation.service.validation.ValidationService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AutomationService {

    private final EmailIngestionService emailIngestionService;
    private final EmailExtractionService emailExtractionService;
    private final ValidationService validationService;
    private final QuotationGenerationService quotationGenerationService;
    private final QuotationEmailService quotationEmailService;

    private final ClientRepository clientRepository;
    private final IncomingEmailRepository incomingEmailRepository;
    private final QuotationRequestRepository quotationRequestRepository;
    private final RequestFieldValueRepository requestFieldValueRepository;

    private static final Pattern GREETING_ONLY = Pattern.compile(
            "(?iu)^\\s*(?:(?:hi|hello|hey|dear)"
                    + "(?:\\s+[\\p{L}]+)?"
                    + "[!,.\\s]*)+$"
    );

    /*
     * Recognises explicitly labelled fields such as:
     * Phone: 9876543210
     * Policy Type: Travel
     * Passport Number: P1234567
     *
     * This helps distinguish an actual field value from the words
     * "I need a policy quotation".
     */
    private static final Pattern EXPLICIT_FIELD_VALUE = Pattern.compile(
            "(?im)^\\s*(?:"
                    + "name|full name|customer name|phone|mobile|"
                    + "passport number|passport|origin country|"
                    + "destination country|travel start date|"
                    + "travel end date|travellers|travelers|"
                    + "policy type|nationality|country of residence|"
                    + "pre-existing medical conditions|"
                    + "purpose of travel|email|email address"
                    + ")\\s*:\\s*\\S.+$"
    );

    public AutomationService(
            EmailIngestionService emailIngestionService,
            EmailExtractionService emailExtractionService,
            ValidationService validationService,
            QuotationGenerationService quotationGenerationService,
            QuotationEmailService quotationEmailService,
            ClientRepository clientRepository,
            IncomingEmailRepository incomingEmailRepository,
            QuotationRequestRepository quotationRequestRepository,
            RequestFieldValueRepository requestFieldValueRepository) {

        this.emailIngestionService = emailIngestionService;
        this.emailExtractionService = emailExtractionService;
        this.validationService = validationService;
        this.quotationGenerationService = quotationGenerationService;
        this.quotationEmailService = quotationEmailService;
        this.clientRepository = clientRepository;
        this.incomingEmailRepository = incomingEmailRepository;
        this.quotationRequestRepository = quotationRequestRepository;
        this.requestFieldValueRepository = requestFieldValueRepository;
    }

    @Transactional
    public void processIncomingEmails() {

        List<EmailMessage> emails =
                emailIngestionService.fetchIncomingEmails();

        if (emails == null || emails.isEmpty()) {
            System.out.println("Automation: no incoming emails.");
            return;
        }

        for (EmailMessage email : emails) {

            if (email == null
                    || email.getMessageId() == null
                    || email.getMessageId().isBlank()) {

                System.out.println(
                        "Automation: skipping email without message ID.");
                continue;
            }

            String messageId = email.getMessageId();

            if (incomingEmailRepository.existsByMessageId(messageId)) {
                emailIngestionService.markEmailAsProcessed(messageId);
                continue;
            }

            try {
                processEmail(email);
            } catch (Exception exception) {
                handleProcessingFailure(email, exception);
            }
        }
    }

    private void processEmail(EmailMessage email) {

        String sender = email.getSenderEmail();

        if (sender == null || sender.isBlank()) {
            throw new IllegalArgumentException(
                    "Incoming email sender address is missing.");
        }

        String subject = email.getSubject() == null
                ? ""
                : email.getSubject().trim();

        String body = email.getBody() == null
                ? ""
                : email.getBody().trim();

        String latestBody = getLatestMessageText(body);
        String textToCheck = subject + "\n" + latestBody;

        System.out.println(
                "Automation: processing sender=" + sender
                        + ", subject=[" + subject + "]");

        // Find an existing client without creating a new one.
        Optional<Client> existingClient =
                clientRepository.findByEmail(sender);

        // Find a pending request for this sender, if any.
        Optional<QuotationRequest> pendingRequest =
                existingClient.flatMap(this::findPendingRequest);

        // Extract the fields from the incoming email.
        List<ExtractedFieldData> extractedFields =
                emailExtractionService.extractFields(email);

        if (extractedFields == null) {
            extractedFields = new ArrayList<>();
        } else {
            extractedFields = extractedFields.stream()
                    .filter(field -> field != null)
                    .toList();
        }

        boolean greetingOnly = isGreetingOnly(latestBody);

        boolean quotationIntent =
                hasQuotationIntent(textToCheck);

        boolean explicitFieldValue =
                hasExplicitFieldValue(latestBody);

        boolean actualDetails =
                hasActualCustomerDetails(
                        extractedFields,
                        explicitFieldValue);

        System.out.println(
                "Automation: greetingOnly=" + greetingOnly
                        + ", quotationIntent=" + quotationIntent
                        + ", explicitFieldValue=" + explicitFieldValue
                        + ", actualDetails=" + actualDetails
                        + ", pendingRequest=" + pendingRequest.isPresent());

        /*
         * IMPORTANT:
         * Greeting-only emails never enter validation, even if the
         * sender already has a pending request.
         */
        if (greetingOnly) {
            sendFormLinkAndFinish(email, sender);
            return;
        }

        /*
         * If the email contains no actual customer details, send
         * the form link rather than creating or validating a request.
         *
         * This also handles:
         * "I need a policy quotation"
         * "I need travel insurance"
         * "I want a travel quotation"
         *
         * Existing pending requests are left unchanged.
         */
        if (!actualDetails) {
            sendFormLinkAndFinish(email, sender);
            return;
        }

        // Only emails containing actual details enter this workflow.
        Client client = existingClient.orElseGet(
                () -> findOrCreateClient(email));

        boolean isReply = pendingRequest.isPresent();

        QuotationRequest request;

        if (isReply) {

            request = pendingRequest.get();

            System.out.println(
                    "Automation: continuing existing request "
                            + request.getId());

        } else {

            request = new QuotationRequest();

            request.setClient(client);
            request.setStatus(RequestStatus.EMAIL_RECEIVED);
            request.setCreatedAt(LocalDateTime.now());
            request.setUpdatedAt(LocalDateTime.now());

            request = quotationRequestRepository.save(request);

            System.out.println(
                    "Automation: created request " + request.getId());
        }

        // Avoid generating a second quotation for the same request.
        if (request.getQuotation() != null) {

            saveIncomingEmail(email, request, "PROCESSED");

            emailIngestionService.markEmailAsProcessed(
                    email.getMessageId());

            return;
        }

        // Save the incoming email against this quotation request.
        IncomingEmail incomingEmail =
                saveIncomingEmail(email, request, "RECEIVED");

        // Retain previously saved values when processing a reply.
        if (isReply) {
            extractedFields = mergeExistingValues(
                    request, extractedFields);
        }

        // Update the client with the extracted name and phone.
        updateClient(client, extractedFields);

        // Resolve the policy type without changing required-field rules.
        String policyType =
                getFieldValue(extractedFields, "policyType");

        if (policyType == null || policyType.isBlank()) {
            policyType = request.getPolicyType();
        }

        if (policyType == null || policyType.isBlank()) {
            policyType = "TRAVEL";
        }

        request.setPolicyType(normalizePolicyType(policyType));
        request.setStatus(RequestStatus.CLIENT_REPLY_RECEIVED);
        request.setUpdatedAt(LocalDateTime.now());

        request = quotationRequestRepository.save(request);

        // Use the existing ValidationService and database configuration.
        ValidationService.ValidationResult result =
                validationService.validate(request, extractedFields);

        if (!result.isValid()) {

            request.setStatus(RequestStatus.MISSING_INFORMATION);
            request.setUpdatedAt(LocalDateTime.now());

            quotationRequestRepository.save(request);

            quotationEmailService.sendMissingInformationEmail(
                    client.getEmail(),
                    result.getMissingFields(),
                    result.getInvalidFields());

            incomingEmail.setProcessingStatus("PROCESSED");
            incomingEmailRepository.save(incomingEmail);

            emailIngestionService.markEmailAsProcessed(
                    email.getMessageId());

            System.out.println(
                    "Automation: missing-information email sent to "
                            + client.getEmail());

            return;
        }

        // Generate the quotation only after validation succeeds.
        Quotation quotation =
                quotationGenerationService.generateQuotation(request);

        if (quotation == null) {
            throw new IllegalStateException(
                    "Quotation generation returned no quotation.");
        }

        quotationEmailService.sendQuotation(quotation);

        request.setStatus(RequestStatus.QUOTATION_SENT);
        request.setUpdatedAt(LocalDateTime.now());

        quotationRequestRepository.save(request);

        incomingEmail.setProcessingStatus("PROCESSED");
        incomingEmailRepository.save(incomingEmail);

        emailIngestionService.markEmailAsProcessed(
                email.getMessageId());

        System.out.println(
                "Automation: quotation generated and sent: "
                        + quotation.getQuotationNumber());
    }

    // ---------------------------------------------------------
    // GREETING DETECTION
    // ---------------------------------------------------------

    private boolean isGreetingOnly(String body) {

        if (body == null || body.isBlank()) {
            return false;
        }

        return GREETING_ONLY.matcher(body.trim()).matches();
    }

    // ---------------------------------------------------------
    // QUOTATION INTENT DETECTION
    // ---------------------------------------------------------

    private boolean hasQuotationIntent(String text) {

        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = text.toLowerCase(Locale.ROOT);

        return normalized.contains("travel insurance")
                || normalized.contains("travel quotation")
                || normalized.contains("travel quote")
                || normalized.contains("travel policy")
                || normalized.contains("insurance for travel")
                || normalized.contains("travel insurance policy")
                || normalized.contains("policy quotation")
                || normalized.contains("policy quote")
                || normalized.contains("insurance quotation")
                || normalized.contains("insurance quote")
                || normalized.contains("need a policy")
                || normalized.contains("need policy")
                || normalized.contains("quotation")
                || normalized.contains("quote");
    }

    // ---------------------------------------------------------
    // DETECT EXPLICITLY PROVIDED FIELD VALUES
    // ---------------------------------------------------------

    private boolean hasExplicitFieldValue(String body) {

        if (body == null || body.isBlank()) {
            return false;
        }

        return EXPLICIT_FIELD_VALUE.matcher(body).find();
    }

    // ---------------------------------------------------------
    // CHECK FOR ACTUAL CUSTOMER DETAILS
    // ---------------------------------------------------------

    private boolean hasActualCustomerDetails(
            List<ExtractedFieldData> fields,
            boolean explicitFieldValue) {

        if (fields == null) {
            return false;
        }

        boolean hasNonIdentityDetail = fields.stream()
                .filter(field -> field != null)
                .filter(this::hasUsableValue)
                .anyMatch(field -> {

                    String name = normalize(field.getFieldName());

                    // Sender identity alone must not trigger validation.
                    if (name.equals("email")
                            || name.equals("emailaddress")
                            || name.equals("name")
                            || name.equals("fullname")
                            || name.equals("customername")) {
                        return false;
                    }

                    /*
                     * A policy type inferred from the phrase
                     * "policy quotation" alone is not customer data.
                     * An explicitly labelled "Policy Type: Travel"
                     * can count as a supplied field.
                     */
                    if (name.equals("policytype")
                            && !explicitFieldValue) {
                        return false;
                    }

                    return true;
                });

        /*
         * If the customer explicitly provided a field, allow it to
         * proceed to normal validation, even if only one field exists.
         */
        return hasNonIdentityDetail || explicitFieldValue;
    }

    // ---------------------------------------------------------
    // SEND FORM LINK WITHOUT CREATING A REQUEST
    // ---------------------------------------------------------

    private void sendFormLinkAndFinish(
            EmailMessage email,
            String sender) {

        if (isHumanSender(sender)) {

            quotationEmailService.sendFormLinkEmail(sender);

            System.out.println(
                    "Automation: form link sent to " + sender);

        } else {

            System.out.println(
                    "Automation: skipped form link for system sender "
                            + sender);
        }

        saveProcessedNonQuotationEmail(email);

        emailIngestionService.markEmailAsProcessed(
                email.getMessageId());
    }

    private void saveProcessedNonQuotationEmail(EmailMessage email) {

        IncomingEmail incoming = new IncomingEmail();

        incoming.setQuotationRequest(null);
        incoming.setMessageId(email.getMessageId());
        incoming.setInReplyTo(email.getInReplyTo());
        incoming.setSenderEmail(email.getSenderEmail());
        incoming.setSubject(email.getSubject());
        incoming.setBody(email.getBody());
        incoming.setProcessingStatus("PROCESSED");
        incoming.setReceivedAt(email.getReceivedAt());

        incomingEmailRepository.save(incoming);
    }

    // ---------------------------------------------------------
    // FIND PENDING REQUEST
    // ---------------------------------------------------------

    private Optional<QuotationRequest> findPendingRequest(
            Client client) {

        List<QuotationRequest> requests = new ArrayList<>();

        requests.addAll(
                quotationRequestRepository.findByStatus(
                        RequestStatus.MISSING_INFORMATION));

        requests.addAll(
                quotationRequestRepository.findByStatus(
                        RequestStatus.WAITING_FOR_CLIENT));

        requests.addAll(
                quotationRequestRepository.findByStatus(
                        RequestStatus.EMAIL_RECEIVED));

        return requests.stream()
                .filter(request ->
                        request.getClient() != null
                                && request.getClient().getId() != null
                                && request.getClient().getId()
                                        .equals(client.getId()))
                .filter(request -> request.getQuotation() == null)
                .max(Comparator.comparingLong(
                        request -> request.getId() == null
                                ? 0L
                                : request.getId()));
    }

    // ---------------------------------------------------------
    // SAVE INCOMING EMAIL
    // ---------------------------------------------------------

    private IncomingEmail saveIncomingEmail(
            EmailMessage email,
            QuotationRequest request,
            String status) {

        IncomingEmail incoming = new IncomingEmail();

        incoming.setQuotationRequest(request);
        incoming.setMessageId(email.getMessageId());
        incoming.setInReplyTo(email.getInReplyTo());
        incoming.setSenderEmail(email.getSenderEmail());
        incoming.setSubject(email.getSubject());
        incoming.setBody(email.getBody());
        incoming.setProcessingStatus(status);
        incoming.setReceivedAt(email.getReceivedAt());

        return incomingEmailRepository.save(incoming);
    }

    // ---------------------------------------------------------
    // MERGE SAVED AND NEW VALUES
    // ---------------------------------------------------------

    private List<ExtractedFieldData> mergeExistingValues(
            QuotationRequest request,
            List<ExtractedFieldData> newFields) {

        Map<String, ExtractedFieldData> combined =
                new LinkedHashMap<>();

        List<RequestFieldValue> savedValues =
                requestFieldValueRepository.findByQuotationRequestId(
                        request.getId());

        for (RequestFieldValue saved : savedValues) {

            if (saved.getRequiredField() == null
                    || saved.getFieldValue() == null
                    || saved.getFieldValue().isBlank()) {
                continue;
            }

            String name = saved.getRequiredField().getFieldName();

            FieldSource source = saved.getSource() != null
                    ? saved.getSource()
                    : FieldSource.EMAIL_EXTRACTED;

            combined.put(
                    normalize(name),
                    new ExtractedFieldData(
                            name,
                            saved.getFieldValue(),
                            saved.getRequiredField().getDataType(),
                            source,
                            1.0,
                            false));
        }

        for (ExtractedFieldData field : newFields) {

            if (field == null || field.getFieldName() == null) {
                continue;
            }

            String key = normalize(field.getFieldName());

            // Empty values must not erase previously saved values.
            if (hasUsableValue(field) || !combined.containsKey(key)) {
                combined.put(key, field);
            }
        }

        return new ArrayList<>(combined.values());
    }

    // ---------------------------------------------------------
    // FIND OR CREATE CLIENT
    // ---------------------------------------------------------

    private Client findOrCreateClient(EmailMessage email) {

        Optional<Client> existing =
                clientRepository.findByEmail(email.getSenderEmail());

        if (existing.isPresent()) {
            return existing.get();
        }

        Client client = new Client();

        client.setEmail(email.getSenderEmail());
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());

        return clientRepository.save(client);
    }

    // ---------------------------------------------------------
    // UPDATE CLIENT DETAILS
    // ---------------------------------------------------------

    private void updateClient(
            Client client,
            List<ExtractedFieldData> fields) {

        String name = getFieldValue(fields, "name");
        String phone = getFieldValue(fields, "phone");

        if (name != null && !name.isBlank()) {
            client.setName(name);
        }

        if (phone != null && !phone.isBlank()) {
            client.setPhone(phone);
        }

        client.setUpdatedAt(LocalDateTime.now());
        clientRepository.save(client);
    }

    // ---------------------------------------------------------
    // FIELD HELPERS
    // ---------------------------------------------------------

    private boolean hasUsableValue(ExtractedFieldData field) {

        return field != null
                && !field.isMissing()
                && field.getFieldValue() != null
                && !field.getFieldValue().isBlank();
    }

    private String getFieldValue(
            List<ExtractedFieldData> fields,
            String fieldName) {

        String target = normalize(fieldName);

        return fields.stream()
                .filter(field -> field != null)
                .filter(this::hasUsableValue)
                .filter(field ->
                        normalize(field.getFieldName()).equals(target))
                .map(ExtractedFieldData::getFieldValue)
                .findFirst()
                .orElse(null);
    }

    private String normalize(String fieldName) {

        return fieldName == null
                ? ""
                : fieldName.replaceAll("[^A-Za-z0-9]", "")
                        .toLowerCase(Locale.ROOT);
    }

    private String normalizePolicyType(String policyType) {

        if (policyType == null || policyType.isBlank()) {
            return "TRAVEL";
        }

        String normalized = policyType
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        if (normalized.equals("trave")
                || normalized.equals("travel")
                || normalized.equals("travel insurance")) {
            return "TRAVEL";
        }

        return policyType.trim().toUpperCase(Locale.ROOT);
    }

    // ---------------------------------------------------------
    // GET LATEST MESSAGE WITHOUT QUOTED HISTORY
    // ---------------------------------------------------------

    private String getLatestMessageText(String body) {

        if (body == null || body.isBlank()) {
            return "";
        }

        Pattern quoteMarker = Pattern.compile(
                "(?im)^(?:"
                        + "-----Original Message-----"
                        + "|On .{1,200}wrote:\\s*"
                        + "|From:\\s.*"
                        + "|>.*"
                        + ")\\s*$");

        Matcher matcher = quoteMarker.matcher(body);

        if (matcher.find()) {
            return body.substring(0, matcher.start()).trim();
        }

        return body.trim();
    }

    // ---------------------------------------------------------
    // AVOID AUTOMATIC REPLIES TO SYSTEM SENDERS
    // ---------------------------------------------------------

    private boolean isHumanSender(String sender) {

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

    // ---------------------------------------------------------
    // HANDLE PROCESSING FAILURE
    // ---------------------------------------------------------

    private void handleProcessingFailure(
            EmailMessage email,
            Exception exception) {

        String messageId = email.getMessageId();

        System.err.println(
                "Automation: failed to process message "
                        + messageId + ": " + exception.getMessage());

        exception.printStackTrace();

        try {
            incomingEmailRepository.findByMessageId(messageId)
                    .ifPresent(saved -> {

                        saved.setProcessingStatus("FAILED");
                        incomingEmailRepository.save(saved);

                        QuotationRequest request =
                                saved.getQuotationRequest();

                        if (request != null
                                && request.getQuotation() == null) {

                            request.setStatus(
                                    RequestStatus.EMAIL_PROCESSING_FAILED);

                            request.setUpdatedAt(LocalDateTime.now());

                            quotationRequestRepository.save(request);
                        }
                    });

        } catch (Exception databaseException) {

            System.err.println(
                    "Automation: could not save failure status: "
                            + databaseException.getMessage());
        }
    }
}