
package com.insurance.quotation.service.validation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insurance.quotation.entity.MissingField;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.RequestFieldValue;
import com.insurance.quotation.entity.RequiredField;
import com.insurance.quotation.entity.enums.FieldDataType;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.repository.MissingFieldRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.repository.RequestFieldValueRepository;
import com.insurance.quotation.repository.RequiredFieldRepository;
import com.insurance.quotation.service.client.IdentityProtectionService;
import com.insurance.quotation.service.extraction.ExtractedFieldData;
import com.insurance.quotation.service.extraction.ValueNormalizer;
import com.insurance.quotation.service.master.MasterDataService;

@Service
public class ValidationService {

    private final RequiredFieldRepository requiredFieldRepository;
    private final RequestFieldValueRepository requestFieldValueRepository;
    private final MissingFieldRepository missingFieldRepository;
    private final QuotationRequestRepository quotationRequestRepository;
    private final MasterDataService masterData;

    public ValidationService(
            RequiredFieldRepository requiredFieldRepository,
            RequestFieldValueRepository requestFieldValueRepository,
            MissingFieldRepository missingFieldRepository,
            QuotationRequestRepository quotationRequestRepository,
            MasterDataService masterData) {

        this.requiredFieldRepository = requiredFieldRepository;
        this.requestFieldValueRepository = requestFieldValueRepository;
        this.missingFieldRepository = missingFieldRepository;
        this.quotationRequestRepository = quotationRequestRepository;
        this.masterData = masterData;
    }

    /**
     * Checks whether a required field applies to the selected policy type.
     */
    public static boolean appliesTo(
            RequiredField field,
            String policyType) {

        String configured = field.getPolicyType();

        if (configured == null || configured.isBlank()) {
            return true;
        }

        if (policyType == null || policyType.isBlank()) {
            return false;
        }

        for (String type : configured.split(",")) {

            if (type.trim().equalsIgnoreCase(policyType.trim())) {
                return true;
            }
        }

        return false;
    }

    @Transactional
    public ValidationResult validate(
            QuotationRequest quotationRequest,
            List<ExtractedFieldData> extractedFields) {

        if (quotationRequest == null) {
            throw new IllegalArgumentException(
                    "Quotation request cannot be null");
        }

        if (extractedFields == null) {
            extractedFields = List.of();
        }

        quotationRequest.setStatus(RequestStatus.VALIDATING);

        List<RequiredField> applicable =
                requiredFieldRepository
                        .findByIsActiveTrueOrderBySortOrderAscIdAsc()
                        .stream()
                        .filter(field ->
                                appliesTo(
                                        field,
                                        quotationRequest.getPolicyType()))
                        .toList();

        if (applicable.isEmpty()) {
            throw new IllegalStateException(
                    "No active required fields are configured for insurance type: "
                            + quotationRequest.getPolicyType());
        }

        // Normalize extracted field names for consistent matching.
        Map<String, ExtractedFieldData> byName = new HashMap<>();

        for (ExtractedFieldData field : extractedFields) {

            if (field == null || field.getFieldName() == null) {
                continue;
            }

            String key = normalize(field.getFieldName());

            ExtractedFieldData existing = byName.get(key);

            // Prefer a valid, non-empty value over an empty extraction.
            if (existing == null
                    || ((!existing.isMissing())
                    && !isBlank(existing.getFieldValue()))) {

                if (!field.isMissing()
                        && !isBlank(field.getFieldValue())) {
                    byName.put(key, field);
                } else if (existing == null) {
                    byName.put(key, field);
                }
            } else {
                byName.put(key, field);
            }
        }

        List<String> missing = new ArrayList<>();
        List<String> invalid = new ArrayList<>();

        RequiredField identityNumberField = null;

        for (RequiredField requiredField : applicable) {

            if ("identitynumber".equals(
                    normalize(requiredField.getFieldName()))) {

                identityNumberField = requiredField;
            }

            ExtractedFieldData extracted =
                    byName.get(normalize(requiredField.getFieldName()));

            boolean absent =
                    extracted == null
                            || extracted.isMissing()
                            || isBlank(extracted.getFieldValue());

            if (absent) {

                if (Boolean.FALSE.equals(
                        requiredField.getIsMandatory())) {
                    continue;
                }

                missing.add(requiredField.getDisplayLabel());

                openMissingField(
                        quotationRequest,
                        requiredField);

                continue;
            }

            String value = ValueNormalizer.normalize(
                    extracted.getFieldValue(),
                    requiredField.getDataType());

            if (!isValidValue(value, requiredField.getDataType())
                    || !matchesRegex(
                            value,
                            requiredField.getValidationRegex())) {

                invalid.add(requiredField.getDisplayLabel());

                openMissingField(
                        quotationRequest,
                        requiredField);

                continue;
            }

            // Save the valid field value for this quotation request.
            saveValue(
                    quotationRequest,
                    requiredField,
                    value,
                    extracted);

            // Resolve any previous missing-field record.
            resolveMissingField(
                    quotationRequest,
                    requiredField);
        }

        // Country-specific identity-document validation.
        ExtractedFieldData idType =
                byName.get("identitytype");

        ExtractedFieldData idNumber =
                byName.get("identitynumber");

        if (idType != null
                && idNumber != null
                && !isBlank(idType.getFieldValue())
                && !isBlank(idNumber.getFieldValue())) {

            ExtractedFieldData idCountry =
                    byName.get("identitycountry");

            String country =
                    idCountry != null
                            && !isBlank(idCountry.getFieldValue())
                            ? idCountry.getFieldValue()
                            : quotationRequest.getClient() == null
                            ? null
                            : quotationRequest.getClient()
                                    .getIdentityCountry();

            var problem = masterData.validateIdentity(
                    country,
                    idType.getFieldValue(),
                    idNumber.getFieldValue());

            if (problem.isPresent()) {

                invalid.add(problem.get());

                if (identityNumberField != null) {
                    openMissingField(
                            quotationRequest,
                            identityNumberField);
                }
            }
        }

        // If information is still missing or invalid, request only those fields.
        if (!missing.isEmpty() || !invalid.isEmpty()) {

            quotationRequest.setStatus(
                    RequestStatus.MISSING_INFORMATION);

            quotationRequestRepository.save(quotationRequest);

            return new ValidationResult(
                    false,
                    missing,
                    invalid);
        }

        // All required information is valid.
        for (MissingField open :
                missingFieldRepository
                        .findByQuotationRequestIdAndStatus(
                                quotationRequest.getId(),
                                "OPEN")) {

            close(open);
        }

        quotationRequest.setStatus(
                RequestStatus.READY_FOR_QUOTATION);

        quotationRequestRepository.save(quotationRequest);

        return new ValidationResult(
                true,
                List.of(),
                List.of());
    }

