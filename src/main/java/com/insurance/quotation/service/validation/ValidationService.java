package com.insurance.quotation.service.validation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

/**
 * Checks a quotation request against the required fields configured in the
 * database for its insurance type (field list, mandatory flag, regex), and
 * the identity document against the rules of the client's country.
 */
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

    /** true when the field applies to this insurance type (NULL = all, or a comma list). */
    public static boolean appliesTo(RequiredField field, String policyType) {
        String configured = field.getPolicyType();
        if (configured == null || configured.isBlank()) {
            return true;
        }
        if (policyType == null || policyType.isBlank()) {
            return false;
        }
        for (String t : configured.split(",")) {
            if (t.trim().equalsIgnoreCase(policyType.trim())) {
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
            throw new IllegalArgumentException("Quotation request cannot be null");
        }
        if (extractedFields == null) {
            extractedFields = List.of();
        }

        quotationRequest.setStatus(RequestStatus.VALIDATING);

        List<RequiredField> applicable = requiredFieldRepository
                .findByIsActiveTrueOrderBySortOrderAscIdAsc().stream()
                .filter(f -> appliesTo(f, quotationRequest.getPolicyType()))
                .toList();

        if (applicable.isEmpty()) {
            throw new IllegalStateException(
                    "No active required fields are configured for insurance type: "
                            + quotationRequest.getPolicyType());
        }

        Map<String, ExtractedFieldData> byName = new HashMap<>();
        for (ExtractedFieldData f : extractedFields) {
            if (f.getFieldName() != null) {
                byName.put(normalize(f.getFieldName()), f);
            }
        }

        List<String> missing = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        RequiredField identityNumberField = null;

        for (RequiredField rf : applicable) {

            if ("identityNumber".equalsIgnoreCase(rf.getFieldName())) {
                identityNumberField = rf;
            }

            ExtractedFieldData extracted = byName.get(normalize(rf.getFieldName()));
            boolean absent = extracted == null || extracted.isMissing()
                    || isBlank(extracted.getFieldValue());

            if (absent) {
                if (Boolean.FALSE.equals(rf.getIsMandatory())) {
                    continue;                       // optional and not given
                }
                missing.add(rf.getDisplayLabel());
                openMissingField(quotationRequest, rf);
                continue;
            }

            String value = ValueNormalizer.normalize(extracted.getFieldValue(), rf.getDataType());

            if (!isValidValue(value, rf.getDataType()) || !matchesRegex(value, rf.getValidationRegex())) {
                invalid.add(rf.getDisplayLabel());
                openMissingField(quotationRequest, rf);
                continue;
            }

            saveValue(quotationRequest, rf, value, extracted);
            resolveMissingField(quotationRequest, rf);
        }

        // Identity document: country-specific rules from the master data.
        ExtractedFieldData idType = byName.get("identitytype");
        ExtractedFieldData idNumber = byName.get("identitynumber");
        if (idType != null && idNumber != null
                && !isBlank(idType.getFieldValue()) && !isBlank(idNumber.getFieldValue())) {

            ExtractedFieldData idCountry = byName.get("identitycountry");
            String country = idCountry != null && !isBlank(idCountry.getFieldValue())
                    ? idCountry.getFieldValue()
                    : quotationRequest.getClient() == null ? null
                    : quotationRequest.getClient().getIdentityCountry();

            var problem = masterData.validateIdentity(
                    country, idType.getFieldValue(), idNumber.getFieldValue());
            if (problem.isPresent()) {
                invalid.add(problem.get());
                if (identityNumberField != null) {
                    openMissingField(quotationRequest, identityNumberField);
                }
            }
        }

        if (!missing.isEmpty() || !invalid.isEmpty()) {
            quotationRequest.setStatus(RequestStatus.MISSING_INFORMATION);
            quotationRequestRepository.save(quotationRequest);
            return new ValidationResult(false, missing, invalid);
        }

        // everything is fine: close whatever was still open
        for (MissingField open : missingFieldRepository
                .findByQuotationRequestIdAndStatus(quotationRequest.getId(), "OPEN")) {
            close(open);
        }

        quotationRequest.setStatus(RequestStatus.READY_FOR_QUOTATION);
        quotationRequestRepository.save(quotationRequest);
        return new ValidationResult(true, List.of(), List.of());
    }

    // ------------------------------------------------------------------

    private boolean isValidValue(String value, FieldDataType dataType) {
        if (isBlank(value)) {
            return false;
        }
        String v = value.trim();
        if (dataType == null) {
            return true;
        }
        return switch (dataType) {
            case TEXT -> true;
            case EMAIL -> v.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
            case PHONE -> v.matches("^\\d{10,15}$");
            case NUMBER -> v.matches("^\\d+(\\.\\d+)?$");
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

    private boolean matchesRegex(String value, String regex) {
        if (regex == null || regex.isBlank()) {
            return true;
        }
        try {
            return Pattern.compile(regex).matcher(value.trim()).matches();
        } catch (PatternSyntaxException e) {
            return true;   // a broken pattern in the database must not block every customer
        }
    }

    private void saveValue(
            QuotationRequest request, RequiredField rf, String value, ExtractedFieldData extracted) {

        // Never keep the raw government ID in request data; the client master holds it encrypted.
        String stored = "identityNumber".equalsIgnoreCase(rf.getFieldName())
                ? IdentityProtectionService.mask(lastFour(value))
                : value.trim();

        RequestFieldValue row = requestFieldValueRepository
                .findByQuotationRequestId(request.getId()).stream()
                .filter(v -> v.getRequiredField() != null
                        && rf.getId().equals(v.getRequiredField().getId()))
                .findFirst()
                .orElseGet(RequestFieldValue::new);

        LocalDateTime now = LocalDateTime.now();
        row.setQuotationRequest(request);
        row.setRequiredField(rf);
        row.setFieldValue(stored);
        row.setSource(extracted.getSource());
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(now);
        }
        row.setUpdatedAt(now);
        requestFieldValueRepository.save(row);
    }

    private void openMissingField(QuotationRequest request, RequiredField rf) {
        boolean alreadyOpen = !missingFieldRepository
                .findByQuotationRequestIdAndRequiredFieldIdAndStatus(
                        request.getId(), rf.getId(), "OPEN").isEmpty();
        if (alreadyOpen) {
            return;
        }
        MissingField mf = new MissingField();
        mf.setQuotationRequest(request);
        mf.setRequiredField(rf);
        mf.setStatus("OPEN");
        missingFieldRepository.save(mf);
    }

    private void resolveMissingField(QuotationRequest request, RequiredField rf) {
        for (MissingField open : missingFieldRepository
                .findByQuotationRequestIdAndRequiredFieldIdAndStatus(
                        request.getId(), rf.getId(), "OPEN")) {
            close(open);
        }
    }

    private void close(MissingField mf) {
        mf.setStatus("RESOLVED");
        mf.setResolvedAt(LocalDateTime.now());
        missingFieldRepository.save(mf);
    }

    private static String lastFour(String v) {
        String n = IdentityProtectionService.normalize(v);
        return n.length() <= 4 ? n : n.substring(n.length() - 4);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public static class ValidationResult {

        private final boolean valid;
        private final List<String> missingFields;
        private final List<String> invalidFields;

        public ValidationResult(boolean valid, List<String> missingFields, List<String> invalidFields) {
            this.valid = valid;
            this.missingFields = missingFields;
            this.invalidFields = invalidFields;
        }

        public boolean isValid() { return valid; }
        public List<String> getMissingFields() { return missingFields; }
        public List<String> getInvalidFields() { return invalidFields; }
    }
}
