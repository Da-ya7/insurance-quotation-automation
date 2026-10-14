package com.insurance.quotation.service.quotation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.QuotationRequestTraveller;
import com.insurance.quotation.entity.RequestFieldValue;
import com.insurance.quotation.repository.QuotationRequestTravellerRepository;
import com.insurance.quotation.repository.RequestFieldValueRepository;

/**
 * Per-traveller pricing. ALL rates live in application.properties
 * (pricing.*) so they can be changed without touching code.
 * The defaults are DEMO values until the company confirms real rates.
 */
@Service
public class QuotationCalculationService {

    public record LineItem(String description, BigDecimal amount) {
    }

    public record CalculationResult(
            List<LineItem> items,
            BigDecimal premium,
            BigDecimal tax,
            BigDecimal total) {
    }

    private final RequestFieldValueRepository fieldValueRepository;
    private final QuotationRequestTravellerRepository travellerRepository;

    private final BigDecimal dailyRate;
    private final BigDecimal childMultiplier;
    private final BigDecimal adultMultiplier;
    private final BigDecimal seniorMultiplier;
    private final int childMaxAge;
    private final int seniorMinAge;
    private final BigDecimal medicalLoading;
    private final BigDecimal internationalSurcharge;
    private final BigDecimal taxRate;

    public QuotationCalculationService(
            RequestFieldValueRepository fieldValueRepository,
            QuotationRequestTravellerRepository travellerRepository,
            @Value("${pricing.daily-rate:100.00}") BigDecimal dailyRate,
            @Value("${pricing.child-multiplier:0.50}") BigDecimal childMultiplier,
            @Value("${pricing.adult-multiplier:1.00}") BigDecimal adultMultiplier,
            @Value("${pricing.senior-multiplier:1.50}") BigDecimal seniorMultiplier,
            @Value("${pricing.child-max-age:17}") int childMaxAge,
            @Value("${pricing.senior-min-age:60}") int seniorMinAge,
            @Value("${pricing.medical-loading:0.20}") BigDecimal medicalLoading,
            @Value("${pricing.international-surcharge:0.25}") BigDecimal internationalSurcharge,
            @Value("${pricing.tax-rate:0.00}") BigDecimal taxRate) {

        this.fieldValueRepository = fieldValueRepository;
        this.travellerRepository = travellerRepository;
        this.dailyRate = dailyRate;
        this.childMultiplier = childMultiplier;
        this.adultMultiplier = adultMultiplier;
        this.seniorMultiplier = seniorMultiplier;
        this.childMaxAge = childMaxAge;
        this.seniorMinAge = seniorMinAge;
        this.medicalLoading = medicalLoading;
        this.internationalSurcharge = internationalSurcharge;
        this.taxRate = taxRate;
    }

    public CalculationResult calculate(QuotationRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("Quotation request cannot be null.");
        }

        // Read saved values from the database (not from lazy collections).
        Map<String, String> fields = new HashMap<>();
        for (RequestFieldValue v :
                fieldValueRepository.findByQuotationRequestId(request.getId())) {
            if (v.getRequiredField() != null) {
                fields.put(v.getRequiredField().getFieldName(), v.getFieldValue());
            }
        }

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(required(fields, "travelStartDate"));
            end = LocalDate.parse(required(fields, "travelEndDate"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Travel dates must use YYYY-MM-DD format.");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException(
                    "Travel end date cannot be before travel start date.");
        }

        long days = ChronoUnit.DAYS.between(start, end) + 1;

        List<LineItem> items = new ArrayList<>();
        BigDecimal premium = BigDecimal.ZERO;

        List<QuotationRequestTraveller> travellers = travellerRepository
                .findByQuotationRequestIdOrderByTravellerNumber(request.getId());

        if (travellers.isEmpty()) {
            // Email-originated request: only a head-count is known.
            int count;
            try {
                count = Integer.parseInt(required(fields, "travellers"));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Number of travellers must be a valid whole number.");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("Number of travellers must be at least 1.");
            }
            BigDecimal base = money(dailyRate
                    .multiply(adultMultiplier)
                    .multiply(BigDecimal.valueOf(days))
                    .multiply(BigDecimal.valueOf(count)));
            items.add(new LineItem(
                    count + " traveller(s) x " + days + " day(s)", base));
            premium = premium.add(base);
        } else {
            for (QuotationRequestTraveller t : travellers) {
                int age = t.getAgeAtSubmission();
                BigDecimal multiplier = age <= childMaxAge ? childMultiplier
                        : age >= seniorMinAge ? seniorMultiplier : adultMultiplier;
                String band = age <= childMaxAge ? "child"
                        : age >= seniorMinAge ? "senior" : "adult";

                BigDecimal base = money(dailyRate
                        .multiply(multiplier)
                        .multiply(BigDecimal.valueOf(days)));

                items.add(new LineItem(
                        trim("Traveller " + t.getTravellerNumber() + " - "
                                + t.getName() + " (" + band + ", " + days + " days)"),
                        base));
                premium = premium.add(base);

                String medical = t.getPreExistingMedicalConditions();
                if (medical != null && !medical.isBlank()) {
                    BigDecimal loading = money(base.multiply(medicalLoading));
                    items.add(new LineItem(
                            trim("Medical loading - Traveller " + t.getTravellerNumber()),
                            loading));
                    premium = premium.add(loading);
                }
            }
        }

        String origin = required(fields, "originCountry");
        String destination = required(fields, "destinationCountry");
        if (!origin.equalsIgnoreCase(destination)) {
            BigDecimal surcharge = money(premium.multiply(internationalSurcharge));
            items.add(new LineItem("International travel surcharge", surcharge));
            premium = premium.add(surcharge);
        }

        premium = money(premium);
        BigDecimal tax = money(premium.multiply(taxRate));
        BigDecimal total = money(premium.add(tax));

        return new CalculationResult(items, premium, tax, total);
    }

    private BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private String trim(String s) {
        return s.length() > 190 ? s.substring(0, 190) : s;
    }

    private String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Required quotation field is missing: " + name);
        }
        return value.trim();
    }
}
