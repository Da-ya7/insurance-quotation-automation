
package com.insurance.quotation.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
import com.insurance.quotation.entity.QuotationRequestTraveller;
import com.insurance.quotation.entity.RequestFieldValue;
import com.insurance.quotation.entity.RequiredField;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.MissingFieldRepository;
import com.insurance.quotation.repository.QuotationRequestTravellerRepository;
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

    private static final Logger log =
            LoggerFactory.getLogger(PublicFormController.class);

    private final MasterDataService masterData;
    private final FormLinkService formLinks;
    private final OtpService otp;
    private final GenericSubmissionService submissions;
    private final RequiredFieldRepository requiredFieldRepository;
    private final RequestFieldValueRepository fieldValueRepository;
    private final MissingFieldRepository missingFieldRepository;
    private final ClientRepository clientRepository;
    private final QuotationRequestTravellerRepository travellerRepository;
    private final IdentityProtectionService identityProtection;

    public PublicFormController(
            MasterDataService masterData,
            FormLinkService formLinks,
            OtpService otp,
            GenericSubmissionService submissions,
            RequiredFieldRepository requiredFieldRepository,
            RequestFieldValueRepository fieldValueRepository,
            MissingFieldRepository missingFieldRepository,
            ClientRepository clientRepository,
            QuotationRequestTravellerRepository travellerRepository,
            IdentityProtectionService identityProtection) {

        this.masterData = masterData;
        this.formLinks = formLinks;
        this.otp = otp;
        this.submissions = submissions;
        this.requiredFieldRepository = requiredFieldRepository;
        this.fieldValueRepository = fieldValueRepository;
        this.missingFieldRepository = missingFieldRepository;
        this.clientRepository = clientRepository;
        this.travellerRepository = travellerRepository;
        this.identityProtection = identityProtection;
    }

    // ---------------- master data for dropdowns ----------------

    public record TypeOption(String code, String label) {
    }

    public record CountryOption(String code, String name, String callingCode) {
    }

    public record IdentityOption(String code, String label, String example) {
    }

    public record NewCustomerOtpSendRequest(String phone) {
    }

    public record NewCustomerOtpVerifyRequest(String challengeId, String code) {
    }

    public record IdentityMatchRequest(
            String verificationToken,
            String identityCountry,
            String identityType,
            String identityNumber) {
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
                .map(c -> new CountryOption(
                        c.code(),
                        c.name(),
                        c.callingCode()))
                .toList();
    }

    @GetMapping("/master/identity-types")
    public List<IdentityOption> identityTypes(
            @RequestParam(required = false) String country) {

        return masterData.identityTypesForCountry(country, true).stream()
                .map(t -> new IdentityOption(
                        t.code(),
                        t.label(),
                        t.example()))
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
                        HttpStatus.NOT_FOUND,
                        "Unknown insurance type"));

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

        return new FormDefinition(
                def.code(),
                def.label(),
                fields);
    }

    // ---------------- new customer phone verification ----------------

    @PostMapping("/phone-otp/send")
    public ResponseEntity<?> sendNewCustomerOtp(
            @RequestBody NewCustomerOtpSendRequest body) {

        if (body == null || isBlank(body.phone())) {
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

    @Transactional(readOnly = true)
    @PostMapping("/phone-otp/verify")
    public ResponseEntity<?> verifyNewCustomerOtp(
            @RequestBody NewCustomerOtpVerifyRequest body) {

        if (body == null
                || isBlank(body.challengeId())
                || isBlank(body.code())) {

            return ResponseEntity.badRequest().body(Map.of(
                    "verified", false,
                    "message", "Challenge ID and OTP are required."));
        }

        try {

            OtpService.NewCustomerVerifyResult result =
                    otp.verifyNewCustomerOtp(
                            body.challengeId(),
                            body.code());

            if (!result.verified()) {

                return ResponseEntity.badRequest().body(Map.of(
                        "verified", false,
                        "attemptsLeft", result.attemptsLeft(),
                        "message", result.message()));
            }

            String verifiedPhone =
                    otp.verifiedPhoneForToken(result.verificationToken());

            if (isBlank(verifiedPhone)) {

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "verified", false,
                        "message",
                        "Phone verification expired. Please verify your number again."));
            }

            String normalizedPhone = normalizePhone(verifiedPhone);

            if (normalizedPhone.isBlank()) {

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "verified", false,
                        "message",
                        "Phone verification expired. Please verify your number again."));
            }

            log.info(
                    "[OTP DEBUG] Verified phone length: {}, last four digits: {}",
                    normalizedPhone.length(),
                    lastFour(normalizedPhone));

            List<Client> matches = findClientsByPhone(normalizedPhone);

            Map<String, Object> response = new LinkedHashMap<>();

            response.put("verified", true);
            response.put("verificationToken", result.verificationToken());

            // CASE 1: Exactly one Client Master record uses this phone.
            if (matches.size() == 1) {

                Client matchedClient = matches.get(0);

                response.put("existingClient", true);
                response.put("ambiguousPhone", false);
                response.put("requiresIdentityCheck", false);
                response.put("newCustomerAllowed", false);
                response.put("client", buildClientDetails(matchedClient));

                response.put(
                        "message",
                        "Phone verified. Your existing customer details have been loaded.");

                log.info(
                        "[OTP DEBUG] One existing client matched: {}",
                        matchedClient.getClientCode());

                return ResponseEntity.ok(response);
            }

            // CASE 2: Multiple customers share this phone.
            if (matches.size() >= 2) {

                response.put("existingClient", false);
                response.put("ambiguousPhone", true);
                response.put("requiresIdentityCheck", true);
                response.put("newCustomerAllowed", false);
                response.put("client", null);

                response.put(
                        "message",
                        "Phone verified. Multiple customers use this number. Please complete additional identity verification.");

                log.info(
                        "[OTP DEBUG] Multiple clients matched. Count: {}",
                        matches.size());

                return ResponseEntity.ok(response);
            }

            // CASE 3: No Client Master record uses this phone.
            response.put("existingClient", false);
            response.put("ambiguousPhone", false);
            response.put("requiresIdentityCheck", false);
            response.put("newCustomerAllowed", true);
            response.put("client", null);

            response.put(
                    "message",
                    "Phone verified. You can continue as a new customer.");

            log.info("[OTP DEBUG] No existing client matched.");

            return ResponseEntity.ok(response);

        } catch (OtpService.OtpException e) {

            return ResponseEntity.badRequest().body(Map.of(
                    "verified", false,
                    "message", e.getMessage()));
        }
    }

    // ---------------- additional identity verification ----------------

    @Transactional(readOnly = true)
    @PostMapping("/phone-otp/verify-identity")
    public ResponseEntity<?> verifyIdentityForSharedPhone(
            @RequestBody IdentityMatchRequest body) {

        if (body == null
                || isBlank(body.verificationToken())
                || isBlank(body.identityCountry())
                || isBlank(body.identityType())
                || isBlank(body.identityNumber())) {

            return ResponseEntity.badRequest().body(Map.of(
                    "matched", false,
                    "newCustomerAllowed", false,
                    "message",
                    "Phone verification, identity country, identity type and identity number are required."));
        }

        String verifiedPhone =
                otp.verifiedPhoneForToken(body.verificationToken());

        if (isBlank(verifiedPhone)) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "matched", false,
                    "newCustomerAllowed", false,
                    "message",
                    "Phone verification expired. Please verify your number again."));
        }

        String normalizedPhone = normalizePhone(verifiedPhone);

        if (normalizedPhone.isBlank()) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "matched", false,
                    "newCustomerAllowed", false,
                    "message",
                    "Phone verification expired. Please verify your number again."));
        }

        // Always find all Client Master records using the verified phone.
        List<Client> phoneMatches = findClientsByPhone(normalizedPhone);

        if (phoneMatches.size() < 2) {

            return ResponseEntity.badRequest().body(Map.of(
                    "matched", false,
                    "newCustomerAllowed", false,
                    "message",
                    "Additional identity verification is only required when multiple customers share a phone number."));
        }

        String normalizedCountry =
                masterData.resolveCountryCode(
                        body.identityCountry().trim());

        String normalizedType =
                MasterDataService.typeCode(
                        body.identityType().trim());

        if (isBlank(normalizedCountry) || isBlank(normalizedType)) {

            return ResponseEntity.badRequest().body(Map.of(
                    "matched", false,
                    "newCustomerAllowed", false,
                    "message",
                    "Please select a valid identity country and identity type."));
        }

        String submittedIdentity = body.identityNumber().trim();

        // First check the protected identity hash in Client Master.
        String submittedHash = identityProtection.hash(
                normalizedCountry,
                normalizedType,
                submittedIdentity);

        Optional<Client> identityOwner =
                clientRepository.findByIdentityNumberHash(submittedHash);

        if (identityOwner.isPresent()) {

            Client owner = identityOwner.get();

            // Identity and verified phone must belong to the same Client.
            if (!belongsToPhoneMatches(owner, phoneMatches)) {

                return duplicateIdentityResponse();
            }

            return matchedClientResponse(
                    owner,
                    body.verificationToken());
        }

        /*
         * Older records may have the passport stored in
         * quotation_request_travellers instead of Client Master.
         */
        List<Client> travellerMatches =
                findClientsByPreviousTravellerIdentity(submittedIdentity);

        List<Client> validMatches = travellerMatches.stream()
                .filter(client -> belongsToPhoneMatches(client, phoneMatches))
                .toList();

        if (validMatches.size() == 1) {

            return matchedClientResponse(
                    validMatches.get(0),
                    body.verificationToken());
        }

        if (validMatches.size() > 1) {

            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "matched", false,
                    "newCustomerAllowed", false,
                    "ambiguousPhone", true,
                    "message",
                    "More than one customer record matches this identity number. Please contact support."));
        }

        /*
         * If the identity exists in previous traveller records but
         * belongs to a different Client Master phone, block registration.
         */
        if (!travellerMatches.isEmpty()) {

            return duplicateIdentityResponse();
        }

        // Identity was not found in Client Master or traveller history.
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("matched", false);
        response.put("existingClient", false);
        response.put("duplicateIdentity", false);
        response.put("newCustomerAllowed", true);
        response.put("ambiguousPhone", true);
        response.put("verificationToken", body.verificationToken());
        response.put("client", null);

        response.put(
                "message",
                "This identity number is not registered. You can continue as a new family member.");

        return ResponseEntity.ok(response);
    }

    /**
     * Searches previous primary-traveller records for the submitted
     * identity number and returns their associated Client Master records.
     */
    private List<Client> findClientsByPreviousTravellerIdentity(
            String submittedIdentity) {

        String normalizedIdentity =
                normalizeIdentityNumber(submittedIdentity);

        if (normalizedIdentity.isBlank()) {
            return List.of();
        }

        List<QuotationRequestTraveller> previousTravellers =
                travellerRepository
                        .findByTravellerNumberOrderByCreatedAtDesc(1);

        Map<Long, Client> uniqueClients = new LinkedHashMap<>();

        for (QuotationRequestTraveller traveller : previousTravellers) {

            if (traveller == null) {
                continue;
            }

            String previousIdentity =
                    normalizeIdentityNumber(traveller.getPassportNumber());

            if (!normalizedIdentity.equals(previousIdentity)) {
                continue;
            }

            if (traveller.getQuotationRequest() == null
                    || traveller.getQuotationRequest().getClient() == null) {
                continue;
            }

            Client client =
                    traveller.getQuotationRequest().getClient();

            if (client.getId() == null) {
                continue;
            }

            uniqueClients.putIfAbsent(client.getId(), client);
        }

        return new ArrayList<>(uniqueClients.values());
    }

    /**
     * Confirms that the matched Client Master record is one of
     * the customers associated with the OTP-verified phone.
     */
    private boolean belongsToPhoneMatches(
            Client client,
            List<Client> phoneMatches) {

        if (client == null || client.getId() == null) {
            return false;
        }

        return phoneMatches.stream()
                .anyMatch(candidate ->
                        candidate != null
                                && Objects.equals(
                                        candidate.getId(),
                                        client.getId()));
    }

    private ResponseEntity<?> matchedClientResponse(
            Client client,
            String verificationToken) {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("matched", true);
        response.put("existingClient", true);
        response.put("duplicateIdentity", false);
        response.put("newCustomerAllowed", false);
        response.put("ambiguousPhone", true);
        response.put("verificationToken", verificationToken);
        response.put("client", buildClientDetails(client));

        response.put(
                "message",
                "Identity verified. Your matching customer's details have been loaded.");

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<?> duplicateIdentityResponse() {

        return ResponseEntity.ok(Map.of(
                "matched", false,
                "existingClient", false,
                "duplicateIdentity", true,
                "newCustomerAllowed", false,
                "ambiguousPhone", true,
                "message",
                "This identity number is already registered to another customer. It cannot be used for this phone number."));
    }

    // ---------------- shared customer matching helpers ----------------

    /**
     * Uses the repository's native normalized-phone query.
     *
     * This avoids loading every Client Master record into Java.
     * The repository query must compare phone values after removing
     * spaces, + signs, hyphens and other non-digit characters.
     */
    private List<Client> findClientsByPhone(String normalizedPhone) {

        if (isBlank(normalizedPhone)) {
            log.warn("[OTP DEBUG] Phone lookup skipped because phone is blank.");
            return List.of();
        }

        String phone = normalizePhone(normalizedPhone);

        if (phone.isBlank()) {
            log.warn("[OTP DEBUG] Phone lookup skipped because normalized phone is blank.");
            return List.of();
        }

        log.info(
                "[OTP DEBUG] Running normalized phone query. Length: {}, last four digits: {}",
                phone.length(),
                lastFour(phone));

        List<Client> matches =
                clientRepository.findAllByNormalizedPhone(phone);

        log.info(
                "[OTP DEBUG] Matching clients count: {}",
                matches.size());

        for (Client client : matches) {
            log.info(
                    "[OTP DEBUG] Matched Client ID: {}",
                    client.getClientCode());
        }

        return matches;
    }

    /**
     * Builds the response from the exact matched Client Master record.
     * Never returns the full identity number.
     */
    private Map<String, Object> buildClientDetails(Client client) {

        Map<String, Object> clientDetails = new LinkedHashMap<>();

        clientDetails.put("clientCode", client.getClientCode());
        clientDetails.put("name", client.getName());
        clientDetails.put("email", client.getEmail());

        clientDetails.put(
                "phoneMasked",
                client.getPhone() == null
                        ? null
                        : OtpService.maskPhone(client.getPhone()));

        clientDetails.put("dateOfBirth", client.getDateOfBirth());
        clientDetails.put("gender", client.getGender());
        clientDetails.put("nationality", client.getNationality());
        clientDetails.put("countryOfResidence", client.getCountryOfResidence());

        clientDetails.put("identityCountry", client.getIdentityCountry());
        clientDetails.put("identityType", client.getIdentityType());

        clientDetails.put(
                "identityNumberMasked",
                IdentityProtectionService.mask(
                        client.getIdentityNumberLast4()));

        return clientDetails;
    }

    private static String normalizeIdentityNumber(String value) {

        if (value == null) {
            return "";
        }

        return value.replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizePhone(String phone) {

        if (phone == null || phone.isBlank()) {
            return "";
        }

        String digits = phone.replaceAll("\\D", "");

        // Normalize Indian numbers stored with country code 91.
        if (digits.startsWith("91") && digits.length() == 12) {
            return digits.substring(2);
        }

        return digits;
    }

    private static String lastFour(String value) {

        if (value == null || value.isBlank()) {
            return "";
        }

        return value.substring(Math.max(0, value.length() - 4));
    }

    // ---------------- personal form links ----------------

    @GetMapping("/form-links/{token}")
    public Map<String, Object> link(@PathVariable String token) {

        FormLinkService.FormLink link = requireLink(token);
        Client client = submissions.clientOf(link).orElse(null);

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("valid", true);
        body.put("insuranceType", link.insuranceType());

        body.put(
                "insuranceTypeLabel",
                link.insuranceType() == null
                        ? null
                        : masterData.insuranceType(link.insuranceType())
                                .map(InsuranceTypeDef::label)
                                .orElse(null));

        body.put("requestId", link.requestId());
        body.put("existingClient", client != null);

        if (client == null) {
            body.put("client", null);
        } else {
            body.put("client", buildClientDetails(client));
        }

        List<String> missing = new ArrayList<>();
        Map<String, String> prefill = new LinkedHashMap<>();

        if (link.requestId() != null) {

            for (MissingField m : missingFieldRepository
                    .findByQuotationRequestIdAndStatus(
                            link.requestId(),
                            "OPEN")) {

                missing.add(m.getRequiredField().getFieldName());
            }

            for (RequestFieldValue v : fieldValueRepository
                    .findByQuotationRequestId(link.requestId())) {

                RequiredField rf = v.getRequiredField();

                if (rf != null
                        && !"identityNumber".equals(rf.getFieldName())
                        && !GenericSubmissionService.APPLICANT_FIELDS
                                .contains(rf.getFieldName())) {

                    prefill.put(
                            rf.getFieldName(),
                            v.getFieldValue());
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

            OtpService.SendResult r =
                    otp.send(token, client.getPhone());

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
                otp.verify(
                        token,
                        body == null ? null : body.code());

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
                        HttpStatus.NOT_FOUND,
                        "This link is not valid."));

        if (link.isExpired()) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "This link has expired.");
        }

        return link;
    }
}
