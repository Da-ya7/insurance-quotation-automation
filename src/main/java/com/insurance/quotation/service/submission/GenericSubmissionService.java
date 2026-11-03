
package com.insurance.quotation.service.submission;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.RequestFieldValue;
import com.insurance.quotation.entity.RequiredField;
import com.insurance.quotation.entity.enums.FieldSource;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.QuotationRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.repository.RequestFieldValueRepository;
import com.insurance.quotation.repository.RequiredFieldRepository;
import com.insurance.quotation.service.client.ClientMasterService;
import com.insurance.quotation.service.extraction.ExtractedFieldData;
import com.insurance.quotation.service.extraction.ValueNormalizer;
import com.insurance.quotation.service.master.FormLinkService;
import com.insurance.quotation.service.master.InsuranceTypeDef;
import com.insurance.quotation.service.master.MasterDataService;
import com.insurance.quotation.service.otp.OtpService;
import com.insurance.quotation.service.quotation.QuotationWorkflowService;
import com.insurance.quotation.service.stats.RequestStatusInfo;
import com.insurance.quotation.service.validation.ValidationService;

/**
 * Web-form submission for every insurance type except TRAVEL (which keeps its
 * own traveller form). Works with or without a personal form link.
 */
@Service
public class GenericSubmissionService {

    public record Applicant(
            String name,
            String email,
            String phone,
            LocalDate dateOfBirth,
            String gender,
            String nationality,
            String countryOfResidence,
            String identityCountry,
            String identityType,
            String identityNumber) {
    }

    public record Submission(
            String insuranceType,
            Applicant applicant,
            Map<String, String> fields,
            String verificationToken) {

        // Retains compatibility with existing Java callers using 3 arguments.
        public Submission(
                String insuranceType,
                Applicant applicant,
                Map<String, String> fields) {
            this(insuranceType, applicant, fields, null);
        }
    }

    public record Result(
            Long requestId,
            String status,
            String statusLabel,
            String quotationNumber,
            String message,
            List<String> missingFields,
            List<String> blockedFields) {
    }

    /** Fields collected in the applicant block, not in the dynamic list. */
    public static final List<String> APPLICANT_FIELDS = List.of(
            "name", "email", "phone", "policyType", "dateOfBirth",
            "identityType", "identityNumber", "identityCountry",
            "gender", "nationality", "countryOfResidence");

    private final FormLinkService formLinks;
    private final OtpService otp;
    private final MasterDataService masterData;
    private final ClientMasterService clientMaster;
    private final ClientRepository clientRepository;
    private final QuotationRequestRepository requestRepository;
    private final QuotationRepository quotationRepository;
    private final RequiredFieldRepository requiredFieldRepository;
    private final RequestFieldValueRepository fieldValueRepository;
    private final QuotationWorkflowService workflow;

    public GenericSubmissionService(
            FormLinkService formLinks,
            OtpService otp,
            MasterDataService masterData,
            ClientMasterService clientMaster,
            ClientRepository clientRepository,
            QuotationRequestRepository requestRepository,
            QuotationRepository quotationRepository,
            RequiredFieldRepository requiredFieldRepository,
            RequestFieldValueRepository fieldValueRepository,
            QuotationWorkflowService workflow) {

        this.formLinks = formLinks;
        this.otp = otp;
        this.masterData = masterData;
        this.clientMaster = clientMaster;
        this.clientRepository = clientRepository;
        this.requestRepository = requestRepository;
        this.quotationRepository = quotationRepository;
        this.requiredFieldRepository = requiredFieldRepository;
        this.fieldValueRepository = fieldValueRepository;
        this.workflow = workflow;
    }

    public Result submit(String token, String pathType, Submission body) {

        if (body == null || body.applicant() == null) {
            throw new IllegalArgumentException(
                    "Applicant details are required.");
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

        Applicant a = body.applicant();

        // ============================================================
        // 1. MANDATORY PHONE OTP VERIFICATION
        // ============================================================

        if (isBlank(a.phone())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "A verified phone number is required.");
        }

        boolean otpOk;

        if (link != null) {
            // Returning customer: verification must belong to this link.
            otpOk = otp.isVerified(link.token());
        } else {
            // New customer: verification token must match submitted phone.
            otpOk = otp.isPhoneVerified(
                    body.verificationToken(),
                    a.phone());
        }

        if (!otpOk) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Please verify your phone number before submitting the form.");
        }

