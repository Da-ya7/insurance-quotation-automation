// package com.insurance.quotation.controller;

// import java.util.ArrayList;
// import java.util.LinkedHashMap;
// import java.util.List;
// import java.util.Map;

// import org.springframework.http.HttpStatus;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.PathVariable;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestBody;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RequestParam;
// import org.springframework.web.bind.annotation.RestController;
// import org.springframework.web.server.ResponseStatusException;

// import com.insurance.quotation.entity.Client;
// import com.insurance.quotation.entity.MissingField;
// import com.insurance.quotation.entity.RequestFieldValue;
// import com.insurance.quotation.entity.RequiredField;
// import com.insurance.quotation.repository.MissingFieldRepository;
// import com.insurance.quotation.repository.RequestFieldValueRepository;
// import com.insurance.quotation.repository.RequiredFieldRepository;
// import com.insurance.quotation.service.client.IdentityProtectionService;
// import com.insurance.quotation.service.master.FormLinkService;
// import com.insurance.quotation.service.master.InsuranceTypeDef;
// import com.insurance.quotation.service.master.MasterDataService;
// import com.insurance.quotation.service.otp.OtpService;
// import com.insurance.quotation.service.submission.GenericSubmissionService;
// import com.insurance.quotation.service.validation.ValidationService;

// /** Everything the public (no login) forms need. */
// @RestController
// @RequestMapping("/api/public")
// public class PublicFormController {

//     private final MasterDataService masterData;
//     private final FormLinkService formLinks;
//     private final OtpService otp;
//     private final GenericSubmissionService submissions;
//     private final RequiredFieldRepository requiredFieldRepository;
//     private final RequestFieldValueRepository fieldValueRepository;
//     private final MissingFieldRepository missingFieldRepository;

//     public PublicFormController(
//             MasterDataService masterData, FormLinkService formLinks, OtpService otp,
//             GenericSubmissionService submissions, RequiredFieldRepository requiredFieldRepository,
//             RequestFieldValueRepository fieldValueRepository,
//             MissingFieldRepository missingFieldRepository) {
//         this.masterData = masterData;
//         this.formLinks = formLinks;
//         this.otp = otp;
//         this.submissions = submissions;
//         this.requiredFieldRepository = requiredFieldRepository;
//         this.fieldValueRepository = fieldValueRepository;
//         this.missingFieldRepository = missingFieldRepository;
//     }

//     // ---------------- master data for dropdowns ----------------

//     public record TypeOption(String code, String label) {
//     }

//     public record CountryOption(String code, String name) {
//     }

//     public record IdentityOption(String code, String label, String example) {
//     }

//     public record NewCustomerOtpSendRequest(String phone) {
//     }

//     public record NewCustomerOtpVerifyRequest(String challengeId, String code) {
//     }

//     @GetMapping("/master/insurance-types")
//     public List<TypeOption> insuranceTypes() {
//         return masterData.insuranceTypes(true).stream()
//                 .map(t -> new TypeOption(t.code(), t.label())).toList();
//     }

//     @GetMapping("/master/countries")
//     public List<CountryOption> countries() {
//         return masterData.countries(true).stream()
//                 .map(c -> new CountryOption(c.code(), c.name())).toList();
//     }

//     @GetMapping("/master/identity-types")
//     public List<IdentityOption> identityTypes(@RequestParam(required = false) String country) {
//         return masterData.identityTypesForCountry(country, true).stream()
//                 .map(t -> new IdentityOption(t.code(), t.label(), t.example())).toList();
//     }

//     // ---------------- dynamic form definition ----------------

//     public record FormField(String fieldName, String displayLabel, String dataType,
//                             boolean isMandatory, String helpText, int sortOrder) {
//     }

//     public record FormDefinition(String insuranceType, String label, List<FormField> fields) {
//     }

//     @GetMapping("/forms/{type}")
//     public FormDefinition form(@PathVariable String type) {
//         InsuranceTypeDef def = masterData.insuranceType(type)
//                 .filter(InsuranceTypeDef::active)
//                 .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown insurance type"));