    // ---------------------------------------------------------
    // VALUE VALIDATION
    // ---------------------------------------------------------

    private boolean isValidValue(
            String value,
            FieldDataType dataType) {

        if (isBlank(value)) {
            return false;
        }

        String v = value.trim();

        if (dataType == null) {
            return true;
        }

        return switch (dataType) {

            case TEXT -> true;

            case EMAIL ->
                    v.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

            case PHONE ->
                    v.matches("^\\d{10,15}$");

            case NUMBER ->
                    v.matches("^\\d+(\\.\\d+)?$");

            case DATE -> {
                try {
                    LocalDate.parse(v);
                    yield true;
                } catch (DateTimeParseException e) {
                    yield false;
                }
            }
        };
    }

    private boolean matchesRegex(
            String value,
            String regex) {

        if (regex == null || regex.isBlank()) {
            return true;
        }

        try {
            return Pattern.compile(regex)
                    .matcher(value.trim())
                    .matches();

        } catch (PatternSyntaxException e) {

            return true;
        }
    }

    // ---------------------------------------------------------
    // SAVE FIELD VALUES
    // ---------------------------------------------------------

    private void saveValue(
            QuotationRequest request,
            RequiredField requiredField,
            String value,
            ExtractedFieldData extracted) {

        // Never store the raw government identity number here.
        String stored =
                "identitynumber".equals(
                        normalize(requiredField.getFieldName()))
                        ? IdentityProtectionService.mask(
                                lastFour(value))
                        : value.trim();

        RequestFieldValue row =
                requestFieldValueRepository
                        .findByQuotationRequestId(request.getId())
                        .stream()
                        .filter(field ->
                                field.getRequiredField() != null
                                        && requiredField.getId().equals(
                                                field.getRequiredField().getId()))
                        .findFirst()
                        .orElseGet(RequestFieldValue::new);

        LocalDateTime now = LocalDateTime.now();

        row.setQuotationRequest(request);
        row.setRequiredField(requiredField);
        row.setFieldValue(stored);
        row.setSource(extracted.getSource());

        if (row.getCreatedAt() == null) {
            row.setCreatedAt(now);
        }

        row.setUpdatedAt(now);

        requestFieldValueRepository.save(row);
    }

    // ---------------------------------------------------------
    // MISSING FIELD MANAGEMENT
    // ---------------------------------------------------------

    private void openMissingField(
            QuotationRequest request,
            RequiredField requiredField) {

        boolean alreadyOpen =
                !missingFieldRepository
                        .findByQuotationRequestIdAndRequiredFieldIdAndStatus(
                                request.getId(),
                                requiredField.getId(),
                                "OPEN")
                        .isEmpty();

        if (alreadyOpen) {
            return;
        }

        MissingField missingField = new MissingField();

        missingField.setQuotationRequest(request);
        missingField.setRequiredField(requiredField);
        missingField.setStatus("OPEN");

        missingFieldRepository.save(missingField);
    }

    private void resolveMissingField(
            QuotationRequest request,
            RequiredField requiredField) {

        for (MissingField open :
                missingFieldRepository
                        .findByQuotationRequestIdAndRequiredFieldIdAndStatus(
                                request.getId(),
                                requiredField.getId(),
                                "OPEN")) {

            close(open);
        }
    }

    private void close(MissingField missingField) {

        missingField.setStatus("RESOLVED");
        missingField.setResolvedAt(LocalDateTime.now());

        missingFieldRepository.save(missingField);
    }

    // ---------------------------------------------------------
    // HELPER METHODS
    // ---------------------------------------------------------

    private static String lastFour(String value) {

        String normalized =
                IdentityProtectionService.normalize(value);

        return normalized.length() <= 4
                ? normalized
                : normalized.substring(normalized.length() - 4);
    }

    private boolean isBlank(String value) {

        return value == null || value.trim().isEmpty();
    }

    /**
     * Uses the same normalization rule as AutomationService.
     * Examples:
     * "Origin Country" -> "origincountry"
     * "originCountry"  -> "origincountry"
     * "Travel Start Date" -> "travelstartdate"
     */
    private String normalize(String value) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    // ---------------------------------------------------------
    // VALIDATION RESULT
    // ---------------------------------------------------------

    public static class ValidationResult {

        private final boolean valid;
        private final List<String> missingFields;
        private final List<String> invalidFields;

        public ValidationResult(
                boolean valid,
                List<String> missingFields,
                List<String> invalidFields) {

            this.valid = valid;
            this.missingFields = missingFields;
            this.invalidFields = invalidFields;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getMissingFields() {
            return missingFields;
        }

        public List<String> getInvalidFields() {
            return invalidFields;
        }
    }
}
