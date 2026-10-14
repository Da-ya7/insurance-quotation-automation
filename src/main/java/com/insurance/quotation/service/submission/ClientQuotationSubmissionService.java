
package com.insurance.quotation.service.submission;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.scheduling.annotation.Async;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.insurance.quotation.dto.ClientQuotationSubmission;
import com.insurance.quotation.dto.ClientQuotationSubmission.TravellerSubmission;
import com.insurance.quotation.entity.AuditLog;
import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.QuotationRequestTraveller;
import com.insurance.quotation.entity.enums.FieldDataType;
import com.insurance.quotation.entity.enums.FieldSource;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.repository.AuditLogRepository;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.repository.QuotationRequestTravellerRepository;
import com.insurance.quotation.service.extraction.ExtractedFieldData;
import com.insurance.quotation.service.quotation.QuotationEmailService;
import com.insurance.quotation.service.quotation.QuotationGenerationService;
import com.insurance.quotation.service.validation.ValidationService;

@Service
public class ClientQuotationSubmissionService {

    private static final Set<String> PURPOSES = Set.of(
            "HOLIDAY", "BUSINESS", "STUDY", "FAMILY_VISIT", "OTHER"
    );

    private final ClientRepository clientRepository;
    private final QuotationRequestRepository requestRepository;
    private final QuotationRequestTravellerRepository travellerRepository;
    private final AuditLogRepository auditLogRepository;
    private final ValidationService validationService;
    private final QuotationGenerationService quotationGenerationService;
    private final QuotationEmailService quotationEmailService;

    public ClientQuotationSubmissionService(
            ClientRepository clientRepository,
            QuotationRequestRepository requestRepository,
            QuotationRequestTravellerRepository travellerRepository,
            AuditLogRepository auditLogRepository,
            ValidationService validationService,
            QuotationGenerationService quotationGenerationService,
            QuotationEmailService quotationEmailService) {

        this.clientRepository = clientRepository;
        this.requestRepository = requestRepository;
        this.travellerRepository = travellerRepository;
        this.auditLogRepository = auditLogRepository;
        this.validationService = validationService;
        this.quotationGenerationService = quotationGenerationService;
        this.quotationEmailService = quotationEmailService;
    }

    public record SubmissionReceipt(
            Long requestId,
            String clientEmail,
            boolean valid,
            List<String> missingFields,
            List<String> invalidFields) {
    }

    public record SubmissionResponse(
            Long requestId,
            String status,
            String message,
            String quotationNumber,
            List<String> missingFields,
            List<String> invalidFields) {
    }

    // Save the submission and its related database records atomically.
    @Transactional
    public SubmissionReceipt persistSubmission(
            ClientQuotationSubmission form) {

        validateForm(form);

        LocalDateTime now = LocalDateTime.now();
        String email = form.email().trim().toLowerCase(Locale.ROOT);

        Client client = clientRepository.findByEmail(email)
                .orElseGet(Client::new);

        if (client.getId() == null) {
            client.setEmail(email);
            client.setCreatedAt(now);
        }

        client.setName(form.name().trim());
        client.setPhone(form.phone().trim());
        client.setUpdatedAt(now);
        client = clientRepository.save(client);

        QuotationRequest request = new QuotationRequest();
        request.setClient(client);
        request.setPolicyType("TRAVEL");
        request.setPurposeOfTravel(
                form.purposeOfTravel().trim().toUpperCase(Locale.ROOT));
        request.setStatus(RequestStatus.EMAIL_RECEIVED);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        request = requestRepository.save(request);

        List<PersonData> people = getPeople(form);

        for (int i = 0; i < people.size(); i++) {
            PersonData person = people.get(i);

            QuotationRequestTraveller traveller =
                    new QuotationRequestTraveller();

            traveller.setQuotationRequest(request);
            traveller.setTravellerNumber(i + 1);
            traveller.setName(person.name().trim());
            traveller.setDateOfBirth(person.dateOfBirth());
            traveller.setAgeAtSubmission(
                    Period.between(person.dateOfBirth(),
                            LocalDate.now()).getYears());
            traveller.setPassportNumber(
                    person.passportNumber().trim());
            traveller.setGender(blankToNull(person.gender()));
            traveller.setNationality(person.nationality().trim());
            traveller.setCountryOfResidence(
                    person.countryOfResidence().trim());
            traveller.setEmail(
                    person.email().trim().toLowerCase(Locale.ROOT));
            traveller.setPhone(person.phone().trim());
            traveller.setPreExistingMedicalConditions(
                    blankToNull(person.medicalConditions()));

            travellerRepository.save(traveller);
        }

        int travellerCount = people.size();

        List<ExtractedFieldData> fields = List.of(
                field("name", form.name(), FieldDataType.TEXT),
                field("email", email, FieldDataType.EMAIL),
                field("phone", digitsOnly(form.phone()), FieldDataType.PHONE),
                field("passportNumber", form.passportNumber(),
                        FieldDataType.TEXT),
                field("originCountry", form.departureCountry(),
                        FieldDataType.TEXT),
                field("destinationCountry", form.destinationCountry(),
                        FieldDataType.TEXT),
                field("travelStartDate", form.travelStartDate().toString(),
                        FieldDataType.DATE),
                field("travelEndDate", form.travelEndDate().toString(),
                        FieldDataType.DATE),
                field("travellers", String.valueOf(travellerCount),
                        FieldDataType.NUMBER),
                field("policyType", "TRAVEL", FieldDataType.TEXT)
        );

        ValidationService.ValidationResult result =
                validationService.validate(request, fields);

        AuditLog audit = new AuditLog();
        audit.setEntityType("QUOTATION_REQUEST");
        audit.setEntityId(request.getId());
        audit.setAction("FORM_SUBMITTED");
        audit.setPerformedBy("CUSTOMER_FORM");
        audit.setDetails(
                "Source=client quotation form; travellers="
                        + travellerCount
                        + "; purpose=" + request.getPurposeOfTravel());
        audit.setCreatedAt(now);
        auditLogRepository.save(audit);

        return new SubmissionReceipt(
                request.getId(),
                email,
                result.isValid(),
                result.getMissingFields(),
                result.getInvalidFields());
    }