//         List<FormField> fields = requiredFieldRepository.findByIsActiveTrueOrderBySortOrderAscIdAsc().stream()
//                 .filter(f -> ValidationService.appliesTo(f, def.code()))
//                 .filter(f -> !GenericSubmissionService.APPLICANT_FIELDS.contains(f.getFieldName()))
//                 .map(f -> new FormField(f.getFieldName(), f.getDisplayLabel(), f.getDataType().name(),
//                         !Boolean.FALSE.equals(f.getIsMandatory()), f.getHelpText(),
//                         f.getSortOrder() == null ? 0 : f.getSortOrder()))
//                 .toList();
//         return new FormDefinition(def.code(), def.label(), fields);
//     }

//     // ---------------- new customer phone verification ----------------

//     @PostMapping("/phone-otp/send")
//     public ResponseEntity<?> sendNewCustomerOtp(
//             @RequestBody NewCustomerOtpSendRequest body) {

//         if (body == null || body.phone() == null || body.phone().isBlank()) {
//             return ResponseEntity.badRequest().body(
//                     Map.of("sent", false, "message", "Please enter your phone number."));
//         }

//         try {
//             OtpService.NewCustomerSendResult result =
//                     otp.sendNewCustomerOtp(body.phone());

//             return ResponseEntity.ok(Map.of(
//                     "sent", true,
//                     "challengeId", result.challengeId(),
//                     "phoneMasked", result.phoneMasked(),
//                     "expiresInSeconds", result.expiresInSeconds()
//             ));

//         } catch (OtpService.OtpException e) {
//             HttpStatus status = switch (e.getReason()) {
//                 case NO_PHONE, INVALID_CHALLENGE -> HttpStatus.BAD_REQUEST;
//                 case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
//                 case SEND_FAILED -> HttpStatus.BAD_GATEWAY;
//             };

//             return ResponseEntity.status(status).body(
//                     Map.of("sent", false, "message", e.getMessage()));
//         }
//     }

//     @PostMapping("/phone-otp/verify")
//     public ResponseEntity<?> verifyNewCustomerOtp(
//             @RequestBody NewCustomerOtpVerifyRequest body) {

//         if (body == null || body.challengeId() == null || body.code() == null) {
//             return ResponseEntity.badRequest().body(Map.of(
//                     "verified", false,
//                     "message", "Challenge ID and OTP are required."
//             ));
//         }

//         try {
//             OtpService.NewCustomerVerifyResult result =
//                     otp.verifyNewCustomerOtp(body.challengeId(), body.code());

//             if (!result.verified()) {
//                 return ResponseEntity.badRequest().body(Map.of(
//                         "verified", false,
//                         "attemptsLeft", result.attemptsLeft(),
//                         "message", result.message()
//                 ));
//             }

//             return ResponseEntity.ok(Map.of(
//                     "verified", true,
//                     "verificationToken", result.verificationToken()
//             ));

//         } catch (OtpService.OtpException e) {
//             return ResponseEntity.badRequest().body(Map.of(
//                     "verified", false,
//                     "message", e.getMessage()
//             ));
//         }
//     }

//     // ---------------- personal form links ----------------

//     @GetMapping("/form-links/{token}")
//     public Map<String, Object> link(@PathVariable String token) {
//         FormLinkService.FormLink link = requireLink(token);
//         Client client = submissions.clientOf(link).orElse(null);

//         Map<String, Object> body = new LinkedHashMap<>();
//         body.put("valid", true);
//         body.put("insuranceType", link.insuranceType());
//         body.put("insuranceTypeLabel", link.insuranceType() == null ? null
//                 : masterData.insuranceType(link.insuranceType()).map(InsuranceTypeDef::label).orElse(null));
//         body.put("requestId", link.requestId());
//         body.put("existingClient", client != null);

