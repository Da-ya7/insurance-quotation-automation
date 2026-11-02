package com.insurance.quotation.service.extraction;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Locale;

import com.insurance.quotation.entity.enums.FieldDataType;

/**
 * Turns what people actually type into the format the system stores:
 * dates -> YYYY-MM-DD, phones -> digits only, numbers -> plain 1234.50.
 * Unrecognised input is returned trimmed so validation can reject it.
 */
public final class ValueNormalizer {

    // day-first formats come before month-first (Indian customers).
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            fmt("yyyy-MM-dd"), fmt("yyyy/MM/dd"),
            fmt("dd/MM/yyyy"), fmt("d/M/yyyy"),
            fmt("dd-MM-yyyy"), fmt("d-M-yyyy"),
            fmt("dd.MM.yyyy"), fmt("d.M.yyyy"),
            fmt("d MMM yyyy"), fmt("d MMMM yyyy"),
            fmt("d-MMM-yyyy"), fmt("MMM d, yyyy"), fmt("MMMM d, yyyy"));

    private ValueNormalizer() {
    }

    private static DateTimeFormatter fmt(String pattern) {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .toFormatter(Locale.ENGLISH);
    }

    public static String normalize(String raw, FieldDataType type) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty() || type == null) {
            return v;
        }
        return switch (type) {
            case DATE -> date(v);
            case PHONE -> phone(v);
            case NUMBER -> number(v);
            case EMAIL -> v.toLowerCase(Locale.ROOT);
            case TEXT -> v;
        };
    }

    public static String date(String raw) {
        String v = raw.trim().replaceAll("(?<=\\d)(st|nd|rd|th)\\b", "");
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(v, f).toString();
            } catch (RuntimeException ignored) {
                // try the next format
            }
        }
        return raw.trim();
    }

    public static String phone(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        return digits;
    }

    public static String number(String raw) {
        String cleaned = raw.replaceAll("(?i)(inr|rs\\.?|usd|\\$|₹|,|\\s)", "");
        return cleaned.matches("^\\d+(\\.\\d+)?$") ? cleaned : raw.trim();
    }
}
