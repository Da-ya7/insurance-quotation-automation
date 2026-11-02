package com.insurance.quotation.service.master;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insurance.quotation.service.client.IdentityProtectionService;

/**
 * Reads and writes the configurable master data (countries, insurance types,
 * identity documents, subject keywords) so nothing has to be hard-coded.
 */
@Service
public class MasterDataService {

    private final JdbcTemplate jdbc;

    public MasterDataService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------- countries ----------------

    public List<CountryDef> countries(boolean activeOnly) {
        return jdbc.query(
                "SELECT code, name, active FROM countries"
                        + (activeOnly ? " WHERE active = TRUE" : "") + " ORDER BY name",
                (rs, i) -> new CountryDef(rs.getString("code"), rs.getString("name"),
                        rs.getBoolean("active")));
    }

    @Transactional
    public void saveCountry(CountryDef c) {
        String code = c.code().trim().toUpperCase(Locale.ROOT);
        int updated = jdbc.update(
                "UPDATE countries SET name = ?, active = ? WHERE code = ?",
                c.name(), c.active(), code);
        if (updated == 0) {
            jdbc.update("INSERT INTO countries (code, name, active) VALUES (?, ?, ?)",
                    code, c.name(), c.active());
        }
    }

    /** Accepts a code ("IN") or a name ("India"); returns the code or null. */
    public String resolveCountryCode(String codeOrName) {
        if (codeOrName == null || codeOrName.isBlank()) {
            return null;
        }
        String v = codeOrName.trim();
        List<String> found = jdbc.query(
                "SELECT code FROM countries WHERE UPPER(code) = UPPER(?) OR UPPER(name) = UPPER(?) LIMIT 1",
                (rs, i) -> rs.getString(1), v, v);
        return found.isEmpty() ? null : found.get(0);
    }

    // ---------------- insurance types ----------------

    public List<InsuranceTypeDef> insuranceTypes(boolean activeOnly) {
        return jdbc.query(
                "SELECT code, label, keywords, base_premium, rate_percent, rate_basis_field, active, sort_order "
                        + "FROM insurance_types" + (activeOnly ? " WHERE active = TRUE" : "")
                        + " ORDER BY sort_order, code",
                (rs, i) -> new InsuranceTypeDef(
                        rs.getString("code"), rs.getString("label"), rs.getString("keywords"),
                        rs.getBigDecimal("base_premium"), rs.getBigDecimal("rate_percent"),
                        rs.getString("rate_basis_field"), rs.getBoolean("active"),
                        rs.getInt("sort_order")));
    }

    public Optional<InsuranceTypeDef> insuranceType(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return insuranceTypes(false).stream()
                .filter(t -> t.code().equalsIgnoreCase(code.trim())).findFirst();
    }

    private volatile java.util.Map<String, String> labelCache = java.util.Map.of();
    private volatile long labelCacheAt = 0;

    /** Label for a type code; cached for 30 s because every list page asks for it. */
    public String insuranceTypeLabel(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        if (System.currentTimeMillis() - labelCacheAt > 30_000) {
            java.util.Map<String, String> fresh = new java.util.HashMap<>();
            insuranceTypes(false).forEach(t -> fresh.put(t.code().toUpperCase(Locale.ROOT), t.label()));
            labelCache = fresh;
            labelCacheAt = System.currentTimeMillis();
        }
        return labelCache.getOrDefault(code.trim().toUpperCase(Locale.ROOT), code);
    }

