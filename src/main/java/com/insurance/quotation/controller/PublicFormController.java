package com.insurance.quotation.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.MissingField;
import com.insurance.quotation.entity.RequestFieldValue;
import com.insurance.quotation.entity.RequiredField;
import com.insurance.quotation.repository.MissingFieldRepository;
import com.insurance.quotation.repository.RequestFieldValueRepository;
import com.insurance.quotation.repository.RequiredFieldRepository;
import com.insurance.quotation.service.client.IdentityProtectionService;
import com.insurance.quotation.service.master.FormLinkService;
import com.insurance.quotation.service.master.InsuranceTypeDef;
import com.insurance.quotation.service.master.MasterDataService;
import com.insurance.quotation.service.otp.OtpService;
import com.insurance.quotation.service.submission.GenericSubmissionService;
import com.insurance.quotation.service.validation.ValidationService;

/** Everything the public (no login) forms need. */
@RestController
@RequestMapping("/api/public")
public class PublicFormController {

    private final MasterDataService masterData;
    private final FormLinkService formLinks;
    private final OtpService otp;
    private final GenericSubmissionService submissions;
    private final RequiredFieldRepository requiredFieldRepository;
    private final RequestFieldValueRepository fieldValueRepository;
    private final MissingFieldRepository missingFieldRepository;

    public PublicFormController(
            MasterDataService masterData, FormLinkService formLinks, OtpService otp,
            GenericSubmissionService submissions, RequiredFieldRepository requiredFieldRepository,
            RequestFieldValueRepository fieldValueRepository,
            MissingFieldRepository missingFieldRepository) {
        this.masterData = masterData;
        this.formLinks = formLinks;
        this.otp = otp;
        this.submissions = submissions;
        this.requiredFieldRepository = requiredFieldRepository;
        this.fieldValueRepository = fieldValueRepository;
        this.missingFieldRepository = missingFieldRepository;
    }

    // ---------------- master data for dropdowns ----------------

    public record TypeOption(String code, String label) {
    }

    public record CountryOption(String code, String name) {
    }

    public record IdentityOption(String code, String label, String example) {
    }

    @GetMapping("/master/insurance-types")
    public List<TypeOption> insuranceTypes() {
        return masterData.insuranceTypes(true).stream()
                .map(t -> new TypeOption(t.code(), t.label())).toList();
    }

    @GetMapping("/master/countries")
    public List<CountryOption> countries() {
        return masterData.countries(true).stream()
                .map(c -> new CountryOption(c.code(), c.name())).toList();
    }

    @GetMapping("/master/identity-types")
    public List<IdentityOption> identityTypes(@RequestParam(required = false) String country) {
        return masterData.identityTypesForCountry(country, true).stream()
                .map(t -> new IdentityOption(t.code(), t.label(), t.example())).toList();
    }

    // ---------------- dynamic form definition ----------------

    public record FormField(String fieldName, String displayLabel, String dataType,
                            boolean isMandatory, String helpText, int sortOrder) {
    }

    public record FormDefinition(String insuranceType, String label, List<FormField> fields) {
    }

    @GetMapping("/forms/{type}")
    public FormDefinition form(@PathVariable String type) {
        InsuranceTypeDef def = masterData.insuranceType(type)
                .filter(InsuranceTypeDef::active)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown insurance type"));

        List<FormField> fields = requiredFieldRepository.findByIsActiveTrueOrderBySortOrderAscIdAsc().stream()
                .filter(f -> ValidationService.appliesTo(f, def.code()))
                .filter(f -> !GenericSubmissionService.APPLICANT_FIELDS.contains(f.getFieldName()))
                .map(f -> new FormField(f.getFieldName(), f.getDisplayLabel(), f.getDataType().name(),
                        !Boolean.FALSE.equals(f.getIsMandatory()), f.getHelpText(),
                        f.getSortOrder() == null ? 0 : f.getSortOrder()))
                .toList();
        return new FormDefinition(def.code(), def.label(), fields);
    }

    // ---------------- personal form links ----------------