    // Perform email/quotation actions after the database transaction commits.
    @Async
    public void processSubmissionAsync(SubmissionReceipt receipt) {
        try {
            SubmissionResponse response = processSubmission(receipt);

            System.out.println(
                    "[FORM] Background processing completed for request "
                            + receipt.requestId()
                            + " with status "
                            + response.status());

        } catch (Exception exception) {
            System.err.println(
                    "[FORM] Background processing failed for request "
                            + receipt.requestId()
                            + ": " + exception.getMessage());

            exception.printStackTrace();
        }
    }

    public SubmissionResponse processSubmission(
            SubmissionReceipt receipt) {

        QuotationRequest request = requestRepository
                .findById(receipt.requestId())
                .orElseThrow(() -> new IllegalStateException(
                        "Saved quotation request was not found."));

        if (!receipt.valid()) {
            try {
                quotationEmailService.sendMissingInformationEmail(
                        receipt.clientEmail(),
                        receipt.missingFields(),
                        receipt.invalidFields());

                return new SubmissionResponse(
                        receipt.requestId(),
                        "MISSING_INFORMATION",
                        "Your request was saved. We have requested the "
                                + "remaining information.",
                        null,
                        receipt.missingFields(),
                        receipt.invalidFields());

            } catch (RuntimeException exception) {
                return new SubmissionResponse(
                        receipt.requestId(),
                        "MISSING_INFORMATION",
                        "Your request was saved, but the follow-up email "
                                + "could not be sent. Please contact support.",
                        null,
                        receipt.missingFields(),
                        receipt.invalidFields());
            }
        }

        Quotation quotation;

        try {
            quotation = quotationGenerationService
                    .generateQuotation(request);

        } catch (RuntimeException exception) {
            request.setStatus(RequestStatus.QUOTATION_GENERATION_FAILED);
            request.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(request);

            return new SubmissionResponse(
                    request.getId(),
                    "QUOTATION_GENERATION_FAILED",
                    "Your request was saved, but the quotation could not "
                            + "be generated. The team can review it.",
                    null, List.of(), List.of());
        }

        try {
            quotationEmailService.sendQuotation(quotation);

        } catch (RuntimeException exception) {
            request.setStatus(RequestStatus.EMAIL_SEND_FAILED);
            request.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(request);

            return new SubmissionResponse(
                    request.getId(),
                    "EMAIL_SEND_FAILED",
                    "Your request and quotation were saved, but the "
                            + "quotation email could not be sent.",
                    quotation.getQuotationNumber(),
                    List.of(), List.of());
        }

        request.setStatus(RequestStatus.QUOTATION_SENT);
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);

        return new SubmissionResponse(
                request.getId(),
                "QUOTATION_SENT",
                "Your quotation was generated and emailed successfully.",
                quotation.getQuotationNumber(),
                List.of(), List.of());
    }

    private void validateForm(ClientQuotationSubmission form) {
        if (form == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Form data is required.");
        }

        LocalDate today = LocalDate.now();

        if (form.travelStartDate() != null
                && form.travelStartDate().isBefore(today)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Travel start date cannot be in the past.");
        }

        if (form.travelStartDate() != null
                && form.travelEndDate() != null
                && form.travelEndDate().isBefore(form.travelStartDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Travel end date cannot be before travel start date.");
        }

        String purpose = form.purposeOfTravel() == null
                ? ""
                : form.purposeOfTravel().trim().toUpperCase(Locale.ROOT);

        if (!PURPOSES.contains(purpose)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid travel purpose.");
        }

        List<PersonData> people = getPeople(form);

        if (people.size() > 20) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Maximum 20 travellers are allowed.");
        }

        for (PersonData person : people) {
            if (person.dateOfBirth() == null
                    || person.dateOfBirth().isAfter(today)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Every traveller must have a valid date of birth.");
            }
        }
    }

    private List<PersonData> getPeople(ClientQuotationSubmission form) {
        List<PersonData> people = new ArrayList<>();

        people.add(new PersonData(
                form.name(),
                form.dateOfBirth(),
                form.passportNumber(),
                form.gender(),
                form.nationality(),
                form.countryOfResidence(),
                form.email(),
                form.phone(),
                form.preExistingMedicalConditions()));

        if (form.additionalTravellers() != null) {
            for (TravellerSubmission traveller : form.additionalTravellers()) {
                people.add(new PersonData(
                        traveller.name(),
                        traveller.dateOfBirth(),
                        traveller.passportNumber(),
                        traveller.gender(),
                        traveller.nationality(),
                        traveller.countryOfResidence(),
                        traveller.email(),
                        traveller.phone(),
                        traveller.preExistingMedicalConditions()));
            }
        }

        return people;
    }

    private ExtractedFieldData field(
            String name, String value, FieldDataType type) {

        String cleaned = value == null ? null : value.trim();

        return new ExtractedFieldData(
                name,
                cleaned,
                type,
                FieldSource.MANUAL_ENTRY,
                1.0,
                cleaned == null || cleaned.isBlank());
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PersonData(
            String name,
            LocalDate dateOfBirth,
            String passportNumber,
            String gender,
            String nationality,
            String countryOfResidence,
            String email,
            String phone,
            String medicalConditions) {
    }
}
