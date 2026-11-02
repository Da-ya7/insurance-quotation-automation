package com.insurance.quotation.service.master;

import java.math.BigDecimal;

/** One supported insurance type (row of insurance_types). */
public record InsuranceTypeDef(
        String code,
        String label,
        String keywords,
        BigDecimal basePremium,
        BigDecimal ratePercent,
        String rateBasisField,
        boolean active,
        int sortOrder) {
}
