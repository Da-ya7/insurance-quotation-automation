
package com.insurance.quotation.service.submission;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.insurance.quotation.dto.request.QuotationFormRequest;
import com.insurance.quotation.dto.request.TravellerForm;
import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.QuotationRequestTraveller;
import com.insurance.quotation.entity.RequiredField;
import com.insurance.quotation.entity.enums.FieldSource;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.QuotationRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.repository.QuotationRequestTravellerRepository;
import com.insurance.quotation.service.client.ClientMasterService;
import com.insurance.quotation.service.extraction.ExtractedFieldData;
import com.insurance.quotation.service.extraction.ValueNormalizer;
import com.insurance.quotation.service.master.FormLinkService;
import com.insurance.quotation.service.otp.OtpService;
import com.insurance.quotation.service.quotation.QuotationWorkflowService;
import com.insurance.quotation.service.stats.RequestStatusInfo;
import com.insurance.quotation.service.validation.ValidationService;

@Service
public class FormSubmissionService {

    private static final Logger log =
            LoggerFactory.getLogger(FormSubmissionService.class);

    public record SubmissionResult(
            Long requestId,
            String status,
            String quotationNumber,
            String message,
            List<String> blockedFields) {

        public SubmissionResult(
                Long requestId,
                String status,
                String quotationNumber,
                String message) {

            this(requestId, status, quotationNumber, message, List.of());
        }
    }

    private final ClientMasterService clientMasterService;
    private final ClientRepository clientRepository;
    private final QuotationRequestRepository requestRepository;
    private final QuotationRequestTravellerRepository travellerRepository;
    private final QuotationRepository quotationRepository;
    private final QuotationWorkflowService workflow;
    private final FormLinkService formLinks;
    private final OtpService otp;
    private final ValidationService validationService;

    public FormSubmissionService(
            ClientMasterService clientMasterService,
            ClientRepository clientRepository,
            QuotationRequestRepository requestRepository,
            QuotationRequestTravellerRepository travellerRepository,
            QuotationRepository quotationRepository,
            QuotationWorkflowService workflow,
            FormLinkService formLinks,
            OtpService otp,
            ValidationService validationService) {

        this.clientMasterService = clientMasterService;
        this.clientRepository = clientRepository;
        this.requestRepository = requestRepository;
        this.travellerRepository = travellerRepository;
        this.quotationRepository = quotationRepository;
        this.workflow = workflow;
        this.formLinks = formLinks;
        this.otp = otp;
        this.validationService = validationService;
    }

    public SubmissionResult submit(QuotationFormRequest form) {
        return submit(form, null);
    }

    // Deliberately NOT @Transactional as a whole, so that a failure
    // status can still be saved after something goes wrong.
    public SubmissionResult submit(
            QuotationFormRequest form,
            String token) {

        if (form == null || form.getApplicant() == null) {
            throw new IllegalArgumentException(
                    "Applicant details are required.");
        }

        // Validate dates only when both are supplied.
        if (form.getTravelStartDate() != null
                && form.getTravelEndDate() != null
                && form.getTravelEndDate().isBefore(
                        form.getTravelStartDate())) {

            throw new IllegalArgumentException(
                    "Travel end date must be on or after the travel start date.");
        }

        if (form.getTravelStartDate() != null
                && form.getTravelStartDate().isBefore(LocalDate.now())) {

            throw new IllegalArgumentException(
                    "Travel start date cannot be in the past.");
        }

        FormLinkService.FormLink link = null;

        if (token != null && !token.isBlank()) {

            link = formLinks.find(token)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "This link is not valid."));

