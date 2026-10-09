package com.insurance.quotation.service.validation;

import java.time.LocalDateTime;
import com.insurance.quotation.entity.MissingField;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.RequiredField;
import com.insurance.quotation.entity.RequestFieldValue;
import com.insurance.quotation.entity.enums.FieldDataType;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.repository.MissingFieldRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.repository.RequiredFieldRepository;
import com.insurance.quotation.repository.RequestFieldValueRepository;
import com.insurance.quotation.service.extraction.ExtractedFieldData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ValidationService {

    private final RequiredFieldRepository requiredFieldRepository;
    private final RequestFieldValueRepository requestFieldValueRepository;
    private final MissingFieldRepository missingFieldRepository;
    private final QuotationRequestRepository quotationRequestRepository;

    public ValidationService(
            RequiredFieldRepository requiredFieldRepository,
            RequestFieldValueRepository requestFieldValueRepository,
            MissingFieldRepository missingFieldRepository,
            QuotationRequestRepository quotationRequestRepository) {

        this.requiredFieldRepository = requiredFieldRepository;
        this.requestFieldValueRepository = requestFieldValueRepository;
        this.missingFieldRepository = missingFieldRepository;
        this.quotationRequestRepository = quotationRequestRepository;
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

        List<RequiredField> requiredFields =
                requiredFieldRepository.findAll();

        List<RequiredField> applicableFields = requiredFields.stream()
                .filter(field -> Boolean.TRUE.equals(field.getIsActive()))
                .filter(field -> isApplicableToPolicy(
                        field,
                        quotationRequest.getPolicyType()))
                .toList();

        if (applicableFields.isEmpty()) {
            throw new IllegalStateException(
                    "No active required fields are configured for policy type: "
                            + quotationRequest.getPolicyType());
        }

        Map<String, ExtractedFieldData> extractedFieldMap = new HashMap<>();

        for (ExtractedFieldData field : extractedFields) {
            if (field.getFieldName() != null) {
                extractedFieldMap.put(
                        normalize(field.getFieldName()),
                        field
                );
            }
        }

        List<String> missingFields = new ArrayList<>();
        List<String> invalidFields = new ArrayList<>();
        Set<String> validFieldNames = new HashSet<>();

        for (RequiredField requiredField : applicableFields) {

            String fieldName = normalize(requiredField.getFieldName());

            ExtractedFieldData extracted =
                    extractedFieldMap.get(fieldName);

            if (extracted == null
                    || extracted.isMissing()
                    || isBlank(extracted.getFieldValue())) {

                missingFields.add(requiredField.getDisplayLabel());
                createMissingFieldIfNeeded(
                        quotationRequest,
                        requiredField
                );
                continue;
            }

            if (!isValidValue(
                    extracted.getFieldValue(),
                    requiredField.getDataType())) {

                invalidFields.add(requiredField.getDisplayLabel());
                createMissingFieldIfNeeded(
                        quotationRequest,
                        requiredField
                );
                continue;
            }

            saveRequestFieldValue(
                    quotationRequest,
                    requiredField,
                    extracted
            );

            resolveExistingMissingField(
                    quotationRequest,
                    requiredField
            );

            validFieldNames.add(fieldName);
        }

        if (!missingFields.isEmpty() || !invalidFields.isEmpty()) {

            quotationRequest.setStatus(
                    RequestStatus.MISSING_INFORMATION
            );

            quotationRequestRepository.save(quotationRequest);

            return new ValidationResult(
                    false,
                    missingFields,
                    invalidFields
            );
        }

        quotationRequest.setStatus(
                RequestStatus.READY_FOR_QUOTATION
        );

        quotationRequestRepository.save(quotationRequest);

        return new ValidationResult(
                true,
                List.of(),
                List.of()
        );
    }

    private boolean isApplicableToPolicy(
            RequiredField field,
            String policyType) {

        if (field.getPolicyType() == null
                || field.getPolicyType().isBlank()) {
            return true;
        }

        if (policyType == null || policyType.isBlank()) {
            return false;
        }

        return field.getPolicyType()
                .trim()
                .equalsIgnoreCase(policyType.trim());
    }

    private boolean isValidValue(
            String value,
            FieldDataType dataType) {

        if (isBlank(value)) {
            return false;
        }

        String trimmed = value.trim();

        if (dataType == null) {
            return true;
        }

        return switch (dataType) {

            case TEXT ->
                    !trimmed.isBlank();

            case EMAIL ->
                    trimmed.matches(
                            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
                    );

            case PHONE ->
                    trimmed.matches("^\\d{10,15}$");

            case NUMBER ->
                    trimmed.matches("^\\d+(\\.\\d+)?$");

            case DATE -> {
                try {
                    LocalDate.parse(trimmed);
                    yield true;
                } catch (DateTimeParseException e) {
                    yield false;
                }
            }
        };
    }

    private void saveRequestFieldValue(
            QuotationRequest quotationRequest,
            RequiredField requiredField,
            ExtractedFieldData extracted) {

        List<RequestFieldValue> existingValues =
                requestFieldValueRepository
                        .findByQuotationRequestId(
                                quotationRequest.getId()
                        );

        RequestFieldValue requestFieldValue = existingValues.stream()
                .filter(value ->
                        value.getRequiredField() != null
                                && requiredField.getId()
                                .equals(value.getRequiredField().getId()))
                .findFirst()
                .orElseGet(RequestFieldValue::new);

        requestFieldValue.setQuotationRequest(quotationRequest);
        requestFieldValue.setRequiredField(requiredField);
        requestFieldValue.setFieldValue(
                extracted.getFieldValue().trim()
        );
        requestFieldValue.setSource(extracted.getSource());

        LocalDateTime now = LocalDateTime.now();

        if (requestFieldValue.getCreatedAt() == null) {
            requestFieldValue.setCreatedAt(now);
        }

        requestFieldValue.setUpdatedAt(now);

        requestFieldValueRepository.save(requestFieldValue);
    }

    private void createMissingFieldIfNeeded(
            QuotationRequest quotationRequest,
            RequiredField requiredField) {

        List<MissingField> existingMissingFields =
                missingFieldRepository.findAll();

        boolean alreadyOpen = existingMissingFields.stream()
                .anyMatch(missingField ->
                        missingField.getQuotationRequest() != null
                                && quotationRequest.getId()
                                .equals(
                                        missingField
                                                .getQuotationRequest()
                                                .getId()
                                )
                                && missingField.getRequiredField() != null
                                && requiredField.getId()
                                .equals(
                                        missingField
                                                .getRequiredField()
                                                .getId()
                                )
                                && "OPEN".equalsIgnoreCase(
                                        missingField.getStatus()
                                )
                );

        if (alreadyOpen) {
            return;
        }

        MissingField missingField = new MissingField();

        missingField.setQuotationRequest(quotationRequest);
        missingField.setRequiredField(requiredField);
        missingField.setStatus("OPEN");

        missingFieldRepository.save(missingField);
    }

    private void resolveExistingMissingField(
            QuotationRequest quotationRequest,
            RequiredField requiredField) {

        List<MissingField> existingMissingFields =
                missingFieldRepository.findAll();

        for (MissingField missingField : existingMissingFields) {

            boolean sameRequest =
                    missingField.getQuotationRequest() != null
                            && quotationRequest.getId()
                            .equals(
                                    missingField
                                            .getQuotationRequest()
                                            .getId()
                            );

            boolean sameField =
                    missingField.getRequiredField() != null
                            && requiredField.getId()
                            .equals(
                                    missingField
                                            .getRequiredField()
                                            .getId()
                            );

            boolean open =
                    "OPEN".equalsIgnoreCase(
                            missingField.getStatus()
                    );

            if (sameRequest && sameField && open) {
                missingField.setStatus("RESOLVED");
                missingField.setResolvedAt(
                        java.time.LocalDateTime.now()
                );
                missingFieldRepository.save(missingField);
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase();
    }

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