//         if (client == null) {
//             body.put("client", null);
//         } else {
//             Map<String, Object> c = new LinkedHashMap<>();
//             c.put("clientCode", client.getClientCode());
//             c.put("name", client.getName());
//             c.put("email", client.getEmail());
//             c.put("phoneMasked", client.getPhone() == null ? null : OtpService.maskPhone(client.getPhone()));
//             c.put("dateOfBirth", client.getDateOfBirth());
//             c.put("gender", client.getGender());
//             c.put("nationality", client.getNationality());
//             c.put("countryOfResidence", client.getCountryOfResidence());
//             c.put("identityCountry", client.getIdentityCountry());
//             c.put("identityType", client.getIdentityType());
//             c.put("identityNumberMasked", IdentityProtectionService.mask(client.getIdentityNumberLast4()));
//             body.put("client", c);
//         }

//         List<String> missing = new ArrayList<>();
//         Map<String, String> prefill = new LinkedHashMap<>();
//         if (link.requestId() != null) {
//             for (MissingField m : missingFieldRepository
//                     .findByQuotationRequestIdAndStatus(link.requestId(), "OPEN")) {
//                 missing.add(m.getRequiredField().getFieldName());
//             }
//             for (RequestFieldValue v : fieldValueRepository.findByQuotationRequestId(link.requestId())) {
//                 RequiredField rf = v.getRequiredField();
//                 if (rf != null && !"identityNumber".equals(rf.getFieldName())
//                         && !GenericSubmissionService.APPLICANT_FIELDS.contains(rf.getFieldName())) {
//                     prefill.put(rf.getFieldName(), v.getFieldValue());
//                 }
//             }
//         }
//         body.put("missingFields", missing);
//         body.put("prefill", prefill);
//         return body;
//     }

//     @PostMapping("/form-links/{token}/otp/send")
//     public ResponseEntity<?> sendOtp(@PathVariable String token) {
//         FormLinkService.FormLink link = requireLink(token);
//         Client client = submissions.clientOf(link).orElse(null);
//         if (client == null) {
//             return ResponseEntity.badRequest().body(Map.of(
//                     "message", "There is no saved client record for this link, so no verification is needed."));
//         }
//         try {
//             OtpService.SendResult r = otp.send(token, client.getPhone());
//             return ResponseEntity.ok(Map.of("sent", true, "phoneMasked", r.phoneMasked(),
//                     "expiresInSeconds", r.expiresInSeconds()));
//         } catch (OtpService.OtpException e) {
//             HttpStatus status = switch (e.getReason()) {
//                 case NO_PHONE -> HttpStatus.BAD_REQUEST;
//                 case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
//                 case SEND_FAILED -> HttpStatus.BAD_GATEWAY;
//                 default -> HttpStatus.BAD_REQUEST;
//             };
//             return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
//         }
//     }

//     public record OtpVerifyRequest(String code) {
//     }

//     @PostMapping("/form-links/{token}/otp/verify")
//     public ResponseEntity<?> verifyOtp(@PathVariable String token, @RequestBody OtpVerifyRequest body) {
//         requireLink(token);
//         OtpService.VerifyResult r = otp.verify(token, body == null ? null : body.code());
//         if (r.verified()) {
//             return ResponseEntity.ok(Map.of("verified", true));
//         }
//         return ResponseEntity.badRequest().body(Map.of(
//                 "verified", false, "attemptsLeft", r.attemptsLeft(), "message", r.message()));
//     }

//     // ---------------- submit ----------------

//     @PostMapping("/form-links/{token}/submit")
//     public GenericSubmissionService.Result submitWithLink(
//             @PathVariable String token, @RequestBody GenericSubmissionService.Submission body) {
//         requireLink(token);
//         return submissions.submit(token, null, body);
//     }

//     @PostMapping("/forms/{type}/submit")
//     public GenericSubmissionService.Result submit(
//             @PathVariable String type, @RequestBody GenericSubmissionService.Submission body) {
//         return submissions.submit(null, type, body);
//     }

