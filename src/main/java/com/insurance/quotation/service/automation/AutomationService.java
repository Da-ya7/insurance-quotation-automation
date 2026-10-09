package com.insurance.quotation.service.automation;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.IncomingEmail;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.mailbox.EmailMessage;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.IncomingEmailRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.service.extraction.EmailExtractionService;
import com.insurance.quotation.service.extraction.ExtractedFieldData;
import com.insurance.quotation.service.ingestion.EmailIngestionService;
import com.insurance.quotation.service.quotation.QuotationGenerationService;
import com.insurance.quotation.service.validation.ValidationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AutomationService {

    private final EmailIngestionService emailIngestionService;
    private final EmailExtractionService emailExtractionService;
    private final ValidationService validationService;
    private final QuotationGenerationService quotationGenerationService;

    private final ClientRepository clientRepository;
    private final IncomingEmailRepository incomingEmailRepository;
    private final QuotationRequestRepository quotationRequestRepository;

    public AutomationService(
            EmailIngestionService emailIngestionService,
            EmailExtractionService emailExtractionService,
            ValidationService validationService,
            QuotationGenerationService quotationGenerationService,
            ClientRepository clientRepository,
            IncomingEmailRepository incomingEmailRepository,
            QuotationRequestRepository quotationRequestRepository) {

        this.emailIngestionService = emailIngestionService;
        this.emailExtractionService = emailExtractionService;
        this.validationService = validationService;
        this.quotationGenerationService = quotationGenerationService;
        this.clientRepository = clientRepository;
        this.incomingEmailRepository = incomingEmailRepository;
        this.quotationRequestRepository = quotationRequestRepository;
    }

    @Transactional
    public void processIncomingEmails() {

        List<EmailMessage> emails =
                emailIngestionService.fetchIncomingEmails();

        for (EmailMessage email : emails) {

            if (incomingEmailRepository.existsByMessageId(
                    email.getMessageId())) {
                continue;
            }

            processEmail(email);
        }
    }

    private void processEmail(EmailMessage email) {

        // 1. Create or find client
        Client client = findOrCreateClient(email);

        // 2. Create quotation request
        QuotationRequest quotationRequest =
                new QuotationRequest();

        quotationRequest.setClient(client);
        quotationRequest.setStatus(RequestStatus.EMAIL_RECEIVED);
        quotationRequest.setCreatedAt(LocalDateTime.now());
        quotationRequest.setUpdatedAt(LocalDateTime.now());

        quotationRequest =
                quotationRequestRepository.save(quotationRequest);

        // 3. Save incoming email
        IncomingEmail incomingEmail =
                new IncomingEmail();

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

        // 4. Extract fields
        quotationRequest.setStatus(RequestStatus.DATA_EXTRACTED);

        List<ExtractedFieldData> extractedFields =
                emailExtractionService.extractFields(email);

        // 5. Update client information from extracted data
        updateClient(client, extractedFields);

        // 6. Get policy type from extracted data
        String policyType =
                getFieldValue(extractedFields, "policyType");

        quotationRequest.setPolicyType(policyType);
        quotationRequest.setUpdatedAt(LocalDateTime.now());

        quotationRequest =
                quotationRequestRepository.save(quotationRequest);

        // 7. Validate extracted information
        ValidationService.ValidationResult result =
                validationService.validate(
                        quotationRequest,
                        extractedFields
                );

        // 8. Stop here when information is missing/invalid
        if (!result.isValid()) {

            incomingEmail.setProcessingStatus("PROCESSED");
            incomingEmailRepository.save(incomingEmail);

            emailIngestionService.markEmailAsProcessed(
                    email.getMessageId()
            );

            return;
        }

        // 9. Generate quotation
        Quotation quotation =
                quotationGenerationService.generateQuotation(
                        quotationRequest
                );

        // 10. Mark request as quotation generated
        quotationRequest.setStatus(
                RequestStatus.QUOTATION_GENERATED
        );
        quotationRequest.setUpdatedAt(LocalDateTime.now());

        quotationRequestRepository.save(quotationRequest);

        // 11. Mark email as processed
        incomingEmail.setProcessingStatus("PROCESSED");
        incomingEmailRepository.save(incomingEmail);

        emailIngestionService.markEmailAsProcessed(
                email.getMessageId()
        );

        System.out.println(
                "Quotation generated successfully: "
                        + quotation.getQuotationNumber()
        );
    }

    private Client findOrCreateClient(EmailMessage email) {

        Optional<Client> existingClient =
                clientRepository.findByEmail(
                        email.getSenderEmail()
                );

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
            List<ExtractedFieldData> extractedFields) {

        String name =
                getFieldValue(extractedFields, "name");

        String phone =
                getFieldValue(extractedFields, "phone");

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
                                field.getFieldName()
                        ))
                .filter(field ->
                        !field.isMissing()
                                && field.getFieldValue() != null
                )
                .map(ExtractedFieldData::getFieldValue)
                .findFirst()
                .orElse(null);
    }
}