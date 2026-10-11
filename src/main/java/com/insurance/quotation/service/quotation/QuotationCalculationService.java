
package com.insurance.quotation.service.quotation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.RequestFieldValue;

@Service
public class QuotationCalculationService {

    // DEMO RATE ONLY. Confirm actual pricing with your manager.
    private static final BigDecimal DAILY_RATE_PER_TRAVELLER =
            new BigDecimal("100.00");

    // Demo 25% surcharge for travel to a different country.
    private static final BigDecimal DESTINATION_SURCHARGE_RATE =
            new BigDecimal("0.25");

    // Tax remains zero until the applicable rate is confirmed.
    private static final BigDecimal TAX_RATE = BigDecimal.ZERO;

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

    public CalculationResult calculate(QuotationRequest quotationRequest) {

        if (quotationRequest == null) {
            throw new IllegalArgumentException(
                    "Quotation request cannot be null.");
        }

        // Read the actual field values saved for this request.
        Map<String, String> fields = new HashMap<>();

        if (quotationRequest.getFieldValues() != null) {
            for (RequestFieldValue fieldValue :
                    quotationRequest.getFieldValues()) {

                if (fieldValue.getRequiredField() != null) {
                    fields.put(
                            fieldValue.getRequiredField().getFieldName(),
                            fieldValue.getFieldValue()
                    );
                }
            }
        }

        String startDateValue =
                getRequiredValue(fields, "travelStartDate");

        String endDateValue =
                getRequiredValue(fields, "travelEndDate");

        String travellersValue =
                getRequiredValue(fields, "travellers");

        String originCountry =
                getRequiredValue(fields, "originCountry");

        String destinationCountry =
                getRequiredValue(fields, "destinationCountry");

        // Parse and validate travel dates.
        LocalDate startDate;
        LocalDate endDate;

        try {
            startDate = LocalDate.parse(startDateValue);
            endDate = LocalDate.parse(endDateValue);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Travel dates must use YYYY-MM-DD format.");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "Travel end date cannot be before travel start date.");
        }

        // Parse and validate traveller count.
        int travellers;

        try {
            travellers = Integer.parseInt(travellersValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Number of travellers must be a valid whole number.");
        }

        if (travellers <= 0) {
            throw new IllegalArgumentException(
                    "Number of travellers must be at least 1.");
        }

        // Count both the departure and return dates.
        long travelDays =
                ChronoUnit.DAYS.between(startDate, endDate) + 1;

        // Base premium = daily rate × days × travellers.
        BigDecimal basePremium = DAILY_RATE_PER_TRAVELLER
                .multiply(BigDecimal.valueOf(travelDays))
                .multiply(BigDecimal.valueOf(travellers))
                .setScale(2, RoundingMode.HALF_UP);

        // Apply the demo surcharge when the countries differ.
        BigDecimal destinationSurcharge = BigDecimal.ZERO;

        if (!originCountry.trim().equalsIgnoreCase(
                destinationCountry.trim())) {

            destinationSurcharge = basePremium
                    .multiply(DESTINATION_SURCHARGE_RATE)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        List<LineItem> items = List.of(
                new LineItem("Base Premium", basePremium),
                new LineItem(
                        "Destination Surcharge",
                        destinationSurcharge)
        );

        BigDecimal premium = basePremium
                .add(destinationSurcharge)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal tax = premium
                .multiply(TAX_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = premium
                .add(tax)
                .setScale(2, RoundingMode.HALF_UP);

        return new CalculationResult(
                items,
                premium,
                tax,
                total
        );
    }

    private String getRequiredValue(
            Map<String, String> fields,
            String fieldName) {

        String value = fields.get(fieldName);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Required quotation field is missing: "
                            + fieldName);
        }

        return value.trim();
    }
}
