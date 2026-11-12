
package com.insurance.quotation.service.master;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormLinkService {

    public record FormLink(
            long id,
            String token,
            Long clientId,
            Long requestId,
            String insuranceType,
            LocalDateTime expiresAt,
            LocalDateTime usedAt) {

        public boolean isExpired() {
            return expiresAt == null
                    || expiresAt.isBefore(LocalDateTime.now());
        }
    }

    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    private final String publicFormUrl;
    private final int validDays;

    public FormLinkService(
            JdbcTemplate jdbc,
            @Value("${app.public-form-url:http://localhost:4200/form}")
            String publicFormUrl,
            @Value("${app.form-link-days:7}")
            int validDays) {

        if (publicFormUrl == null || publicFormUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Public form URL cannot be empty.");
        }

        this.jdbc = jdbc;

        this.publicFormUrl = publicFormUrl.endsWith("/")
                ? publicFormUrl.substring(0, publicFormUrl.length() - 1)
                : publicFormUrl;

        this.validDays = validDays;
    }

    /**
     * Returns the general Angular quotation form URL.
     * Example: http://localhost:4200/form
     */
    public String publicUrl() {
        return publicFormUrl;
    }

    /**
     * Always returns the general form URL.
     * Does not append a token.
     */
    public String url(FormLink link) {
        return publicFormUrl;
    }

    /**
     * Retains database-backed token creation for other existing workflows.
     */
    @Transactional
    public FormLink create(
            Long clientId,
            Long requestId,
            String insuranceType) {

        List<FormLink> existing = jdbc.query(
                "SELECT * FROM form_links "
                        + "WHERE client_id <=> ? "
                        + "AND quotation_request_id <=> ? "
                        + "AND insurance_type <=> ? "
                        + "AND used_at IS NULL "
                        + "AND expires_at > ? "
                        + "ORDER BY id DESC LIMIT 1",
                FormLinkService::map,
                clientId,
                requestId,
                insuranceType,
                LocalDateTime.now());

        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);

        String token = HexFormat.of().formatHex(bytes);

        jdbc.update(
                "INSERT INTO form_links "
                        + "(token, client_id, quotation_request_id, "
                        + "insurance_type, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                token,
                clientId,
                requestId,
                insuranceType,
                LocalDateTime.now().plusDays(validDays));

        return find(token).orElseThrow();
    }

    /**
     * Retains token lookup for existing controller/service callers.
     */
    public Optional<FormLink> find(String token) {

        if (token == null
                || !token.matches("^[0-9a-f]{64}$")) {
            return Optional.empty();
        }

        return jdbc.query(
                "SELECT * FROM form_links WHERE token = ?",
                FormLinkService::map,
                token)
                .stream()
                .findFirst();
    }

    @Transactional
    public void markUsed(long id) {

        jdbc.update(
                "UPDATE form_links SET used_at = ? WHERE id = ?",
                LocalDateTime.now(),
                id);
    }

    @Transactional
    public void attachRequest(long id, long requestId) {

        jdbc.update(
                "UPDATE form_links SET quotation_request_id = ? WHERE id = ?",
                requestId,
                id);
    }

    private static FormLink map(
            java.sql.ResultSet rs,
            int n) throws java.sql.SQLException {

        java.sql.Timestamp expiresTimestamp =
                rs.getTimestamp("expires_at");

        java.sql.Timestamp usedTimestamp =
                rs.getTimestamp("used_at");

        return new FormLink(
                rs.getLong("id"),
                rs.getString("token"),
                (Long) rs.getObject("client_id"),
                (Long) rs.getObject("quotation_request_id"),
                rs.getString("insurance_type"),
                expiresTimestamp == null
                        ? null
                        : expiresTimestamp.toLocalDateTime(),
                usedTimestamp == null
                        ? null
                        : usedTimestamp.toLocalDateTime());
    }
}
