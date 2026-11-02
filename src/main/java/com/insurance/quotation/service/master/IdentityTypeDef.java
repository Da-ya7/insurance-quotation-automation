package com.insurance.quotation.service.master;

/** An identity document accepted for a country ('*' = fallback for other countries). */
public record IdentityTypeDef(
        Long id,
        String countryCode,
        String code,
        String label,
        String validationRegex,
        String example,
        boolean active) {
}
