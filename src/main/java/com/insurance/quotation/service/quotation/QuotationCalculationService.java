
package com.insurance.quotation.service.quotation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.RequestFieldValue;
import com.insurance.quotation.repository.RequestFieldValueRepository;

@Service
public class QuotationCalculationService {

    // Demo rates only. Confirm the actual pricing rules before use.
    private static final BigDecimal DAILY_RATE_PER_TRAVELLER =
            new BigDecimal("100.00");

    private static final BigDecimal DESTINATION_SURCHARGE_RATE =
            new BigDecimal("0.25");

    private static final BigDecimal TAX_RATE = BigDecimal.ZERO;

    private final RequestFieldValueRepository requestFieldValueRepository;

    public QuotationCalculationService(
            RequestFieldValueRepository requestFieldValueRepository) {
        this.requestFieldValueRepository = requestFieldValueRepository;
    }

    public record LineItem(
            String description,
            BigDecimal amount) {
    }

    public record CalculationResult(
            List<LineItem> items,
            BigDecimal premium,
            BigDecimal tax,
            BigDecimal total) {
    }

    public CalculationResult calculate(
            QuotationRequest quotationRequest) {

        if (quotationRequest == null || quotationRequest.getId() == null) {
            throw new IllegalArgumentException(
                    "Quotation request must be saved before calculating.");
        }

        // Read the saved field values directly from MySQL.
        // Do not depend on a possibly stale entity collection.
        List<RequestFieldValue> savedValues =
                requestFieldValueRepository.findByQuotationRequestId(
                        quotationRequest.getId());

        Map<String, String> fields = new HashMap<>();

        for (RequestFieldValue fieldValue : savedValues) {
            if (fieldValue.getRequiredField() != null) {
                fields.put(
                        fieldValue.getRequiredField()
                                .getFieldName()
                                .trim()
                                .toLowerCase(),
                        fieldValue.getFieldValue());
            }
        }

        LocalDate startDate;
        LocalDate endDate;

        try {
            startDate = LocalDate.parse(
                    getRequiredValue(fields, "travelStartDate"));

            endDate = LocalDate.parse(
                    getRequiredValue(fields, "travelEndDate"));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Travel dates must use YYYY-MM-DD format.");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "Travel end date cannot be before travel start date.");
        }

        int travellers;

        try {
            travellers = Integer.parseInt(
                    getRequiredValue(fields, "travellers"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Number of travellers must be a valid whole number.");
        }

        if (travellers <= 0) {
            throw new IllegalArgumentException(
                    "Number of travellers must be at least 1.");
        }

        String originCountry =
                getRequiredValue(fields, "originCountry");

        String destinationCountry =
                getRequiredValue(fields, "destinationCountry");

        long travelDays =
                ChronoUnit.DAYS.between(startDate, endDate) + 1;

        BigDecimal basePremium = DAILY_RATE_PER_TRAVELLER
                .multiply(BigDecimal.valueOf(travelDays))
                .multiply(BigDecimal.valueOf(travellers))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal surcharge = BigDecimal.ZERO;

        if (!originCountry.trim().equalsIgnoreCase(
                destinationCountry.trim())) {
            surcharge = basePremium
                    .multiply(DESTINATION_SURCHARGE_RATE)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        List<LineItem> items = List.of(
                new LineItem("Base Premium", basePremium),
                new LineItem("Destination Surcharge", surcharge));

        BigDecimal premium = basePremium
                .add(surcharge)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal tax = premium
                .multiply(TAX_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = premium
                .add(tax)
                .setScale(2, RoundingMode.HALF_UP);

        return new CalculationResult(items, premium, tax, total);
    }

    private String getRequiredValue(
            Map<String, String> fields,
            String fieldName) {

        String value = fields.get(fieldName.toLowerCase());

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Required quotation field is missing: " + fieldName);
        }

        return value.trim();
    }
}
