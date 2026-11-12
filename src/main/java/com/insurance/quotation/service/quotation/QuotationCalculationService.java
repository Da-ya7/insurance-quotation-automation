
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
import com.insurance.quotation.service.master.InsuranceTypeDef;
import com.insurance.quotation.service.master.MasterDataService;

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
    private final MasterDataService masterData;

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
            MasterDataService masterData,
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
        this.masterData = masterData;
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
                fields.put(
                        v.getRequiredField().getFieldName(),
                        v.getFieldValue());
            }
        }

        String type = request.getPolicyType();

        if (type != null
                && !type.isBlank()
                && !"TRAVEL".equalsIgnoreCase(type)) {
            return calculateGeneric(type, fields);
        }

        LocalDate start;
        LocalDate end;

        try {
            start = LocalDate.parse(required(fields, "travelStartDate"));
            end = LocalDate.parse(required(fields, "travelEndDate"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Travel dates must use YYYY-MM-DD format.");
        }

        if (end.isBefore(start)) {
            throw new IllegalArgumentException(
                    "Travel end date cannot be before travel start date.");
        }

        long days = ChronoUnit.DAYS.between(start, end) + 1;

        List<LineItem> items = new ArrayList<>();
        BigDecimal premium = BigDecimal.ZERO;

        List<QuotationRequestTraveller> savedTravellers = travellerRepository
                .findByQuotationRequestIdOrderByTravellerNumber(request.getId());

        // Read the total group count saved from the public form.
        // For older requests without this field, use the saved traveller count.
        int travellerCount = getTravellerCount(fields, savedTravellers.size());

        if (savedTravellers.isEmpty()) {

            // Email-originated request: only a head-count is known.
            BigDecimal perTravellerPremium = money(
                    dailyRate
                            .multiply(adultMultiplier)
                            .multiply(BigDecimal.valueOf(days)));

            BigDecimal groupPremium = money(
                    perTravellerPremium
                            .multiply(BigDecimal.valueOf(travellerCount)));

            items.add(new LineItem(
                    travellerCount + " traveller(s) x "
                            + days + " day(s)",
                    groupPremium));

            premium = premium.add(groupPremium);

        } else if (savedTravellers.size() == 1) {

            /*
             * Public form request:
             * Only the main applicant's details are collected.
             * Use the applicant's age band and medical condition
             * as the pricing basis for the entire group.
             */

            QuotationRequestTraveller applicant = savedTravellers.get(0);

            int age = applicant.getAgeAtSubmission();

            BigDecimal multiplier = age <= childMaxAge
                    ? childMultiplier
                    : age >= seniorMinAge
                            ? seniorMultiplier
                            : adultMultiplier;

            String band = age <= childMaxAge
                    ? "child"
                    : age >= seniorMinAge
                            ? "senior"
                            : "adult";

            BigDecimal perTravellerBase = money(
                    dailyRate
                            .multiply(multiplier)
                            .multiply(BigDecimal.valueOf(days)));

            BigDecimal groupBase = money(
                    perTravellerBase
                            .multiply(BigDecimal.valueOf(travellerCount)));

            items.add(new LineItem(
                    travellerCount + " traveller(s) x "
                            + days + " day(s) - "
                            + band + " rate",
                    groupBase));

            premium = premium.add(groupBase);

            // Apply the applicant's medical loading to every group member.
            String medical = applicant.getPreExistingMedicalConditions();

            if (medical != null && !medical.isBlank()) {

                BigDecimal perTravellerLoading = money(
                        perTravellerBase.multiply(medicalLoading));

                BigDecimal groupLoading = money(
                        perTravellerLoading
                                .multiply(BigDecimal.valueOf(travellerCount)));

                items.add(new LineItem(
                        "Medical loading for "
                                + travellerCount + " traveller(s)",
                        groupLoading));

                premium = premium.add(groupLoading);
            }

        } else {

            /*
             * Existing requests containing individual details
             * for multiple travellers retain per-person pricing.
             */

            for (QuotationRequestTraveller traveller : savedTravellers) {

                int age = traveller.getAgeAtSubmission();

                BigDecimal multiplier = age <= childMaxAge
                        ? childMultiplier
                        : age >= seniorMinAge
                                ? seniorMultiplier
                                : adultMultiplier;

                String band = age <= childMaxAge
                        ? "child"
                        : age >= seniorMinAge
                                ? "senior"
                                : "adult";

                BigDecimal base = money(
                        dailyRate
                                .multiply(multiplier)
                                .multiply(BigDecimal.valueOf(days)));

                items.add(new LineItem(
                        trim("Traveller "
                                + traveller.getTravellerNumber()
                                + " - "
                                + traveller.getName()
                                + " (" + band + ", "
                                + days + " days)"),
                        base));

                premium = premium.add(base);

                String medical =
                        traveller.getPreExistingMedicalConditions();

                if (medical != null && !medical.isBlank()) {

                    BigDecimal loading = money(
                            base.multiply(medicalLoading));

                    items.add(new LineItem(
                            trim("Medical loading - Traveller "
                                    + traveller.getTravellerNumber()),
                            loading));

                    premium = premium.add(loading);
                }
            }
        }

        // International travel surcharge.
        String origin = required(fields, "originCountry");
        String destination = required(fields, "destinationCountry");

        if (!origin.equalsIgnoreCase(destination)) {

            BigDecimal surcharge = money(
                    premium.multiply(internationalSurcharge));

            items.add(new LineItem(
                    "International travel surcharge",
                    surcharge));

            premium = premium.add(surcharge);
        }

        // Final premium and tax.
        premium = money(premium);

        BigDecimal tax = money(
                premium.multiply(taxRate));

        BigDecimal total = money(
                premium.add(tax));

        return new CalculationResult(
                items,
                premium,
                tax,
                total);
    }

    /**
     * Gets the submitted group size.
     * Valid range: 1 to 20.
     */
    private int getTravellerCount(
            Map<String, String> fields,
            int savedTravellerCount) {

        String value = fields.get("travellers");

        // Backward compatibility for older requests.
        if (value == null || value.isBlank()) {
            return Math.max(1, savedTravellerCount);
        }

        int count;

        try {
            count = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Number of travellers must be a valid whole number.");
        }

        if (count < 1 || count > 20) {
            throw new IllegalArgumentException(
                    "Number of travellers must be between 1 and 20.");
        }

        return count;
    }

    /**
     * Non-travel types: premium = base premium + rate% of one value field
     * (all three come from the insurance_types master data).
     * DEMO pricing until the company supplies real rating tables.
     */
    private CalculationResult calculateGeneric(
            String type,
            Map<String, String> fields) {

        InsuranceTypeDef def = masterData.insuranceType(type)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown insurance type: " + type));

        BigDecimal basis = BigDecimal.ZERO;

        if (def.rateBasisField() != null
                && !def.rateBasisField().isBlank()) {

            try {
                basis = new BigDecimal(
                        required(fields, def.rateBasisField()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "The value for "
                                + def.rateBasisField()
                                + " must be a number.");
            }
        }

        return genericPremium(
                def.label(),
                def.basePremium(),
                def.ratePercent(),
                def.rateBasisField(),
                basis,
                taxRate);
    }

    /** Pure calculation, public for unit tests. */
    public static CalculationResult genericPremium(
            String label,
            BigDecimal base,
            BigDecimal ratePercent,
            String basisField,
            BigDecimal basis,
            BigDecimal taxRate) {

        List<LineItem> items = new ArrayList<>();
        BigDecimal premium = BigDecimal.ZERO;

        BigDecimal basePremium = base.setScale(
                2, RoundingMode.HALF_UP);

        items.add(new LineItem(
                "Base premium - " + label,
                basePremium));

        premium = premium.add(basePremium);

        if (ratePercent.signum() > 0 && basis.signum() > 0) {

            BigDecimal charge = basis.multiply(ratePercent)
                    .divide(
                            BigDecimal.valueOf(100),
                            2,
                            RoundingMode.HALF_UP);

            items.add(new LineItem(
                    ratePercent.stripTrailingZeros().toPlainString()
                            + "% of " + basisField,
                    charge));

            premium = premium.add(charge);
        }

        premium = premium.setScale(
                2, RoundingMode.HALF_UP);

        BigDecimal tax = premium.multiply(taxRate)
                .setScale(2, RoundingMode.HALF_UP);

        return new CalculationResult(
                items,
                premium,
                tax,
                premium.add(tax).setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trim(String value) {
        return value.length() > 190
                ? value.substring(0, 190)
                : value;
    }

    private String required(
            Map<String, String> fields,
            String name) {

        String value = fields.get(name);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Required quotation field is missing: " + name);
        }

        return value.trim();
    }
}
