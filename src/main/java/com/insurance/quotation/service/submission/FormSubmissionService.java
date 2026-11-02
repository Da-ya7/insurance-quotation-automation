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
import com.insurance.quotation.service.client.ClientMasterService;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.QuotationRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.repository.QuotationRequestTravellerRepository;
import com.insurance.quotation.service.extraction.ExtractedFieldData;
import com.insurance.quotation.service.master.FormLinkService;
import com.insurance.quotation.service.otp.OtpService;
import com.insurance.quotation.service.quotation.QuotationWorkflowService;
import com.insurance.quotation.service.stats.RequestStatusInfo;

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
            String message,
            List<String> blockedFields) {

        public SubmissionResult(Long requestId, String status,
                                String quotationNumber, String message) {
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

    // NOTE: deliberately NOT @Transactional as a whole, so that a failure
    // status can still be saved after something goes wrong.
    public SubmissionResult submit(QuotationFormRequest form, String token) {

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
                    .orElseThrow(() -> new IllegalArgumentException("This link is not valid."));
            if (link.isExpired()) {
                throw new IllegalArgumentException(
                        "This link has expired. Please reply to our email to get a new one.");
            }
        }
        boolean otpOk = link != null && otp.isVerified(link.token());

        TravellerForm applicant = form.getApplicant();
        String email = applicant.getEmail().trim().toLowerCase();
        String phone = digitsOnly(applicant.getPhone());

        // 1. Client master: returning clients are recognised (identity, then
        // email + name); saved details change only with a verified OTP.
        ClientMasterService.ClientDetails details = new ClientMasterService.ClientDetails(
                applicant.getName(), email, phone,
                applicant.getDateOfBirth(), applicant.getGender(),
                applicant.getNationality(), applicant.getCountryOfResidence(),
                applicant.getNationality(), "PASSPORT", applicant.getPassportNumber());

        ClientMasterService.ApplyResult applied;
        if (link != null && link.clientId() != null) {
            Client linked = clientRepository.findById(link.clientId())
                    .orElseThrow(() -> new IllegalArgumentException("This link is not valid."));
            applied = clientMasterService.apply(linked, details, "FORM", "CUSTOMER_FORM", otpOk);
        } else {
            applied = clientMasterService.findOrCreateAndApply(details, "FORM", "CUSTOMER_FORM", otpOk);
        }
        Client client = applied.client();

        if (applied.hasBlockedChanges()) {
            return new SubmissionResult(null, "OTP_REQUIRED", null,
                    link == null
                            ? "Your details are already saved. Use the personal link from our email to change them."
                            : "To change saved personal details please verify the code sent to your registered phone.",
                    applied.blocked());
        }

        // 2. Request (a personal link keeps using the request it was created for)
        QuotationRequest request = link != null && link.requestId() != null
                ? requestRepository.findById(link.requestId()).orElse(null) : null;
        boolean reused = request != null;
        if (request == null) {
            request = new QuotationRequest();
            request.setClient(client);
            request.setCreatedAt(LocalDateTime.now());
        }
        request.setPolicyType("TRAVEL");
        request.setPurposeOfTravel(form.getPurposeOfTravel().trim());
        request.setStatus(RequestStatus.EMAIL_RECEIVED);
        request.setUpdatedAt(LocalDateTime.now());
        request = requestRepository.save(request);
        if (link != null && link.requestId() == null) {
            formLinks.attachRequest(link.id(), request.getId());
        }

        boolean hadQuotation = quotationRepository
                .findFirstByQuotationRequestIdOrderByVersionNoDesc(request.getId()).isPresent();

        // 3. Travellers (applicant = #1); replaced when the same request is re-submitted
        if (reused) {
            travellerRepository.deleteByQuotationRequestId(request.getId());
        }
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

        // 4. The same field list the email flow uses (saved details of a
        // returning client are used, not the possibly different typed ones)
        List<ExtractedFieldData> fields = List.of(
                field("name", client.getName(), FieldDataType.TEXT),
                field("email", client.getEmail(), FieldDataType.EMAIL),
                field("phone", client.getPhone(), FieldDataType.PHONE),
                field("passportNumber", applicant.getPassportNumber(), FieldDataType.TEXT),
                field("originCountry", form.getDepartureCountry(), FieldDataType.TEXT),
                field("destinationCountry", form.getDestinationCountry(), FieldDataType.TEXT),
                field("travelStartDate", form.getTravelStartDate().toString(), FieldDataType.DATE),
                field("travelEndDate", form.getTravelEndDate().toString(), FieldDataType.DATE),
                field("travellers", String.valueOf(all.size()), FieldDataType.NUMBER),
                field("policyType", "TRAVEL", FieldDataType.TEXT));

        // 5. validate -> quotation (new version if re-submitted) -> email
        try {
            QuotationWorkflowService.Outcome outcome =
                    workflow.process(request, client, fields, hadQuotation);

            Long id = request.getId();
            if (link != null && outcome.result() == QuotationWorkflowService.Result.QUOTATION_SENT) {
                formLinks.markUsed(link.id());
            }

            return switch (outcome.result()) {
                case QUOTATION_SENT -> new SubmissionResult(id, "QUOTATION_SENT",
                        outcome.quotation().getQuotationNumber(),
                        "Your quotation " + outcome.quotation().getQuotationNumber()
                                + " has been emailed to " + client.getEmail() + ".");
                case MISSING_INFORMATION -> {
                    List<String> all2 = new ArrayList<>(outcome.missingFields());
                    all2.addAll(outcome.invalidFields());
                    yield new SubmissionResult(id, "MISSING_INFORMATION", null,
                            "We still need a few details: " + String.join(", ", all2) + ".");
                }
                case EMAIL_SEND_FAILED -> new SubmissionResult(id, "EMAIL_SEND_FAILED",
                        outcome.quotation() == null ? null : outcome.quotation().getQuotationNumber(),
                        "Your quotation was created but the email could not be sent. Our team will send it to you.");
                case QUOTATION_GENERATION_FAILED -> new SubmissionResult(id, "QUOTATION_GENERATION_FAILED", null,
                        "We received your request but could not create the quotation yet. Our team will contact you.");
            };

        } catch (Exception e) {
            log.error("Quotation processing failed for request {}", request.getId(), e);
            request.setStatus(RequestStatus.MANUAL_REVIEW_REQUIRED);
            request.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(request);
            return new SubmissionResult(request.getId(), request.getStatus().name(), null,
                    "We received your request and our team will review it. Status: "
                            + RequestStatusInfo.label(request.getStatus()) + ".");
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
