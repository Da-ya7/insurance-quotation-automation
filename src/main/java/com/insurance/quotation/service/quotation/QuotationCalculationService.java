package com.insurance.quotation.service.quotation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class QuotationCalculationService {

    // Demo pricing values from manager task
    private static final BigDecimal BASE_PREMIUM = new BigDecimal("4000.00");
    private static final BigDecimal TRAVELLER_FACTOR = new BigDecimal("500.00");
    private static final BigDecimal DESTINATION_FACTOR = new BigDecimal("500.00");

    // Tax rate is not finalized yet
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

    public CalculationResult calculate() {

        List<LineItem> items = List.of(
                new LineItem("Base Premium", BASE_PREMIUM),
                new LineItem("Traveller Factor", TRAVELLER_FACTOR),
                new LineItem("Destination Factor", DESTINATION_FACTOR)
        );

        BigDecimal premium = items.stream()
                .map(LineItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tax = premium
                .multiply(TAX_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = premium.add(tax);

        return new CalculationResult(
                items,
                premium,
                tax,
                total
        );
    }
}