        // ============================================================
        // 2. RETURNING CUSTOMER PREFILL
        // ============================================================

        if (link != null && link.clientId() != null) {
            Client known = clientRepository.findById(link.clientId())
                    .orElse(null);

            if (known != null
                    && (isBlank(a.name()) || isBlank(a.email()))) {

                a = new Applicant(
                        isBlank(a.name()) ? known.getName() : a.name(),
                        isBlank(a.email()) ? known.getEmail() : a.email(),
                        a.phone(),
                        a.dateOfBirth(),
                        a.gender(),
                        a.nationality(),
                        a.countryOfResidence(),
                        a.identityCountry(),
                        a.identityType(),
                        a.identityNumber());
            }
        }

        if (isBlank(a.name()) || isBlank(a.email())) {
            throw new IllegalArgumentException(
                    "Name and email are required.");
        }

        // ============================================================
        // 3. VALIDATE INSURANCE TYPE
        // ============================================================

        String type = firstNonBlank(
                body.insuranceType(),
                pathType,
                link == null ? null : link.insuranceType());

        InsuranceTypeDef def = masterData.insuranceType(type)
                .filter(InsuranceTypeDef::active)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Please choose a valid insurance type."));

        if ("TRAVEL".equals(def.code())) {
            throw new IllegalArgumentException(
                    "Travel insurance uses the travel quotation form.");
        }

        // ============================================================
        // 4. CLIENT MASTER
        // ============================================================

        ClientMasterService.ClientDetails details =
                new ClientMasterService.ClientDetails(
                        a.name(),
                        a.email().trim().toLowerCase(Locale.ROOT),
                        digits(a.phone()),
                        a.dateOfBirth(),
                        a.gender(),
                        a.nationality(),
                        a.countryOfResidence(),
                        a.identityCountry(),
                        a.identityType(),
                        a.identityNumber());

        ClientMasterService.ApplyResult applied;

        if (link != null && link.clientId() != null) {
            Client linked = clientRepository.findById(link.clientId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "This link is not valid."));