    @GetMapping("/form-links/{token}")
    public Map<String, Object> link(@PathVariable String token) {
        FormLinkService.FormLink link = requireLink(token);
        Client client = submissions.clientOf(link).orElse(null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("valid", true);
        body.put("insuranceType", link.insuranceType());
        body.put("insuranceTypeLabel", link.insuranceType() == null ? null
                : masterData.insuranceType(link.insuranceType()).map(InsuranceTypeDef::label).orElse(null));
        body.put("requestId", link.requestId());
        body.put("existingClient", client != null);

        if (client == null) {
            body.put("client", null);
        } else {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("clientCode", client.getClientCode());
            c.put("name", client.getName());
            c.put("email", client.getEmail());
            c.put("phoneMasked", client.getPhone() == null ? null : OtpService.maskPhone(client.getPhone()));
            c.put("dateOfBirth", client.getDateOfBirth());
            c.put("gender", client.getGender());
            c.put("nationality", client.getNationality());
            c.put("countryOfResidence", client.getCountryOfResidence());
            c.put("identityCountry", client.getIdentityCountry());
            c.put("identityType", client.getIdentityType());
            c.put("identityNumberMasked", IdentityProtectionService.mask(client.getIdentityNumberLast4()));
            body.put("client", c);
        }

        List<String> missing = new ArrayList<>();
        Map<String, String> prefill = new LinkedHashMap<>();
        if (link.requestId() != null) {
            for (MissingField m : missingFieldRepository
                    .findByQuotationRequestIdAndStatus(link.requestId(), "OPEN")) {
                missing.add(m.getRequiredField().getFieldName());
            }
            for (RequestFieldValue v : fieldValueRepository.findByQuotationRequestId(link.requestId())) {
                RequiredField rf = v.getRequiredField();
                if (rf != null && !"identityNumber".equals(rf.getFieldName())
                        && !GenericSubmissionService.APPLICANT_FIELDS.contains(rf.getFieldName())) {
                    prefill.put(rf.getFieldName(), v.getFieldValue());
                }
            }
        }
        body.put("missingFields", missing);
        body.put("prefill", prefill);
        return body;
    }

    @PostMapping("/form-links/{token}/otp/send")
    public ResponseEntity<?> sendOtp(@PathVariable String token) {
        FormLinkService.FormLink link = requireLink(token);
        Client client = submissions.clientOf(link).orElse(null);
        if (client == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "There is no saved client record for this link, so no verification is needed."));
        }
        try {
            OtpService.SendResult r = otp.send(token, client.getPhone());
            return ResponseEntity.ok(Map.of("sent", true, "phoneMasked", r.phoneMasked(),
                    "expiresInSeconds", r.expiresInSeconds()));
        } catch (OtpService.OtpException e) {
            HttpStatus status = switch (e.getReason()) {
                case NO_PHONE -> HttpStatus.BAD_REQUEST;
                case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
                case SEND_FAILED -> HttpStatus.BAD_GATEWAY;
            };
            return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
        }
    }

    public record OtpVerifyRequest(String code) {
    }

    @PostMapping("/form-links/{token}/otp/verify")
    public ResponseEntity<?> verifyOtp(@PathVariable String token, @RequestBody OtpVerifyRequest body) {
        requireLink(token);
        OtpService.VerifyResult r = otp.verify(token, body == null ? null : body.code());
        if (r.verified()) {
            return ResponseEntity.ok(Map.of("verified", true));
        }
        return ResponseEntity.badRequest().body(Map.of(
                "verified", false, "attemptsLeft", r.attemptsLeft(), "message", r.message()));
    }

    // ---------------- submit ----------------

    @PostMapping("/form-links/{token}/submit")
    public GenericSubmissionService.Result submitWithLink(
            @PathVariable String token, @RequestBody GenericSubmissionService.Submission body) {
        requireLink(token);
        return submissions.submit(token, null, body);
    }

    @PostMapping("/forms/{type}/submit")
    public GenericSubmissionService.Result submit(
            @PathVariable String type, @RequestBody GenericSubmissionService.Submission body) {
        return submissions.submit(null, type, body);
    }

    private FormLinkService.FormLink requireLink(String token) {
        FormLinkService.FormLink link = formLinks.find(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This link is not valid."));
        if (link.isExpired()) {
            throw new ResponseStatusException(HttpStatus.GONE, "This link has expired.");
        }
        return link;
    }
}
