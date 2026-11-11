
package com.insurance.quotation.service.quotation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.enums.FieldDataType;
import com.insurance.quotation.entity.enums.FieldSource;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.service.client.IdentityProtectionService;
import com.insurance.quotation.service.extraction.ExtractedFieldData;
import com.insurance.quotation.service.validation.ValidationService;

@Service
public class QuotationWorkflowService {

    private static final Logger log =
            LoggerFactory.getLogger(QuotationWorkflowService.class);

    public enum Result {
        MISSING_INFORMATION,
        QUOTATION_SENT,
        EMAIL_SEND_FAILED,
        QUOTATION_GENERATION_FAILED
    }

    public record Outcome(
            Result result,
            Quotation quotation,
            List<String> missingFields,
            List<String> invalidFields) {
    }

    private final ValidationService validationService;
    private final QuotationGenerationService generationService;
    private final QuotationEmailService emailService;
    private final QuotationRequestRepository requestRepository;
    private final IdentityProtectionService identityProtection;

    public QuotationWorkflowService(
            ValidationService validationService,
            QuotationGenerationService generationService,
            QuotationEmailService emailService,
            QuotationRequestRepository requestRepository,
            IdentityProtectionService identityProtection) {

        this.validationService = validationService;
        this.generationService = generationService;
        this.emailService = emailService;
        this.requestRepository = requestRepository;
        this.identityProtection = identityProtection;
    }

    /**
     * Validates customer details.
     *
     * If details are missing, sends a missing-information email.
     * If all details are valid, generates the quotation and sends it.
     *
     * @param revise true when a new quotation version must be generated.
     */
    public Outcome process(
            QuotationRequest request,
            Client client,
            List<ExtractedFieldData> fields,
            boolean revise) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Quotation request cannot be null");
        }

        List<ExtractedFieldData> all =
                withIdentityFromClient(fields, client);

        ValidationService.ValidationResult result =
                validationService.validate(request, all);

        // -------------------------------------------------
        // MISSING OR INVALID INFORMATION
        // -------------------------------------------------

        if (!result.isValid()) {

            request.setStatus(RequestStatus.MISSING_INFORMATION);
            request.setUpdatedAt(LocalDateTime.now());

            requestRepository.save(request);

            log.info(
                    "Request {} has missing fields: {} and invalid fields: {}",
                    request.getId(),
                    result.getMissingFields(),
                    result.getInvalidFields()
            );

            try {

                emailService.sendMissingInformationEmail(
                        client,
                        request.getId(),
                        request.getPolicyType(),
                        result.getMissingFields(),
                        result.getInvalidFields()
                );

                log.info(
                        "Missing-information email sent for request {}",
                        request.getId()
                );

                return new Outcome(
                        Result.MISSING_INFORMATION,
                        null,
                        result.getMissingFields(),
                        result.getInvalidFields()
                );

            } catch (RuntimeException e) {

                log.error(
                        "Failed to send missing-information email for request {}",
                        request.getId(),
                        e
                );

                request.setStatus(RequestStatus.EMAIL_SEND_FAILED);
                request.setUpdatedAt(LocalDateTime.now());

                requestRepository.save(request);

                return new Outcome(
                        Result.EMAIL_SEND_FAILED,
                        null,
                        result.getMissingFields(),
                        result.getInvalidFields()
                );
            }
        }

        // -------------------------------------------------
        // GENERATE QUOTATION
        // -------------------------------------------------

        Quotation quotation;

        try {

            quotation = revise
                    ? generationService.reviseQuotation(request)
                    : generationService.generateQuotation(request);

        } catch (RuntimeException e) {

            log.error(
                    "Quotation generation failed for request {}",
                    request.getId(),
                    e
            );

            request.setStatus(
                    RequestStatus.QUOTATION_GENERATION_FAILED);

            request.setUpdatedAt(LocalDateTime.now());

            requestRepository.save(request);

            return new Outcome(
                    Result.QUOTATION_GENERATION_FAILED,
                    null,
                    List.of(),
                    List.of()
            );
        }

        // -------------------------------------------------
        // SAVE GENERATED STATUS
        // -------------------------------------------------

        request.setStatus(RequestStatus.QUOTATION_GENERATED);
        request.setUpdatedAt(LocalDateTime.now());

        requestRepository.save(request);

        // -------------------------------------------------
        // SEND QUOTATION EMAIL
        // -------------------------------------------------

        try {

            emailService.sendQuotation(quotation);

            log.info(
                    "Quotation email sent for request {}",
                    request.getId()
            );

        } catch (RuntimeException e) {

            log.error(
                    "Quotation email failed for request {}",
                    request.getId(),
                    e
            );

            request.setStatus(RequestStatus.EMAIL_SEND_FAILED);
            request.setUpdatedAt(LocalDateTime.now());

            requestRepository.save(request);

            return new Outcome(
                    Result.EMAIL_SEND_FAILED,
                    quotation,
                    List.of(),
                    List.of()
            );
        }

        // -------------------------------------------------
        // MARK QUOTATION AS SENT
        // -------------------------------------------------

        request.setStatus(RequestStatus.QUOTATION_SENT);
        request.setUpdatedAt(LocalDateTime.now());

        requestRepository.save(request);

        return new Outcome(
                Result.QUOTATION_SENT,
                quotation,
                List.of(),
                List.of()
        );
    }

    /**
     * Uses the client master as the source of truth for
     * identity document details when they are available.
     */
    List<ExtractedFieldData> withIdentityFromClient(
            List<ExtractedFieldData> fields,
            Client client) {

        List<ExtractedFieldData> out = new ArrayList<>();

        if (fields != null) {

            for (ExtractedFieldData field : fields) {

                if (field == null) {
                    continue;
                }

                String name = normalize(field.getFieldName());

                if (!name.equals("identitytype")
                        && !name.equals("identitynumber")
                        && !name.equals("identitycountry")) {

                    out.add(field);
                }
            }
        }

        if (client != null
                && client.getIdentityNumberEnc() != null
                && !client.getIdentityNumberEnc().isBlank()) {

            String identityNumber =
                    identityProtection.decrypt(
                            client.getIdentityNumberEnc()
                    );

            out.add(value(
                    "identityType",
                    client.getIdentityType()
            ));

            out.add(value(
                    "identityCountry",
                    client.getIdentityCountry()
            ));

            out.add(value(
                    "identityNumber",
                    identityNumber
            ));

        } else if (fields != null) {

            // Keep identity details submitted in the current email
            // when the client master does not have a saved identity number.

            for (ExtractedFieldData field : fields) {

                if (field == null) {
                    continue;
                }

                String name = normalize(field.getFieldName());

                if (name.equals("identitytype")
                        || name.equals("identitynumber")
                        || name.equals("identitycountry")) {

                    out.add(field);
                }
            }
        }

        return out;
    }

    private static ExtractedFieldData value(
            String name,
            String value) {

        return new ExtractedFieldData(
                name,
                value,
                FieldDataType.TEXT,
                FieldSource.MANUAL_ENTRY,
                1.0,
                value == null || value.isBlank()
        );
    }

    private static String normalize(String value) {

        if (value == null) {
            return "";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }
}
