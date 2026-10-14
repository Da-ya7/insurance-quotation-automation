package com.insurance.quotation.service.submission;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.insurance.quotation.dto.request.QuotationFormRequest;
import com.insurance.quotation.dto.request.TravellerForm;
import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.QuotationRequestTraveller;
import com.insurance.quotation.entity.enums.FieldDataType;
import com.insurance.quotation.entity.enums.FieldSource;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.repository.QuotationRequestTravellerRepository;
import com.insurance.quotation.service.extraction.ExtractedFieldData;
import com.insurance.quotation.service.quotation.QuotationEmailService;
import com.insurance.quotation.service.quotation.QuotationGenerationService;
import com.insurance.quotation.service.validation.ValidationService;

/**
 * Handles a submission from the public web form.
 * It stores the data, then reuses the SAME validation, quotation and
 * email services that the email automation uses.
 */
@Service
public class FormSubmissionService {

    private static final Logger log =
            LoggerFactory.getLogger(FormSubmissionService.class);

    public record SubmissionResult(
            Long requestId,
            String status,
            String quotationNumber,
            String message) {
    }

    private final ClientRepository clientRepository;
    private final QuotationRequestRepository requestRepository;
    private final QuotationRequestTravellerRepository travellerRepository;
    private final ValidationService validationService;
    private final QuotationGenerationService generationService;
    private final QuotationEmailService emailService;

    public FormSubmissionService(
            ClientRepository clientRepository,
            QuotationRequestRepository requestRepository,
            QuotationRequestTravellerRepository travellerRepository,
            ValidationService validationService,
            QuotationGenerationService generationService,
            QuotationEmailService emailService) {

        this.clientRepository = clientRepository;
        this.requestRepository = requestRepository;
        this.travellerRepository = travellerRepository;
        this.validationService = validationService;
        this.generationService = generationService;
        this.emailService = emailService;
    }

    // NOTE: deliberately NOT @Transactional as a whole, so that a failure
    // status can still be saved after something goes wrong.
    public SubmissionResult submit(QuotationFormRequest form) {

        if (form.getTravelEndDate().isBefore(form.getTravelStartDate())) {
            throw new IllegalArgumentException(
                    "Travel end date must be on or after the travel start date.");
        }

        if (form.getTravelStartDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Travel start date cannot be in the past.");
        }

        TravellerForm applicant = form.getApplicant();
        String email = applicant.getEmail().trim().toLowerCase();
        String phone = digitsOnly(applicant.getPhone());

        // 1. Client
        Client client = clientRepository.findByEmail(email)
                .orElseGet(() -> {
                    Client c = new Client();
                    c.setEmail(email);
                    c.setCreatedAt(LocalDateTime.now());
                    return c;
                });
        client.setName(applicant.getName().trim());
        client.setPhone(phone);
        client.setUpdatedAt(LocalDateTime.now());
        client = clientRepository.save(client);

        // 2. Request
        QuotationRequest request = new QuotationRequest();
        request.setClient(client);
        request.setPolicyType("TRAVEL");
        request.setPurposeOfTravel(form.getPurposeOfTravel().trim());
        request.setStatus(RequestStatus.EMAIL_RECEIVED);
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        request = requestRepository.save(request);

        // 3. Travellers (applicant = #1)
        List<TravellerForm> all = new ArrayList<>();
        all.add(applicant);
        all.addAll(form.getAdditionalTravellers());

        int number = 1;
        for (TravellerForm t : all) {
            QuotationRequestTraveller row = new QuotationRequestTraveller();
            row.setQuotationRequest(request);
            row.setTravellerNumber(number++);
            row.setName(t.getName().trim());
            row.setDateOfBirth(t.getDateOfBirth());
            row.setAgeAtSubmission(
                    Period.between(t.getDateOfBirth(), LocalDate.now()).getYears());
            row.setPassportNumber(t.getPassportNumber().trim());
            row.setGender(t.getGender());
            row.setNationality(t.getNationality().trim());
            row.setCountryOfResidence(t.getCountryOfResidence().trim());
            row.setEmail(t.getEmail().trim().toLowerCase());
            row.setPhone(digitsOnly(t.getPhone()));
            row.setPreExistingMedicalConditions(
                    t.getPreExistingMedicalConditions() == null
                            || t.getPreExistingMedicalConditions().isBlank()
                            ? null
                            : t.getPreExistingMedicalConditions().trim());
            row.setCreatedAt(LocalDateTime.now());
            travellerRepository.save(row);
        }

        // 4. Convert the form into the same field list the email flow uses
        List<ExtractedFieldData> fields = List.of(
                field("name", applicant.getName(), FieldDataType.TEXT),
                field("email", email, FieldDataType.EMAIL),
                field("phone", phone, FieldDataType.PHONE),
                field("passportNumber", applicant.getPassportNumber(), FieldDataType.TEXT),
                field("originCountry", form.getDepartureCountry(), FieldDataType.TEXT),
                field("destinationCountry", form.getDestinationCountry(), FieldDataType.TEXT),
                field("travelStartDate", form.getTravelStartDate().toString(), FieldDataType.DATE),
                field("travelEndDate", form.getTravelEndDate().toString(), FieldDataType.DATE),
                field("travellers", String.valueOf(all.size()), FieldDataType.NUMBER),
                field("policyType", "TRAVEL", FieldDataType.TEXT));

        // 5. Validate -> quotation -> email, with failure handling
        try {
            ValidationService.ValidationResult result =
                    validationService.validate(request, fields);

            if (!result.isValid()) {
                // Should not happen for a complete form, but never ignore it.
                request.setStatus(RequestStatus.MANUAL_REVIEW_REQUIRED);
                request.setUpdatedAt(LocalDateTime.now());
                requestRepository.save(request);
                return new SubmissionResult(request.getId(),
                        request.getStatus().name(), null,
                        "We received your request and our team will review it.");
            }

            Quotation quotation = generationService.generateQuotation(request);

            request.setStatus(RequestStatus.QUOTATION_GENERATED);
            request.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(request);

            try {
                emailService.sendQuotation(quotation);
            } catch (Exception emailError) {
                log.error("Quotation email failed for request {}",
                        request.getId(), emailError);
                request.setStatus(RequestStatus.EMAIL_SEND_FAILED);
                request.setUpdatedAt(LocalDateTime.now());
                requestRepository.save(request);
                return new SubmissionResult(request.getId(),
                        request.getStatus().name(),
                        quotation.getQuotationNumber(),
                        "Your quotation was created but the email could not be sent. Our team will send it to you.");
            }

            request.setStatus(RequestStatus.QUOTATION_SENT);
            request.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(request);

            return new SubmissionResult(request.getId(),
                    request.getStatus().name(),
                    quotation.getQuotationNumber(),
                    "Your quotation has been emailed to " + email + ".");

        } catch (Exception e) {
            log.error("Quotation generation failed for request {}",
                    request.getId(), e);
            request.setStatus(RequestStatus.QUOTATION_GENERATION_FAILED);
            request.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(request);
            return new SubmissionResult(request.getId(),
                    request.getStatus().name(), null,
                    "We received your request but could not create the quotation yet. Our team will contact you.");
        }
    }

    private ExtractedFieldData field(
            String name, String value, FieldDataType type) {
        return new ExtractedFieldData(
                name, value == null ? null : value.trim(),
                type, FieldSource.MANUAL_ENTRY, 1.0, false);
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }
}
