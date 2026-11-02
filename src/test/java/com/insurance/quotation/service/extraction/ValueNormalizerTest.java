package com.insurance.quotation.service.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.insurance.quotation.entity.enums.FieldDataType;

class ValueNormalizerTest {

    @Test
    void datesInCommonFormatsBecomeIso() {
        assertEquals("2026-11-12", ValueNormalizer.date("12/11/2026"));
        assertEquals("2026-11-12", ValueNormalizer.date("12-11-2026"));
        assertEquals("2026-11-12", ValueNormalizer.date("12 Nov 2026"));
        assertEquals("2026-11-12", ValueNormalizer.date("12th November 2026"));
        assertEquals("2026-11-12", ValueNormalizer.date("2026-11-12"));
    }

    @Test
    void unreadableDateIsReturnedTrimmedSoValidationRejectsIt() {
        assertEquals("next week", ValueNormalizer.date("  next week "));
    }

    @Test
    void phoneKeepsDigitsOnly() {
        assertEquals("919876543210", ValueNormalizer.phone("+91 98765-43210"));
        assertEquals("9876543210", ValueNormalizer.phone("(987) 654 3210"));
    }

    @Test
    void numbersLoseCurrencyAndSeparators() {
        assertEquals("500000", ValueNormalizer.number("Rs. 5,00,000"));
        assertEquals("1200.50", ValueNormalizer.number("$1,200.50"));
        assertEquals("abc", ValueNormalizer.number("abc"));
    }

    @Test
    void normalizeDispatchesByType() {
        assertEquals("a@b.com", ValueNormalizer.normalize(" A@B.com ", FieldDataType.EMAIL));
        assertEquals("2026-01-05", ValueNormalizer.normalize("05/01/2026", FieldDataType.DATE));
    }
}
