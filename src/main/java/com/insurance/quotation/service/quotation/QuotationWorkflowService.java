package com.insurance.quotation.service.quotation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

/**
 * validate -> (missing details email | quotation + PDF + email), shared by the
 * email automation and the web forms so both behave identically and a retry
 * never creates a second quotation.
 */
@Service
public class QuotationWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(QuotationWorkflowService.class);

    public enum Result { MISSING_INFORMATION, QUOTATION_SENT, EMAIL_SEND_FAILED, QUOTATION_GENERATION_FAILED }

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
     * @param revise true when the client changed details and a NEW quotation
     *               version must be produced (the old one is kept as SUPERSEDED)
     */
    public Outcome process(
            QuotationRequest request, Client client,
            List<ExtractedFieldData> fields, boolean revise) {

        List<ExtractedFieldData> all = withIdentityFromClient(fields, client);

        ValidationService.ValidationResult result = validationService.validate(request, all);

        if (!result.isValid()) {
            request.setStatus(RequestStatus.MISSING_INFORMATION);
            request.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(request);

            // failure here propagates: the caller records it and retries later
            emailService.sendMissingInformationEmail(
                    client, request.getId(), request.getPolicyType(),
                    result.getMissingFields(), result.getInvalidFields());

            return new Outcome(Result.MISSING_INFORMATION, null,
                    result.getMissingFields(), result.getInvalidFields());
        }

        Quotation quotation;
        try {
            quotation = revise
                    ? generationService.reviseQuotation(request)
                    : generationService.generateQuotation(request);
        } catch (RuntimeException e) {
            log.error("Quotation generation failed for request {}", request.getId(), e);
            request.setStatus(RequestStatus.QUOTATION_GENERATION_FAILED);
            request.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(request);
            return new Outcome(Result.QUOTATION_GENERATION_FAILED, null, List.of(), List.of());
        }

        request.setStatus(RequestStatus.QUOTATION_GENERATED);
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);

        try {
            emailService.sendQuotation(quotation);
        } catch (RuntimeException e) {
            log.error("Quotation email failed for request {}", request.getId(), e);
            request.setStatus(RequestStatus.EMAIL_SEND_FAILED);
            request.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(request);
            return new Outcome(Result.EMAIL_SEND_FAILED, quotation, List.of(), List.of());
        }

        request.setStatus(RequestStatus.QUOTATION_SENT);
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);
        return new Outcome(Result.QUOTATION_SENT, quotation, List.of(), List.of());
    }

    /**
     * The client master is the single source of truth for the identity
     * document: replace any identity* values with the stored ones.
     */
    List<ExtractedFieldData> withIdentityFromClient(List<ExtractedFieldData> fields, Client client) {

        List<ExtractedFieldData> out = new ArrayList<>();
        for (ExtractedFieldData f : fields) {
            String n = f.getFieldName() == null ? "" : f.getFieldName().toLowerCase();
            if (!n.equals("identitytype") && !n.equals("identitynumber") && !n.equals("identitycountry")) {
                out.add(f);
            }
        }
        if (client != null && client.getIdentityNumberEnc() != null) {
            out.add(value("identityType", client.getIdentityType()));
            out.add(value("identityCountry", client.getIdentityCountry()));
            out.add(value("identityNumber", identityProtection.decrypt(client.getIdentityNumberEnc())));
        } else {
            // keep what was submitted (it will be validated; the client master is updated by the caller)
            for (ExtractedFieldData f : fields) {
                String n = f.getFieldName() == null ? "" : f.getFieldName().toLowerCase();
                if (n.equals("identitytype") || n.equals("identitynumber") || n.equals("identitycountry")) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    private static ExtractedFieldData value(String name, String v) {
        return new ExtractedFieldData(name, v, FieldDataType.TEXT, FieldSource.MANUAL_ENTRY, 1.0,
                v == null || v.isBlank());
    }
}
