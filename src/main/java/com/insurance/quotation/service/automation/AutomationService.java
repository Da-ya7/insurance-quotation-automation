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
import java.util.Map;
import java.util.Optional;

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

    // Not one big transaction: a failure on one email must not undo or block others.
    public void processIncomingEmails() {

        List<EmailMessage> emails =
                emailIngestionService.fetchIncomingEmails();

        for (EmailMessage email : emails) {

            if (incomingEmailRepository.existsByMessageId(
                    email.getMessageId())) {

                emailIngestionService.markEmailAsProcessed(
                        email.getMessageId());

                continue;
            }

            try {
                processEmail(email);
            } catch (Exception e) {
                handleProcessingFailure(email, e);
            }
        }
    }

    private void handleProcessingFailure(EmailMessage email, Exception e) {
        System.err.println("Failed to process email "
                + email.getMessageId() + ": " + e.getMessage());
        e.printStackTrace();

        incomingEmailRepository.findByMessageId(email.getMessageId())
                .ifPresent(saved -> {
                    saved.setProcessingStatus("FAILED");
                    incomingEmailRepository.save(saved);

                    QuotationRequest req = saved.getQuotationRequest();
                    if (req != null) {
                        req.setStatus(RequestStatus.EMAIL_PROCESSING_FAILED);
                        req.setUpdatedAt(LocalDateTime.now());
                        quotationRequestRepository.save(req);
                    }
                });

        // Mark as processed so a broken email is not retried every minute.
        try {
            emailIngestionService.markEmailAsProcessed(email.getMessageId());
        } catch (Exception ignored) {
            // nothing more we can do
        }
    }

    private void processEmail(EmailMessage email) {

        // 1. Find or create the customer.
        Client client = findOrCreateClient(email);

        // 2. Reuse the customer's pending request when possible.
        Optional<QuotationRequest> pendingRequest =
                findPendingRequest(client);

        boolean isReply = pendingRequest.isPresent();

        QuotationRequest quotationRequest;

        if (isReply) {
            quotationRequest = pendingRequest.get();

            quotationRequest.setStatus(
                    RequestStatus.CLIENT_REPLY_RECEIVED);

            quotationRequest.setUpdatedAt(LocalDateTime.now());

            System.out.println(
                    "Customer reply linked to existing request "
                            + quotationRequest.getId());

        } else {
            quotationRequest = new QuotationRequest();

            quotationRequest.setClient(client);
            quotationRequest.setStatus(RequestStatus.EMAIL_RECEIVED);
            quotationRequest.setCreatedAt(LocalDateTime.now());
            quotationRequest.setUpdatedAt(LocalDateTime.now());

            quotationRequest =
                    quotationRequestRepository.save(quotationRequest);
        }

        // 3. Save the incoming email or customer reply in MySQL.
        IncomingEmail incomingEmail = new IncomingEmail();

        incomingEmail.setQuotationRequest(quotationRequest);
        incomingEmail.setMessageId(email.getMessageId());
        incomingEmail.setInReplyTo(email.getInReplyTo());
        incomingEmail.setSenderEmail(email.getSenderEmail());
        incomingEmail.setSubject(email.getSubject());
        incomingEmail.setBody(email.getBody());
        incomingEmail.setProcessingStatus("RECEIVED");
        incomingEmail.setReceivedAt(email.getReceivedAt());

        incomingEmail =
                incomingEmailRepository.save(incomingEmail);

        // 4. Extract fields from the latest email.
        List<ExtractedFieldData> extractedFields =
                emailExtractionService.extractFields(email);

        // 4b. Unrelated email: nothing usable found and not a reply.
        boolean nothingExtracted = extractedFields.stream().noneMatch(f ->
                !f.isMissing()
                        && f.getFieldValue() != null
                        && !f.getFieldValue().isBlank());

        if (!isReply && nothingExtracted) {
            quotationRequest.setStatus(RequestStatus.MANUAL_REVIEW_REQUIRED);
            quotationRequest.setUpdatedAt(LocalDateTime.now());
            quotationRequestRepository.save(quotationRequest);

            incomingEmail.setProcessingStatus("PROCESSED");
            incomingEmailRepository.save(incomingEmail);

            emailIngestionService.markEmailAsProcessed(email.getMessageId());
            return;
        }

        // 5. For a reply, combine new details with previous valid values.
        if (isReply) {
            extractedFields = mergeExistingValues(
                    quotationRequest,
                    extractedFields);
        }

        // 6. Update customer information.
        updateClient(client, extractedFields);

        // 7. Set the policy type before validation.
        String policyType =
                getFieldValue(extractedFields, "policyType");

        if (policyType == null || policyType.isBlank()) {
        policyType = quotationRequest.getPolicyType();
        }

        if (policyType == null || policyType.isBlank()) {
        policyType = "TRAVEL";
        }

        quotationRequest.setPolicyType(policyType);

        quotationRequest.setUpdatedAt(LocalDateTime.now());

        quotationRequest =
                quotationRequestRepository.save(quotationRequest);

        // 8. Validate the combined information.
        ValidationService.ValidationResult result =
                validationService.validate(
                        quotationRequest,
                        extractedFields);

        // 9. If information is still missing, email the customer.
        if (!result.isValid()) {

        // IMPORTANT: Save a pending status so the client's reply
        // can be linked to this same quotation request.
        quotationRequest.setStatus(RequestStatus.MISSING_INFORMATION);
        quotationRequest.setUpdatedAt(LocalDateTime.now());
        quotationRequestRepository.save(quotationRequest);

        quotationEmailService.sendMissingInformationEmail(
                client.getEmail(),
                result.getMissingFields(),
                result.getInvalidFields());

        incomingEmail.setProcessingStatus("PROCESSED");
        incomingEmailRepository.save(incomingEmail);

        emailIngestionService.markEmailAsProcessed(
                email.getMessageId());

        System.out.println(
                "Missing-information email sent to "
                        + client.getEmail());

        return;
        }

        // 10. Generate the quotation and PDF.
        Quotation quotation =
                quotationGenerationService.generateQuotation(
                        quotationRequest);

        // 11. Send the actual PDF to the customer.
        try {
            quotationEmailService.sendQuotation(quotation);
        } catch (Exception sendError) {
            quotationRequest.setStatus(RequestStatus.EMAIL_SEND_FAILED);
            quotationRequest.setUpdatedAt(LocalDateTime.now());
            quotationRequestRepository.save(quotationRequest);

            incomingEmail.setProcessingStatus("FAILED");
            incomingEmailRepository.save(incomingEmail);

            emailIngestionService.markEmailAsProcessed(email.getMessageId());
            throw sendError;
        }

        // 12. Mark the request as sent only after successful sending.
        quotationRequest.setStatus(RequestStatus.QUOTATION_SENT);
        quotationRequest.setUpdatedAt(LocalDateTime.now());

        quotationRequestRepository.save(quotationRequest);

        // 13. Mark the incoming email as processed.
        incomingEmail.setProcessingStatus("PROCESSED");
        incomingEmailRepository.save(incomingEmail);

        emailIngestionService.markEmailAsProcessed(
                email.getMessageId());

        System.out.println(
                "Quotation generated and sent: "
                        + quotation.getQuotationNumber());
    }

    private Optional<QuotationRequest> findPendingRequest(
            Client client) {

        List<QuotationRequest> pendingRequests =
                new ArrayList<>();

        pendingRequests.addAll(
                quotationRequestRepository.findByStatus(
                        RequestStatus.MISSING_INFORMATION));

        pendingRequests.addAll(
                quotationRequestRepository.findByStatus(
                        RequestStatus.WAITING_FOR_CLIENT));

        return pendingRequests.stream()
                .filter(request ->
                        request.getClient() != null
                                && request.getClient().getId() != null
                                && request.getClient().getId()
                                .equals(client.getId()))
                .max(Comparator.comparingLong(
                        request -> request.getId() == null
                                ? 0L
                                : request.getId()));
    }

    private List<ExtractedFieldData> mergeExistingValues(
            QuotationRequest quotationRequest,
            List<ExtractedFieldData> newlyExtractedFields) {

        Map<String, ExtractedFieldData> combined =
                new LinkedHashMap<>();

        // Load the values already stored for this request.
        List<RequestFieldValue> savedValues =
                requestFieldValueRepository.findByQuotationRequestId(
                        quotationRequest.getId());

        for (RequestFieldValue saved : savedValues) {

            if (saved.getRequiredField() == null
                    || saved.getFieldValue() == null) {
                continue;
            }

            String fieldName =
                    saved.getRequiredField().getFieldName();

            FieldSource source = saved.getSource() != null
                    ? saved.getSource()
                    : FieldSource.EMAIL_EXTRACTED;

            combined.put(
                    normalize(fieldName),
                    new ExtractedFieldData(
                            fieldName,
                            saved.getFieldValue(),
                            saved.getRequiredField().getDataType(),
                            source,
                            1.0,
                            false
                    )
            );
        }

        // Only replace an existing value when the latest email
        // actually contains a nonblank value for that field.
        for (ExtractedFieldData field : newlyExtractedFields) {

            if (field.getFieldName() == null) {
                continue;
            }

            String key = normalize(field.getFieldName());

            boolean hasValue =
                    !field.isMissing()
                            && field.getFieldValue() != null
                            && !field.getFieldValue().isBlank();

            if (hasValue || !combined.containsKey(key)) {
                combined.put(key, field);
            }
        }

        return new ArrayList<>(combined.values());
    }

    private Client findOrCreateClient(EmailMessage email) {

        Optional<Client> existingClient =
                clientRepository.findByEmail(
                        email.getSenderEmail());

        if (existingClient.isPresent()) {
            return existingClient.get();
        }

        Client client = new Client();

        client.setEmail(email.getSenderEmail());
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());

        return clientRepository.save(client);
    }

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

    private String getFieldValue(
            List<ExtractedFieldData> fields,
            String fieldName) {

        return fields.stream()
                .filter(field ->
                        fieldName.equalsIgnoreCase(
                                field.getFieldName()))
                .filter(field ->
                        !field.isMissing()
                                && field.getFieldValue() != null
                                && !field.getFieldValue().isBlank())
                .map(ExtractedFieldData::getFieldValue)
                .findFirst()
                .orElse(null);
    }

    private String normalize(String fieldName) {
        return fieldName == null
                ? ""
                : fieldName.trim().toLowerCase();
    }
}