            applied = clientMaster.apply(
                    linked, details, "FORM", "CUSTOMER_FORM", otpOk);
        } else {
            applied = clientMaster.findOrCreateAndApply(
                    details, "FORM", "CUSTOMER_FORM", otpOk);
        }

        Client client = applied.client();

        if (applied.hasBlockedChanges()) {
            return new Result(
                    null,
                    "OTP_REQUIRED",
                    "Verification needed",
                    null,
                    link == null
                            ? "Your details are already saved. Use the personal link from our email to change them."
                            : "To change saved personal details please verify the code sent to your registered phone.",
                    List.of(),
                    applied.blocked());
        }

        // ============================================================
        // 5. QUOTATION REQUEST
        // ============================================================

        QuotationRequest request =
                link != null && link.requestId() != null
                        ? requestRepository.findById(link.requestId())
                                .orElse(null)
                        : null;

        if (request == null) {
            request = new QuotationRequest();
            request.setClient(client);
            request.setCreatedAt(LocalDateTime.now());
            request.setStatus(RequestStatus.EMAIL_RECEIVED);
        }

        request.setPolicyType(def.code());
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
        // 6. DYNAMIC INSURANCE FIELDS
        // ============================================================

        List<RequiredField> forType = requiredFieldRepository
                .findByIsActiveTrueOrderBySortOrderAscIdAsc()
                .stream()
                .filter(f -> ValidationService.appliesTo(f, def.code()))
                .toList();

        Map<String, String> saved = new HashMap<>();

        for (RequestFieldValue v :
                fieldValueRepository.findByQuotationRequestId(
                        request.getId())) {

            if (v.getRequiredField() != null) {
                saved.put(
                        v.getRequiredField().getFieldName(),
                        v.getFieldValue());
            }
        }

        Map<String, String> given =
                body.fields() == null ? Map.of() : body.fields();

        List<ExtractedFieldData> fields = new ArrayList<>();

        for (RequiredField rf : forType) {
            String n = rf.getFieldName();

            String value = switch (n) {
                case "name" -> client.getName();
                case "email" -> client.getEmail();
                case "phone" -> client.getPhone();
                case "dateOfBirth" ->
                        client.getDateOfBirth() == null
                                ? null
                                : client.getDateOfBirth().toString();
                case "policyType" -> def.code();
                case "identityType", "identityNumber", "identityCountry" -> null;
                default -> firstNonBlank(given.get(n), saved.get(n));
            };

            fields.add(new ExtractedFieldData(
                    n,
                    value == null
                            ? null
                            : ValueNormalizer.normalize(
                                    value, rf.getDataType()),
                    rf.getDataType(),
                    FieldSource.MANUAL_ENTRY,
                    1.0,
                    isBlank(value)));
        }

        // ============================================================
        // 7. QUOTATION WORKFLOW
        // ============================================================

        QuotationWorkflowService.Outcome outcome =
                workflow.process(request, client, fields, hadQuotation);

        // Consume a new-customer token after the workflow has returned.
        consumeNewCustomerOtpIfRequired(link, body, a);

        if (link != null
                && outcome.result()
                == QuotationWorkflowService.Result.QUOTATION_SENT) {
            formLinks.markUsed(link.id());
        }

        Long id = request.getId();
        String label = RequestStatusInfo.label(request.getStatus());

        return switch (outcome.result()) {

            case QUOTATION_SENT ->
                    new Result(
                            id,
                            "QUOTATION_SENT",
                            label,
                            outcome.quotation().getQuotationNumber(),
                            "Your quotation "
                                    + outcome.quotation().getQuotationNumber()
                                    + " has been emailed to "
                                    + client.getEmail() + ".",
                            List.of(),
                            List.of());

            case MISSING_INFORMATION -> {
                List<String> all =
                        new ArrayList<>(outcome.missingFields());

                all.addAll(outcome.invalidFields());

                yield new Result(
                        id,
                        "MISSING_INFORMATION",
                        label,
                        null,
                        "We still need a few details: "
                                + String.join(", ", all) + ".",
                        all,
                        List.of());
            }

            case EMAIL_SEND_FAILED ->
                    new Result(
                            id,
                            "EMAIL_SEND_FAILED",
                            label,
                            outcome.quotation() == null
                                    ? null
                                    : outcome.quotation().getQuotationNumber(),
                            "Your quotation was created but the email could not be sent. Our team will send it to you.",
                            List.of(),
                            List.of());

            case QUOTATION_GENERATION_FAILED ->
                    new Result(
                            id,
                            "QUOTATION_GENERATION_FAILED",
                            label,
                            null,
                            "We received your request but could not create the quotation yet. Our team will contact you.",
                            List.of(),
                            List.of());
        };
    }

    private void consumeNewCustomerOtpIfRequired(
            FormLinkService.FormLink link,
            Submission body,
            Applicant applicant) {

        if (link != null) {
            return;
        }

        boolean consumed = otp.consumePhoneVerification(
                body.verificationToken(),
                applicant.phone());

        if (!consumed) {
            // The workflow has already returned. Record this for investigation.
            // The submission result should not be discarded at this stage.
            org.slf4j.LoggerFactory.getLogger(
                    GenericSubmissionService.class).warn(
                    "Could not consume phone verification token after accepting generic form submission.");
        }
    }

    /** Finds the current client of a link (for prefill). */
    public Optional<Client> clientOf(FormLinkService.FormLink link) {
        return link.clientId() == null
                ? Optional.empty()
                : clientRepository.findById(link.clientId());
    }

    private static String digits(String v) {
        return v == null ? null : v.replaceAll("\\D", "");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) {
                return v.trim();
            }
        }
        return null;
    }
}