    @Transactional
    public void saveInsuranceType(InsuranceTypeDef t) {
        labelCacheAt = 0;
        String code = t.code().trim().toUpperCase(Locale.ROOT);
        int updated = jdbc.update(
                "UPDATE insurance_types SET label=?, keywords=?, base_premium=?, rate_percent=?, "
                        + "rate_basis_field=?, active=?, sort_order=? WHERE code=?",
                t.label(), t.keywords(), nz(t.basePremium()), nz(t.ratePercent()),
                blankToNull(t.rateBasisField()), t.active(), t.sortOrder(), code);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO insurance_types (code, label, keywords, base_premium, rate_percent, "
                            + "rate_basis_field, active, sort_order) VALUES (?,?,?,?,?,?,?,?)",
                    code, t.label(), t.keywords(), nz(t.basePremium()), nz(t.ratePercent()),
                    blankToNull(t.rateBasisField()), t.active(), t.sortOrder());
        }
    }

    // ---------------- identity documents ----------------

    public List<IdentityTypeDef> allIdentityTypes() {
        return identityTypesForCountry(null, false);
    }

    public List<IdentityTypeDef> identityTypesForCountry(String countryCodeOrNull, boolean activeOnly) {
        String sql = "SELECT id, country_code, code, label, validation_regex, example, active "
                + "FROM identity_document_types";
        String where = " WHERE 1=1" + (activeOnly ? " AND active = TRUE" : "");
        if (countryCodeOrNull == null || countryCodeOrNull.isBlank()) {
            return jdbc.query(sql + where + " ORDER BY country_code, code", this::mapIdentity);
        }
        String cc = countryCodeOrNull.trim().toUpperCase(Locale.ROOT);
        List<IdentityTypeDef> own = jdbc.query(
                sql + where + " AND country_code = ? ORDER BY code", this::mapIdentity, cc);
        if (!own.isEmpty()) {
            return own;
        }
        // Countries without their own list use the '*' fallback types.
        return jdbc.query(sql + where + " AND country_code = '*' ORDER BY code",
                this::mapIdentity);
    }

    private IdentityTypeDef mapIdentity(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new IdentityTypeDef(rs.getLong("id"), rs.getString("country_code"),
                rs.getString("code"), rs.getString("label"), rs.getString("validation_regex"),
                rs.getString("example"), rs.getBoolean("active"));
    }

    @Transactional
    public IdentityTypeDef saveIdentityType(IdentityTypeDef t) {
        Long id = t.id();
        checkRegex(t.validationRegex());
        String cc = t.countryCode().trim().toUpperCase(Locale.ROOT);
        String code = t.code().trim().toUpperCase(Locale.ROOT);
        if (id != null) {
            jdbc.update("UPDATE identity_document_types SET country_code=?, code=?, label=?, "
                            + "validation_regex=?, example=?, active=? WHERE id=?",
                    cc, code, t.label(), t.validationRegex(), t.example(), t.active(), id);
            return new IdentityTypeDef(id, cc, code, t.label(), t.validationRegex(), t.example(), t.active());
        }
        jdbc.update("INSERT INTO identity_document_types (country_code, code, label, validation_regex, "
                        + "example, active) VALUES (?,?,?,?,?,?)",
                cc, code, t.label(), t.validationRegex(), t.example(), t.active());
        Long newId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return new IdentityTypeDef(newId, cc, code, t.label(), t.validationRegex(), t.example(), t.active());
    }

    /**
     * Checks a government ID against the rules configured for its country.
     * Returns an error message, or empty when the number is acceptable.
     */
    public Optional<String> validateIdentity(String country, String type, String number) {
        if (type == null || type.isBlank() || number == null || number.isBlank()) {
            return Optional.empty();
        }
        String cc = resolveCountryCode(country);
        String t = type.trim().toUpperCase(Locale.ROOT);

        List<IdentityTypeDef> candidates = cc != null
                ? identityTypesForCountry(cc, true)
                : identityTypesForCountry(null, true).stream()
                        .filter(x -> x.code().equals(t)).toList();

        Optional<IdentityTypeDef> rule = candidates.stream()
                .filter(x -> x.code().equals(t)).findFirst();

        if (rule.isEmpty()) {
            // Not configured for this country: fall back to the generic rule if one exists.
            rule = identityTypesForCountry("*", true).stream()
                    .filter(x -> x.code().equals(t)).findFirst();
        }
        if (rule.isEmpty()) {
            return Optional.of("Identity type " + t + " is not accepted"
                    + (cc != null ? " for " + cc : ""));
        }
        String normalized = IdentityProtectionService.normalize(number);
        if (!matches(rule.get().validationRegex(), normalized)) {
            return Optional.of(rule.get().label() + " is not valid"
                    + (rule.get().example() != null ? " (example " + rule.get().example() + ")" : ""));
        }
        return Optional.empty();
    }

    /** Throws IllegalArgumentException when the pattern cannot be compiled. */
    public static void checkRegex(String regex) {
        if (regex == null || regex.isBlank()) {
            return;
        }
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid validation pattern: " + e.getDescription());
        }
    }

    /** Upper-case code used for identity types, e.g. "aadhaar " -> "AADHAAR". */
    public static String typeCode(String type) {
        return type == null ? null : type.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    static boolean matches(String regex, String value) {
        try {
            return Pattern.compile(regex).matcher(value).matches();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    // ---------------- subject keywords ----------------

    public List<KeywordDef> keywords() {
        return jdbc.query("SELECT id, keyword, active FROM email_subject_keywords ORDER BY keyword",
                (rs, i) -> new KeywordDef(rs.getLong(1), rs.getString(2), rs.getBoolean(3)));
    }

    public List<String> activeSubjectKeywords() {
        return jdbc.query("SELECT keyword FROM email_subject_keywords WHERE active = TRUE",
                (rs, i) -> rs.getString(1));
    }

    @Transactional
    public void addKeyword(String keyword) {
        String k = keyword.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty() || k.length() > 80) {
            throw new IllegalArgumentException("Keyword must be 1-80 characters.");
        }
        jdbc.update("INSERT IGNORE INTO email_subject_keywords (keyword) VALUES (?)", k);
    }

    @Transactional
    public void setKeywordActive(long id, boolean active) {
        jdbc.update("UPDATE email_subject_keywords SET active=? WHERE id=?", active, id);
    }

    @Transactional
    public void deleteKeyword(long id) {
        jdbc.update("DELETE FROM email_subject_keywords WHERE id=?", id);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