            if (link.isExpired()) {
                throw new IllegalArgumentException(
                        "This link has expired. Please reply to our email to get a new one.");
            }
        }

        TravellerForm applicant = form.getApplicant();

        // ============================================================
        // 1. MANDATORY OTP SECURITY CHECK
        // ============================================================

        String submittedPhone = applicant.getPhone();

        if (submittedPhone == null || submittedPhone.isBlank()) {
            throw new IllegalArgumentException(
                    "A verified phone number is required.");
        }

        boolean otpOk;

        if (link != null) {
            otpOk = otp.isVerified(link.token());
        } else {
            otpOk = otp.isPhoneVerified(
                    form.getVerificationToken(),
                    submittedPhone);
        }

        if (!otpOk) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Please verify your phone number before submitting the form.");
        }

        // No client creation or profile update is allowed before OTP check.

        if (applicant.getEmail() == null
                || applicant.getEmail().isBlank()) {

            throw new IllegalArgumentException(
                    "Email address is required.");
        }

        String email = applicant.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        String phone = digitsOnly(submittedPhone);

        // ============================================================
        // 2. CLIENT MASTER
        // ============================================================

        /*
         * Government identity and passport details are different.
         *
         * ClientMasterService stores the Government ID:
         * identityCountry, identityType, identityNumber.
         *
         * Passport details are kept separately for the quotation request.
         */

        ClientMasterService.ClientDetails details =
                new ClientMasterService.ClientDetails(
                        applicant.getName(),
                        email,
                        phone,
                        applicant.getDateOfBirth(),
                        applicant.getGender(),
                        applicant.getNationality(),
                        applicant.getCountryOfResidence(),
                        applicant.getIdentityCountry(),
                        applicant.getIdentityType(),
                        applicant.getIdentityNumber()
                );

        ClientMasterService.ApplyResult applied;

        if (link != null && link.clientId() != null) {

            Client linked = clientRepository.findById(link.clientId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "This link is not valid."));

            applied = clientMasterService.apply(
                    linked,
                    details,
                    "FORM",
                    "CUSTOMER_FORM",
                    otpOk);

        } else {

            applied = clientMasterService.findOrCreateAndApply(
                    details,
                    "FORM",
                    "CUSTOMER_FORM",
                    otpOk);
        }

        Client client = applied.client();

        if (applied.hasBlockedChanges()) {

            return new SubmissionResult(
                    null,
                    "OTP_REQUIRED",
                    null,
                    link == null
                            ? "Your details are already saved. Use the personal link from our email to change them."
                            : "To change saved personal details please verify the code sent to your registered phone.",
                    applied.blocked());
        }

        // ============================================================
        // 3. QUOTATION REQUEST
        // ============================================================

        QuotationRequest request =
                link != null && link.requestId() != null
                        ? requestRepository.findById(link.requestId()).orElse(null)
                        : null;

        boolean reused = request != null;

        if (request == null) {

            request = new QuotationRequest();
            request.setClient(client);
            request.setCreatedAt(LocalDateTime.now());
        }

        request.setPolicyType("TRAVEL");

        request.setPurposeOfTravel(
                trimToNull(form.getPurposeOfTravel()));

        request.setStatus(RequestStatus.EMAIL_RECEIVED);
        request.setUpdatedAt(LocalDateTime.now());

        request = requestRepository.save(request);

        if (link != null && link.requestId() == null) {
            formLinks.attachRequest(link.id(), request.getId());
        }

        boolean hadQuotation = quotationRepository
                .findFirstByQuotationRequestIdOrderByVersionNoDesc(
                        request.getId())
                .isPresent();

        // ============================================================
        // 4. TRAVELLERS
        // ============================================================

        if (reused) {
            travellerRepository.deleteByQuotationRequestId(
                    request.getId());
        }

        List<TravellerForm> all = new ArrayList<>();
        all.add(applicant);

        if (form.getAdditionalTravellers() != null) {
            all.addAll(form.getAdditionalTravellers());
        }

        int number = 1;

        for (TravellerForm t : all) {

            if (t == null) {
                throw new IllegalArgumentException(
                        "Traveller details cannot be empty.");
            }

            QuotationRequestTraveller row =
                    new QuotationRequestTraveller();

            row.setQuotationRequest(request);
            row.setTravellerNumber(number++);

            // Name remains mandatory.
            String travellerName = trimToNull(t.getName());

            if (travellerName == null) {
                throw new IllegalArgumentException(
                        "Traveller name is required.");
            }

            row.setName(travellerName);

            // Date of birth is optional.
            row.setDateOfBirth(t.getDateOfBirth());
            row.setAgeAtSubmission(null);

            if (t.getDateOfBirth() != null) {
                row.setAgeAtSubmission(
                        Period.between(
                                t.getDateOfBirth(),
                                LocalDate.now()).getYears());
            }

            // These fields are controlled by Master Data.
            row.setPassportNumber(
                    trimToNull(t.getPassportNumber()));

            row.setGender(
                    trimToNull(t.getGender()));

            row.setNationality(
                    trimToNull(t.getNationality()));

            row.setCountryOfResidence(
                    trimToNull(t.getCountryOfResidence()));

            // Email remains mandatory in the current database schema.
            String travellerEmail = trimToNull(t.getEmail());

            if (travellerEmail == null) {
                throw new IllegalArgumentException(
                        "Traveller email is required.");
            }

            row.setEmail(
                    travellerEmail.toLowerCase(Locale.ROOT));

            // Phone remains mandatory in the current database schema.
            String travellerPhone = trimToNull(t.getPhone());

            if (travellerPhone == null) {
                throw new IllegalArgumentException(
                        "Traveller phone is required.");
            }

            String cleanedPhone = digitsOnly(travellerPhone);

            if (cleanedPhone.isBlank()) {
                throw new IllegalArgumentException(
                        "A valid traveller phone number is required.");
            }

            row.setPhone(cleanedPhone);

            row.setPreExistingMedicalConditions(
                    trimToNull(t.getPreExistingMedicalConditions()));

            row.setCreatedAt(LocalDateTime.now());

            travellerRepository.save(row);
        }

        // ============================================================
        // 5. DYNAMIC MASTER DATA FIELD MAPPING
        // ============================================================

        /*
         * Get only active fields applicable to TRAVEL.
         *
         * ValidationService handles policy-specific configuration,
         * mandatory checks, data-type validation and persistence.
         */

        List<RequiredField> configuredFields =
                validationService.getApplicableFields("TRAVEL");

        Map<String, String> submittedFields =
                form.getFields() == null
                        ? Map.of()
                        : form.getFields();

        List<ExtractedFieldData> fields = new ArrayList<>();

        for (RequiredField requiredField : configuredFields) {

            String fieldName = requiredField.getFieldName();

            String value = getConfiguredFieldValue(
                    fieldName,
                    form,
                    applicant,
                    client,
                    submittedFields);

            String normalizedValue = isBlank(value)
                    ? null
                    : ValueNormalizer.normalize(
                            value,
                            requiredField.getDataType());

            fields.add(new ExtractedFieldData(
                    fieldName,
                    normalizedValue,
                    requiredField.getDataType(),
                    FieldSource.MANUAL_ENTRY,
                    1.0,
                    isBlank(value)));
        }

        // ============================================================
        // 6. WORKFLOW
        // ============================================================

        try {

            QuotationWorkflowService.Outcome outcome =
                    workflow.process(
                            request,
                            client,
                            fields,
                            hadQuotation);

            consumeNewCustomerOtpIfRequired(
                    link,
                    form,
                    applicant);

            Long id = request.getId();

            if (link != null
                    && outcome.result()
                    == QuotationWorkflowService.Result.QUOTATION_SENT) {

                formLinks.markUsed(link.id());
            }

            return switch (outcome.result()) {

                case QUOTATION_SENT ->

                        new SubmissionResult(
                                id,
                                "QUOTATION_SENT",
                                outcome.quotation().getQuotationNumber(),
                                "Your quotation "
                                        + outcome.quotation().getQuotationNumber()
                                        + " has been emailed to "
                                        + client.getEmail() + ".");

                case MISSING_INFORMATION -> {

                    List<String> allMissing =
                            new ArrayList<>(outcome.missingFields());

                    allMissing.addAll(outcome.invalidFields());

                    yield new SubmissionResult(
                            id,
                            "MISSING_INFORMATION",
                            null,
                            "We still need a few details: "
                                    + String.join(", ", allMissing) + ".");
                }

                case EMAIL_SEND_FAILED ->

                        new SubmissionResult(
                                id,
                                "EMAIL_SEND_FAILED",
                                outcome.quotation() == null
                                        ? null
                                        : outcome.quotation().getQuotationNumber(),
                                "Your quotation was created but the email could not be sent. Our team will send it to you.");

                case QUOTATION_GENERATION_FAILED ->

                        new SubmissionResult(
                                id,
                                "QUOTATION_GENERATION_FAILED",
                                null,
                                "We received your request but could not create the quotation yet. Our team will contact you.");
            };

        } catch (Exception e) {

            log.error(
                    "Quotation processing failed for request {}",
                    request.getId(),
                    e);

            request.setStatus(RequestStatus.MANUAL_REVIEW_REQUIRED);
            request.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(request);

            consumeNewCustomerOtpIfRequired(
                    link,
                    form,
                    applicant);

            return new SubmissionResult(
                    request.getId(),
                    request.getStatus().name(),
                    null,
                    "We received your request and our team will review it. Status: "
                            + RequestStatusInfo.label(request.getStatus()) + ".");
        }
    }

    // ================================================================
    // GET VALUE FOR A CONFIGURED MASTER DATA FIELD
    // ================================================================

    private String getConfiguredFieldValue(
            String fieldName,
            QuotationFormRequest form,
            TravellerForm applicant,
            Client client,
            Map<String, String> submittedFields) {

        String standardValue = switch (fieldName) {

            // Applicant details
            case "name" ->
                    client.getName();

            case "email" ->
                    client.getEmail();

            case "phone" ->
                    client.getPhone();

            case "dateOfBirth" ->
                    applicant.getDateOfBirth() == null
                            ? null
                            : applicant.getDateOfBirth().toString();

            case "age" ->
                    applicant.getDateOfBirth() == null
                            ? null
                            : String.valueOf(
                                    Period.between(
                                            applicant.getDateOfBirth(),
                                            LocalDate.now()).getYears());

            case "gender" ->
                    applicant.getGender();

            case "nationality" ->
                    applicant.getNationality();

            case "countryOfResidence" ->
                    applicant.getCountryOfResidence();

            // Government identity
            case "identityCountry" ->
                    applicant.getIdentityCountry();

            case "identityType" ->
                    applicant.getIdentityType();

            case "identityNumber" ->
                    applicant.getIdentityNumber();

            // Passport details
            case "passportCountry" ->
                    applicant.getPassportCountry();

            case "passportNumber" ->
                    applicant.getPassportNumber();

            // Travel details
            case "originCountry", "departureCountry" ->
                    form.getDepartureCountry();

            case "destinationCountry" ->
                    form.getDestinationCountry();

            case "travelStartDate" ->
                    form.getTravelStartDate() == null
                            ? null
                            : form.getTravelStartDate().toString();

            case "travelEndDate" ->
                    form.getTravelEndDate() == null
                            ? null
                            : form.getTravelEndDate().toString();

            case "travellers" ->
                    form.getTravellers() == null
                            ? null
                            : String.valueOf(form.getTravellers());

            case "purposeOfTravel" ->
                    form.getPurposeOfTravel();

            case "policyType" ->
                    "TRAVEL";

            default ->
                    null;
        };

        /*
         * Standard fields use their structured DTO values first.
         * Other fields are read from the dynamic fields map.
         *
         * Government identity number is deliberately excluded from
         * the dynamic fallback to avoid bypassing the applicant's
         * identity-protection flow.
         */

        if ("identityNumber".equals(fieldName)) {
            return standardValue;
        }

        return firstNonBlank(
                standardValue,
                submittedFields.get(fieldName));
    }

    private void consumeNewCustomerOtpIfRequired(
            FormLinkService.FormLink link,
            QuotationFormRequest form,
            TravellerForm applicant) {

        if (link != null) {
            return;
        }

        boolean consumed = otp.consumePhoneVerification(
                form.getVerificationToken(),
                applicant.getPhone());

        if (!consumed) {
            log.warn(
                    "Could not consume phone verification token after accepting travel form submission.");
        }
    }

    private String digitsOnly(String value) {

        return value == null
                ? ""
                : value.replaceAll("[^0-9]", "");
    }

    private String trimToNull(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String... values) {

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }
}
