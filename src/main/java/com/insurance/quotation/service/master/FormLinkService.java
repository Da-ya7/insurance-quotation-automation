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

/**
 * Personal form links ("https://.../form/HEALTH?token=..."). The token ties the
 * form to a client and request so a returning client sees saved details.
 */
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
            return expiresAt.isBefore(LocalDateTime.now());
        }
    }

    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    private final String travelFormUrl;
    private final String baseUrl;
    private final int validDays;

    public FormLinkService(
            JdbcTemplate jdbc,
            @Value("${app.public-form-url:http://localhost:4200/client-quotation}") String travelFormUrl,
            @Value("${app.public-base-url:http://localhost:4200}") String baseUrl,
            @Value("${app.form-link-days:7}") int validDays) {
        this.jdbc = jdbc;
        this.travelFormUrl = travelFormUrl;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.validDays = validDays;
    }

    /** Reuses a still-valid link for the same client/request so the same person gets one link. */
    @Transactional
    public FormLink create(Long clientId, Long requestId, String insuranceType) {

        List<FormLink> existing = jdbc.query(
                "SELECT * FROM form_links WHERE client_id <=> ? AND quotation_request_id <=> ? "
                        + "AND insurance_type <=> ? AND used_at IS NULL AND expires_at > ? "
                        + "ORDER BY id DESC LIMIT 1",
                FormLinkService::map, clientId, requestId, insuranceType, LocalDateTime.now());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);

        jdbc.update("INSERT INTO form_links (token,client_id,quotation_request_id,insurance_type,expires_at) "
                        + "VALUES (?,?,?,?,?)",
                token, clientId, requestId, insuranceType, LocalDateTime.now().plusDays(validDays));
        return find(token).orElseThrow();
    }

    public Optional<FormLink> find(String token) {
        if (token == null || !token.matches("^[0-9a-f]{64}$")) {
            return Optional.empty();
        }
        return jdbc.query("SELECT * FROM form_links WHERE token = ?", FormLinkService::map, token)
                .stream().findFirst();
    }

    @Transactional
    public void markUsed(long id) {
        jdbc.update("UPDATE form_links SET used_at = ? WHERE id = ?", LocalDateTime.now(), id);
    }

    @Transactional
    public void attachRequest(long id, long requestId) {
        jdbc.update("UPDATE form_links SET quotation_request_id = ? WHERE id = ?", requestId, id);
    }

    public String url(FormLink link) {
        String type = link.insuranceType();
        if (type == null || type.isBlank()) {
            return baseUrl + "/form?token=" + link.token();
        }
        if ("TRAVEL".equalsIgnoreCase(type)) {
            return travelFormUrl + "?token=" + link.token();
        }
        return baseUrl + "/form/" + type.toUpperCase() + "?token=" + link.token();
    }

    private static FormLink map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new FormLink(
                rs.getLong("id"), rs.getString("token"),
                (Long) rs.getObject("client_id"),
                (Long) rs.getObject("quotation_request_id"),
                rs.getString("insurance_type"),
                rs.getTimestamp("expires_at").toLocalDateTime(),
                rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toLocalDateTime());
    }
}