//     private FormLinkService.FormLink requireLink(String token) {
//         FormLinkService.FormLink link = formLinks.find(token)
//                 .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This link is not valid."));
//         if (link.isExpired()) {
//             throw new ResponseStatusException(HttpStatus.GONE, "This link has expired.");
//         }
//         return link;
//     }
// }






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
import com.insurance.quotation.repository.ClientRepository;
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
    private final ClientRepository clientRepository;

    public PublicFormController(
            MasterDataService masterData,
            FormLinkService formLinks,
            OtpService otp,
            GenericSubmissionService submissions,
            RequiredFieldRepository requiredFieldRepository,
            RequestFieldValueRepository fieldValueRepository,
            MissingFieldRepository missingFieldRepository,
            ClientRepository clientRepository) {

        this.masterData = masterData;
        this.formLinks = formLinks;
        this.otp = otp;
        this.submissions = submissions;
        this.requiredFieldRepository = requiredFieldRepository;
        this.fieldValueRepository = fieldValueRepository;
        this.missingFieldRepository = missingFieldRepository;
        this.clientRepository = clientRepository;
    }

    // ---------------- master data for dropdowns ----------------

    public record TypeOption(String code, String label) {
    }

    public record CountryOption(String code, String name) {
    }

    public record IdentityOption(String code, String label, String example) {
    }

    public record NewCustomerOtpSendRequest(String phone) {
    }

    public record NewCustomerOtpVerifyRequest(String challengeId, String code) {
    }

    @GetMapping("/master/insurance-types")
    public List<TypeOption> insuranceTypes() {
        return masterData.insuranceTypes(true).stream()
                .map(t -> new TypeOption(t.code(), t.label()))
                .toList();
    }

    @GetMapping("/master/countries")
    public List<CountryOption> countries() {
        return masterData.countries(true).stream()
                .map(c -> new CountryOption(c.code(), c.name()))
                .toList();
    }

    @GetMapping("/master/identity-types")
    public List<IdentityOption> identityTypes(
            @RequestParam(required = false) String country) {

        return masterData.identityTypesForCountry(country, true).stream()
                .map(t -> new IdentityOption(t.code(), t.label(), t.example()))
                .toList();
    }

    // ---------------- dynamic form definition ----------------

    public record FormField(
            String fieldName,
            String displayLabel,
            String dataType,
            boolean isMandatory,
            String helpText,
            int sortOrder) {
    }

    public record FormDefinition(
            String insuranceType,
            String label,
            List<FormField> fields) {
    }

    @GetMapping("/forms/{type}")
    public FormDefinition form(@PathVariable String type) {

        InsuranceTypeDef def = masterData.insuranceType(type)
                .filter(InsuranceTypeDef::active)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown insurance type"));

        List<FormField> fields = requiredFieldRepository
                .findByIsActiveTrueOrderBySortOrderAscIdAsc()
                .stream()
                .filter(f -> ValidationService.appliesTo(f, def.code()))
                .filter(f -> !GenericSubmissionService.APPLICANT_FIELDS
                        .contains(f.getFieldName()))
                .map(f -> new FormField(
                        f.getFieldName(),
                        f.getDisplayLabel(),
                        f.getDataType().name(),
                        !Boolean.FALSE.equals(f.getIsMandatory()),
                        f.getHelpText(),
                        f.getSortOrder() == null ? 0 : f.getSortOrder()))
                .toList();

        return new FormDefinition(def.code(), def.label(), fields);
    }

    // ---------------- new customer phone verification ----------------

    @PostMapping("/phone-otp/send")
    public ResponseEntity<?> sendNewCustomerOtp(
            @RequestBody NewCustomerOtpSendRequest body) {

        if (body == null || body.phone() == null || body.phone().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "sent", false,
                    "message", "Please enter your phone number."));
        }

        try {
            OtpService.NewCustomerSendResult result =
                    otp.sendNewCustomerOtp(body.phone());

            return ResponseEntity.ok(Map.of(
                    "sent", true,
                    "challengeId", result.challengeId(),
                    "phoneMasked", result.phoneMasked(),
                    "expiresInSeconds", result.expiresInSeconds()));

        } catch (OtpService.OtpException e) {

            HttpStatus status = switch (e.getReason()) {
                case NO_PHONE, INVALID_CHALLENGE -> HttpStatus.BAD_REQUEST;
                case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
                case SEND_FAILED -> HttpStatus.BAD_GATEWAY;
            };

            return ResponseEntity.status(status).body(Map.of(
                    "sent", false,
                    "message", e.getMessage()));
        }
    }

    @PostMapping("/phone-otp/verify")
    public ResponseEntity<?> verifyNewCustomerOtp(
            @RequestBody NewCustomerOtpVerifyRequest body) {

        if (body == null || body.challengeId() == null || body.code() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "verified", false,
                    "message", "Challenge ID and OTP are required."));
        }

        try {
            OtpService.NewCustomerVerifyResult result =
                    otp.verifyNewCustomerOtp(body.challengeId(), body.code());

            if (!result.verified()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "verified", false,
                        "attemptsLeft", result.attemptsLeft(),
                        "message", result.message()));
            }

            // Retrieve the phone number only from the successfully verified token.
            String verifiedPhone =
                    otp.verifiedPhoneForToken(result.verificationToken());

            if (verifiedPhone == null || verifiedPhone.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "verified", false,
                        "message", "Phone verification expired. Please verify your number again."));
            }

            // Match saved phone numbers after removing formatting characters.
            List<Client> matches = clientRepository.findAllByPhoneIsNotNull()
                    .stream()
                    .filter(client -> normalizePhone(client.getPhone())
                            .equals(verifiedPhone))
                    .toList();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("verified", true);
            response.put("verificationToken", result.verificationToken());

            if (matches.size() > 1) {
                // Never choose one of multiple people sharing the same phone.
                response.put("existingClient", false);
                response.put("ambiguousPhone", true);
                response.put("client", null);
                response.put("message",
                        "This phone number is linked to multiple customer records. Please contact the support team.");
                return ResponseEntity.ok(response);
            }

            if (matches.isEmpty()) {
                response.put("existingClient", false);
                response.put("ambiguousPhone", false);
                response.put("client", null);
                response.put("message",
                        "Phone verified. Please enter your personal details.");
                return ResponseEntity.ok(response);
            }

            Client client = matches.get(0);

            Map<String, Object> clientDetails = new LinkedHashMap<>();
            clientDetails.put("clientCode", client.getClientCode());
            clientDetails.put("name", client.getName());
            clientDetails.put("email", client.getEmail());
            clientDetails.put("phoneMasked",
                    client.getPhone() == null
                            ? null
                            : OtpService.maskPhone(client.getPhone()));
            clientDetails.put("dateOfBirth", client.getDateOfBirth());
            clientDetails.put("gender", client.getGender());
            clientDetails.put("nationality", client.getNationality());
            clientDetails.put("countryOfResidence",
                    client.getCountryOfResidence());
            clientDetails.put("identityCountry", client.getIdentityCountry());
            clientDetails.put("identityType", client.getIdentityType());

            // Never return the encrypted identity number or its full value.
            clientDetails.put(
                    "identityNumberMasked",
                    IdentityProtectionService.mask(client.getIdentityNumberLast4()));

            response.put("existingClient", true);
            response.put("ambiguousPhone", false);
            response.put("client", clientDetails);
            response.put("message",
                    "Phone verified. Your existing personal details have been found.");

            return ResponseEntity.ok(response);

        } catch (OtpService.OtpException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "verified", false,
                    "message", e.getMessage()));
        }
    }

    private static String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        return phone.replaceAll("\\D", "");
    }

    // ---------------- personal form links ----------------

    @GetMapping("/form-links/{token}")
    public Map<String, Object> link(@PathVariable String token) {

        FormLinkService.FormLink link = requireLink(token);
        Client client = submissions.clientOf(link).orElse(null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("valid", true);
        body.put("insuranceType", link.insuranceType());
        body.put("insuranceTypeLabel", link.insuranceType() == null
                ? null
                : masterData.insuranceType(link.insuranceType())
                        .map(InsuranceTypeDef::label)
                        .orElse(null));
        body.put("requestId", link.requestId());
        body.put("existingClient", client != null);

        if (client == null) {
            body.put("client", null);
        } else {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("clientCode", client.getClientCode());
            c.put("name", client.getName());
            c.put("email", client.getEmail());
            c.put("phoneMasked", client.getPhone() == null
                    ? null
                    : OtpService.maskPhone(client.getPhone()));
            c.put("dateOfBirth", client.getDateOfBirth());
            c.put("gender", client.getGender());
            c.put("nationality", client.getNationality());
            c.put("countryOfResidence", client.getCountryOfResidence());
            c.put("identityCountry", client.getIdentityCountry());
            c.put("identityType", client.getIdentityType());
            c.put("identityNumberMasked",
                    IdentityProtectionService.mask(client.getIdentityNumberLast4()));
            body.put("client", c);
        }

        List<String> missing = new ArrayList<>();
        Map<String, String> prefill = new LinkedHashMap<>();

        if (link.requestId() != null) {
            for (MissingField m : missingFieldRepository
                    .findByQuotationRequestIdAndStatus(link.requestId(), "OPEN")) {
                missing.add(m.getRequiredField().getFieldName());
            }

            for (RequestFieldValue v : fieldValueRepository
                    .findByQuotationRequestId(link.requestId())) {

                RequiredField rf = v.getRequiredField();

                if (rf != null
                        && !"identityNumber".equals(rf.getFieldName())
                        && !GenericSubmissionService.APPLICANT_FIELDS
                                .contains(rf.getFieldName())) {
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
                    "message",
                    "There is no saved client record for this link, so no verification is needed."));
        }

        try {
            OtpService.SendResult r = otp.send(token, client.getPhone());

            return ResponseEntity.ok(Map.of(
                    "sent", true,
                    "phoneMasked", r.phoneMasked(),
                    "expiresInSeconds", r.expiresInSeconds()));

        } catch (OtpService.OtpException e) {

            HttpStatus status = switch (e.getReason()) {
                case NO_PHONE -> HttpStatus.BAD_REQUEST;
                case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
                case SEND_FAILED -> HttpStatus.BAD_GATEWAY;
                default -> HttpStatus.BAD_REQUEST;
            };

            return ResponseEntity.status(status).body(Map.of(
                    "message", e.getMessage()));
        }
    }

    public record OtpVerifyRequest(String code) {
    }

    @PostMapping("/form-links/{token}/otp/verify")
    public ResponseEntity<?> verifyOtp(
            @PathVariable String token,
            @RequestBody OtpVerifyRequest body) {

        requireLink(token);

        OtpService.VerifyResult r =
                otp.verify(token, body == null ? null : body.code());

        if (r.verified()) {
            return ResponseEntity.ok(Map.of("verified", true));
        }

        return ResponseEntity.badRequest().body(Map.of(
                "verified", false,
                "attemptsLeft", r.attemptsLeft(),
                "message", r.message()));
    }

    // ---------------- submit ----------------

    @PostMapping("/form-links/{token}/submit")
    public GenericSubmissionService.Result submitWithLink(
            @PathVariable String token,
            @RequestBody GenericSubmissionService.Submission body) {

        requireLink(token);
        return submissions.submit(token, null, body);
    }

    @PostMapping("/forms/{type}/submit")
    public GenericSubmissionService.Result submit(
            @PathVariable String type,
            @RequestBody GenericSubmissionService.Submission body) {

        return submissions.submit(null, type, body);
    }

    private FormLinkService.FormLink requireLink(String token) {

        FormLinkService.FormLink link = formLinks.find(token)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "This link is not valid."));

        if (link.isExpired()) {
            throw new ResponseStatusException(
                    HttpStatus.GONE, "This link has expired.");
        }

        return link;
    }
}
