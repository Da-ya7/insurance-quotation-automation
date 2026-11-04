package com.insurance.quotation.service.submission;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

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
import com.insurance.quotation.entity.enums.FieldDataType;
import com.insurance.quotation.entity.enums.FieldSource;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.QuotationRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.repository.QuotationRequestTravellerRepository;
import com.insurance.quotation.service.client.ClientMasterService;
import com.insurance.quotation.service.extraction.ExtractedFieldData;
import com.insurance.quotation.service.master.FormLinkService;
import com.insurance.quotation.service.otp.OtpService;
import com.insurance.quotation.service.quotation.QuotationWorkflowService;
import com.insurance.quotation.service.stats.RequestStatusInfo;

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

    public FormSubmissionService(
            ClientMasterService clientMasterService,
            ClientRepository clientRepository,
            QuotationRequestRepository requestRepository,
            QuotationRequestTravellerRepository travellerRepository,
            QuotationRepository quotationRepository,
            QuotationWorkflowService workflow,
            FormLinkService formLinks,
            OtpService otp) {

        this.clientMasterService = clientMasterService;
        this.clientRepository = clientRepository;
        this.requestRepository = requestRepository;
        this.travellerRepository = travellerRepository;
        this.quotationRepository = quotationRepository;
        this.workflow = workflow;
        this.formLinks = formLinks;
        this.otp = otp;
    }

    public SubmissionResult submit(QuotationFormRequest form) {
        return submit(form, null);
    }

    // Deliberately not @Transactional as a whole. This allows the
    // MANUAL_REVIEW_REQUIRED status to be saved if quotation processing fails.
    public SubmissionResult submit(QuotationFormRequest form, String token) {

        if (form == null || form.getApplicant() == null) {
            throw new IllegalArgumentException(
                    "Applicant details are required.");
        }

        if (form.getTravelStartDate() == null
                || form.getTravelEndDate() == null) {
            throw new IllegalArgumentException(
                    "Travel start date and end date are required.");
        }

        if (form.getTravelEndDate().isBefore(form.getTravelStartDate())) {
            throw new IllegalArgumentException(
                    "Travel end date must be on or after the travel start date.");
        }

        if (form.getTravelStartDate().isBefore(LocalDate.now())) {
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

        String submittedPhone = applicant.getPhone();

        if (submittedPhone == null || submittedPhone.isBlank()) {
            throw new IllegalArgumentException(
                    "A verified phone number is required.");
        }

        // ------------------------------------------------------------
        // 1. Verify the original phone OTP.
        // ------------------------------------------------------------

        String originalVerifiedPhone = null;
        boolean initialOtpOk;

        if (link != null) {
            initialOtpOk = otp.isVerified(link.token());
        } else {
            originalVerifiedPhone =
                    otp.getVerifiedPhone(form.getVerificationToken());

            initialOtpOk =
                    originalVerifiedPhone != null
                    && otp.isPhoneVerified(
                            form.getVerificationToken(),
                            originalVerifiedPhone);
        }

        if (!initialOtpOk) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your phone verification has expired. Please verify again.");
        }

        // ------------------------------------------------------------
        // 2. Determine whether a second OTP authorizes profile edits.
        // ------------------------------------------------------------

        boolean profileEditOtpOk;

        if (link != null) {
            // Preserve the existing email form-link verification flow.
            profileEditOtpOk = initialOtpOk;
        } else {
            profileEditOtpOk =
                    originalVerifiedPhone != null
                    && otp.isPhoneVerified(
                            form.getProfileEditVerificationToken(),
                            originalVerifiedPhone);
        }

        String email = applicant.getEmail() == null
                ? null
                : applicant.getEmail().trim().toLowerCase();

        String submittedPhoneDigits = digitsOnly(submittedPhone);

        ClientMasterService.ClientDetails details =
                new ClientMasterService.ClientDetails(
                        applicant.getName(),
                        email,
                        submittedPhoneDigits,
                        applicant.getDateOfBirth(),
                        applicant.getGender(),
                        applicant.getNationality(),
                        applicant.getCountryOfResidence(),
                        applicant.getNationality(),
                        "PASSPORT",
                        applicant.getPassportNumber());

        // ------------------------------------------------------------
        // 3. Resolve the correct Client Master record.
        // ------------------------------------------------------------

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
                    profileEditOtpOk);

        } else if (link == null) {

            // Match by the phone verified by the original OTP.
            // Do not use the edited email or identity number to select
            // a different Client Master record.
            List<Client> phoneMatches =
                    findClientsByPhone(originalVerifiedPhone);

            if (phoneMatches.size() > 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Multiple customer records use this verified phone number. Please contact our team.");
            }

            if (phoneMatches.size() == 1) {

                // Existing customer: update the same Client ID.
                // Sensitive changes remain blocked until the separate
                // profile-edit OTP has been verified.
                applied = clientMasterService.apply(
                        phoneMatches.get(0),
                        details,
                        "FORM",
                        "CUSTOMER_FORM",
                        profileEditOtpOk);

            } else {

                // New customer: their first OTP authorizes initial
                // profile creation, but not a different phone number.
                if (!submittedPhoneDigits.equals(originalVerifiedPhone)) {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "The phone number does not match the verified number. Please verify the new phone number before submitting.");
                }

                applied = clientMasterService.findOrCreateAndApply(
                        details,
                        "FORM",
                        "CUSTOMER_FORM",
                        false);
            }

        } else {

            applied = clientMasterService.findOrCreateAndApply(
                    details,
                    "FORM",
                    "CUSTOMER_FORM",
                    profileEditOtpOk);
        }

        Client client = applied.client();

        // If any protected profile field changed without the second OTP,
        // pause here. Do not save the quotation request or traveller rows.
        if (applied.hasBlockedChanges()) {
            return new SubmissionResult(
                    null,
                    "OTP_REQUIRED",
                    null,
                    "Your saved customer details have changed. Please verify the code sent to your registered phone number to authorize these changes.",
                    applied.blocked());
        }

        // Consume the second OTP only after Client Master accepts the changes.
        if (link == null
                && form.getProfileEditVerificationToken() != null
                && !form.getProfileEditVerificationToken().isBlank()) {

            boolean consumed = otp.consumePhoneVerification(
                    form.getProfileEditVerificationToken(),
                    originalVerifiedPhone);

            if (!consumed) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your profile-edit verification has expired. Please verify again.");
            }
        }

        // ------------------------------------------------------------
        // 4. Create or reuse the quotation request.
        // ------------------------------------------------------------

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
                form.getPurposeOfTravel() == null
                        ? null
                        : form.getPurposeOfTravel().trim());
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

        // ------------------------------------------------------------
        // 5. Save traveller details.
        // ------------------------------------------------------------

        if (reused) {
            travellerRepository.deleteByQuotationRequestId(request.getId());
        }

        List<TravellerForm> all = new ArrayList<>();
        all.add(applicant);

        if (form.getAdditionalTravellers() != null) {
            all.addAll(form.getAdditionalTravellers());
        }

        int number = 1;

        for (TravellerForm t : all) {
            QuotationRequestTraveller row =
                    new QuotationRequestTraveller();

            row.setQuotationRequest(request);
            row.setTravellerNumber(number++);
            row.setName(t.getName().trim());
            row.setDateOfBirth(t.getDateOfBirth());

            row.setAgeAtSubmission(
                    Period.between(
                            t.getDateOfBirth(),
                            LocalDate.now()).getYears());

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

        // ------------------------------------------------------------
        // 6. Run the existing quotation workflow.
        // ------------------------------------------------------------

        List<ExtractedFieldData> fields = List.of(
                field("name", client.getName(), FieldDataType.TEXT),
                field("email", client.getEmail(), FieldDataType.EMAIL),
                field("phone", client.getPhone(), FieldDataType.PHONE),
                field("passportNumber", applicant.getPassportNumber(),
                        FieldDataType.TEXT),
                field("originCountry", form.getDepartureCountry(),
                        FieldDataType.TEXT),
                field("destinationCountry", form.getDestinationCountry(),
                        FieldDataType.TEXT),
                field("travelStartDate",
                        form.getTravelStartDate().toString(),
                        FieldDataType.DATE),
                field("travelEndDate",
                        form.getTravelEndDate().toString(),
                        FieldDataType.DATE),
                field("travellers", String.valueOf(all.size()),
                        FieldDataType.NUMBER),
                field("policyType", "TRAVEL", FieldDataType.TEXT));

        try {
            QuotationWorkflowService.Outcome outcome =
                    workflow.process(request, client, fields, hadQuotation);

            consumeNewCustomerOtpIfRequired(link, form, originalVerifiedPhone);

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
                    List<String> missing =
                            new ArrayList<>(outcome.missingFields());

                    missing.addAll(outcome.invalidFields());

                    yield new SubmissionResult(
                            id,
                            "MISSING_INFORMATION",
                            null,
                            "We still need a few details: "
                                    + String.join(", ", missing) + ".");
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

            consumeNewCustomerOtpIfRequired(link, form, originalVerifiedPhone);

            return new SubmissionResult(
                    request.getId(),
                    request.getStatus().name(),
                    null,
                    "We received your request and our team will review it. Status: "
                            + RequestStatusInfo.label(request.getStatus()) + ".");
        }
    }

    private List<Client> findClientsByPhone(String verifiedPhone) {
        if (verifiedPhone == null || verifiedPhone.isBlank()) {
            return List.of();
        }

        return clientRepository.findAll().stream()
                .filter(c -> c.getPhone() != null)
                .filter(c -> digitsOnly(c.getPhone()).equals(verifiedPhone))
                .toList();
    }

    private void consumeNewCustomerOtpIfRequired(
            FormLinkService.FormLink link,
            QuotationFormRequest form,
            String originalVerifiedPhone) {

        if (link != null) {
            return;
        }

        boolean consumed = otp.consumePhoneVerification(
                form.getVerificationToken(),
                originalVerifiedPhone);

        if (!consumed) {
            log.warn(
                    "Could not consume phone verification token after accepting travel form submission.");
        }
    }

    private ExtractedFieldData field(
            String name,
            String value,
            FieldDataType type) {

        return new ExtractedFieldData(
                name,
                value == null ? null : value.trim(),
                type,
                FieldSource.MANUAL_ENTRY,
                1.0,
                false);
    }

    private String digitsOnly(String value) {
        return value == null
                ? ""
                : value.replaceAll("[^0-9]", "");
    }
}