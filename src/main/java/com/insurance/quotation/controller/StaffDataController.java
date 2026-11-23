package com.insurance.quotation.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.IncomingEmail;
import com.insurance.quotation.entity.MissingField;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.entity.QuotationItem;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.IncomingEmailRepository;
import com.insurance.quotation.repository.MissingFieldRepository;
import com.insurance.quotation.repository.QuotationItemRepository;
import com.insurance.quotation.repository.QuotationRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.service.master.MasterDataService;
import com.insurance.quotation.service.stats.RequestStatusInfo;
import com.insurance.quotation.service.stats.RequestStatusInfo.Group;

/**
 * Data for the staff screens. EVERY count shown on the dashboard and on the
 * individual pages is calculated here from the database, with one definition
 * of each status (see RequestStatusInfo), so the numbers always agree.
 */
@RestController
@RequestMapping("/api")
public class StaffDataController {

    private final IncomingEmailRepository emailRepository;
    private final MissingFieldRepository missingFieldRepository;
    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository itemRepository;
    private final QuotationRequestRepository requestRepository;
    private final ClientRepository clientRepository;
    private final MasterDataService masterData;
    
    public StaffDataController(
            IncomingEmailRepository emailRepository,
            MissingFieldRepository missingFieldRepository,
            QuotationRepository quotationRepository,
            QuotationItemRepository itemRepository,
            QuotationRequestRepository requestRepository,
            ClientRepository clientRepository,
            MasterDataService masterData) {
        this.emailRepository = emailRepository;
        this.missingFieldRepository = missingFieldRepository;
        this.quotationRepository = quotationRepository;
        this.itemRepository = itemRepository;
        this.requestRepository = requestRepository;
        this.clientRepository = clientRepository;
        this.masterData = masterData;
    }
    
    // ================= incoming emails =================
    
    @GetMapping("/emails")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> emails(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status) {

        return emailRepository.findAllByOrderByReceivedAtDesc().stream()
                .filter(e -> blank(kind) || kind.equalsIgnoreCase(e.getEmailKind()))
                .filter(e -> blank(status) || status.equalsIgnoreCase(e.getProcessingStatus()))
                .map(this::email).toList();
    }
    
    @GetMapping("/emails/summary")
    @Transactional(readOnly = true)
    public Map<String, Object> emailSummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", emailRepository.count());
        m.put("pending", emailRepository.countByProcessingStatus("RECEIVED"));
        m.put("processed", emailRepository.countByProcessingStatus("PROCESSED"));
        m.put("failed", emailRepository.countByProcessingStatus("FAILED"));
        m.put("ignored", emailRepository.countByProcessingStatus("IGNORED"));
        m.put("newRequests", emailRepository.countByEmailKind("NEW_REQUEST"));
        m.put("replies", emailRepository.countByEmailKind("CLIENT_REPLY"));
        m.put("unrelated", emailRepository.countByEmailKind("UNRELATED"));
        m.put("quotations", emailRepository.countEmailsWithQuotation());
        return m;
    }
    
    // ================= missing information =================

    @GetMapping("/missing-information")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> missingInformation(
            @RequestParam(defaultValue = "OPEN") String status) {

        List<MissingField> rows = "ALL".equalsIgnoreCase(status)
                ? missingFieldRepository.findAllByOrderByCreatedAtDesc()
                : missingFieldRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        return rows.stream().map(this::missing).toList();
    }

    @GetMapping("/missing-information/summary")
    @Transactional(readOnly = true)
    public Map<String, Object> missingSummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("open", missingFieldRepository.countByStatus("OPEN"));
        m.put("resolved", missingFieldRepository.countByStatus("RESOLVED"));
        m.put("requestsWaiting", requestRepository.countByStatusIn(
                RequestStatusInfo.statuses(Group.MISSING_DETAILS)));
        return m;
    }

    // ================= quotations =================

    @GetMapping("/quotations")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> quotations(@RequestParam(required = false) String status) {
        return quotationRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(q -> blank(status) || status.equalsIgnoreCase(q.getStatus()))
                .map(this::quotation).toList();
    }
    
    @GetMapping("/quotations/summary")
    @Transactional(readOnly = true)
    public Map<String, Object> quotationSummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", quotationRepository.count());
        m.put("sent", quotationRepository.countByStatus("SENT"));
        m.put("notSent", quotationRepository.countByStatus("DRAFT"));
        m.put("superseded", quotationRepository.countByStatus("SUPERSEDED"));
        return m;
    }
    
    @GetMapping("/quotations/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> quotation(@PathVariable Long id) {
        return quotationRepository.findById(id)
                .map(q -> {
                    Map<String, Object> m = quotation(q);
                    m.put("items", itemRepository.findByQuotation(q).stream()
                            .map((QuotationItem i) -> Map.of(
                                    "description", i.getDescription(),
                                    "amount", i.getAmount()))
                            .toList());
                    return ResponseEntity.ok(m);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    /** Every version of the quotation for the same request, newest first. */
    @GetMapping("/quotations/{id}/history")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> history(@PathVariable Long id) {
        Quotation q = quotationRepository.findById(id).orElse(null);
        if (q == null) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Quotation v : quotationRepository.findByQuotationRequestIdOrderByVersionNoDesc(
                q.getQuotationRequest().getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.getId());
            m.put("quotationNumber", v.getQuotationNumber());
            m.put("versionNo", v.getVersionNo());
            m.put("status", v.getStatus());
            m.put("total", v.getTotalAmount());
            m.put("clientVersionNo", v.getClientVersionNo());
            m.put("createdAt", v.getCreatedAt());
            m.put("sentAt", v.getSentAt());
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }
    
    @GetMapping("/quotations/{id}/pdf")
    public ResponseEntity<?> pdf(@PathVariable Long id) throws IOException {
        Quotation q = quotationRepository.findById(id).orElse(null);
        if (q == null || q.getPdfPath() == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = Paths.get(q.getPdfPath()).toAbsolutePath().normalize();
        Path root = Paths.get("generated", "quotations").toAbsolutePath().normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + q.getQuotationNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(Files.readAllBytes(path)));
    }

    // ================= dashboard & reports =================

    @GetMapping("/dashboard/stats")
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {

        long processing = requestRepository.countByStatusIn(RequestStatusInfo.statuses(Group.PROCESSING));
        long missing = requestRepository.countByStatusIn(RequestStatusInfo.statuses(Group.MISSING_DETAILS));
        long sent = requestRepository.countByStatusIn(RequestStatusInfo.statuses(Group.COMPLETED));
        long attention = requestRepository.countByStatusIn(RequestStatusInfo.statuses(Group.NEEDS_ATTENTION));

        long pendingEmails = emailRepository.countByProcessingStatus("RECEIVED");
        long failedEmails = emailRepository.countByProcessingStatus("FAILED");
        long quotationsTotal = quotationRepository.count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalEmails", emailRepository.count());
        m.put("newRequestEmails", emailRepository.countByEmailKind("NEW_REQUEST"));
        m.put("replyEmails", emailRepository.countByEmailKind("CLIENT_REPLY"));
        m.put("unrelatedEmails", emailRepository.countByEmailKind("UNRELATED"));
        m.put("failedEmails", failedEmails);
        m.put("pendingEmails", pendingEmails);
        m.put("processedEmails", emailRepository.countByProcessingStatus("PROCESSED"));

        m.put("quotationRequests", requestRepository.count());
        m.put("requestsProcessing", processing);
        m.put("requestsMissing", missing);
        m.put("requestsSent", sent);
        m.put("requestsNeedAttention", attention);

        m.put("clients", clientRepository.count());
        m.put("quotationsTotal", quotationsTotal);
        m.put("quotationsSent", quotationRepository.countByStatus("SENT"));
        m.put("quotationsNotSent", quotationRepository.countByStatus("DRAFT"));
        m.put("openMissingFields", missingFieldRepository.countByStatus("OPEN"));

        // graph: last 14 days, a row for every day (zero when nothing happened)
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate first = today.minusDays(13);
        Map<String, Long> reqPerDay = perDay(requestRepository.countCreatedPerDay(first.atStartOfDay()));
        Map<String, Long> quotPerDay = perDay(quotationRepository.countCreatedPerDay(first.atStartOfDay()));
        List<Map<String, Object>> daily = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            String d = first.plusDays(i).toString();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d);
            row.put("requests", reqPerDay.getOrDefault(d, 0L));
            row.put("quotations", quotPerDay.getOrDefault(d, 0L));
            daily.add(row);
        }
        m.put("dailyRequests", daily);

        List<Map<String, Object>> groups = new ArrayList<>();
        for (Group g : Group.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("group", g.name());
            row.put("label", g.label());
            row.put("count", requestRepository.countByStatusIn(RequestStatusInfo.statuses(g)));
            groups.add(row);
        }
        m.put("requestsByGroup", groups);

        // old keys, kept so nothing that still reads them breaks
        m.put("pendingDetails", missing);
        m.put("quotationsGenerated", quotationsTotal);
        m.put("needsAttention", attention);
        return m;
    }

    @GetMapping("/reports/summary")
    @Transactional(readOnly = true)
    public Map<String, Object> report() {
        Map<RequestStatus, Long> byStatus = new EnumMap<>(RequestStatus.class);
        requestRepository.findAll().forEach(r -> byStatus.merge(r.getStatus(), 1L, Long::sum));

        Map<String, Long> statusCounts = new HashMap<>();
        byStatus.forEach((k, v) -> statusCounts.put(k.name(), v));

        BigDecimal totalValue = quotationRepository.findAll().stream()
                .filter(q -> !"SUPERSEDED".equals(q.getStatus()))
                .map(Quotation::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> m = new HashMap<>();
        m.put("requestsByStatus", statusCounts);
        m.put("totalQuotationValue", totalValue);
        m.put("generatedAt", LocalDateTime.now());
        return m;
    }

    // ================= mappers =================

    private Map<String, Long> perDay(List<Object[]> rows) {
        Map<String, Long> out = new HashMap<>();
        for (Object[] r : rows) {
            out.put(String.valueOf(r[0]).substring(0, 10), ((Number) r[1]).longValue());
        }
        return out;
    }

    private Map<String, Object> email(IncomingEmail e) {
        Map<String, Object> m = new LinkedHashMap<>();
        QuotationRequest r = e.getQuotationRequest();
        m.put("id", e.getId());
        m.put("senderEmail", e.getSenderEmail());
        m.put("subject", e.getSubject());
        m.put("body", e.getBody());
        m.put("status", e.getProcessingStatus());
        m.put("statusLabel", RequestStatusInfo.emailStatusLabel(e.getProcessingStatus()));
        m.put("kind", e.getEmailKind());
        m.put("kindLabel", RequestStatusInfo.emailKindLabel(e.getEmailKind()));
        m.put("retryCount", e.getRetryCount());
        m.put("lastError", e.getLastError());
        m.put("receivedAt", e.getReceivedAt());
        m.put("requestId", r == null ? null : r.getId());
        m.put("requestReference", r == null ? null : RequestStatusInfo.reference(r.getId(), r.getCreatedAt()));
        m.put("requestStatus", r == null ? null : r.getStatus());
        m.put("requestStatusLabel", r == null ? null : RequestStatusInfo.label(r.getStatus()));
        Client c = r == null ? null : r.getClient();
        m.put("clientId", c == null ? null : c.getId());
        m.put("clientCode", c == null ? null : c.getClientCode());
        m.put("clientName", c == null ? null : c.getName());
        m.put("insuranceType", r == null ? null : masterData.insuranceTypeLabel(r.getPolicyType()));
        m.put("quotationNumber", r == null ? null
                : quotationRepository.findFirstByQuotationRequestIdOrderByVersionNoDesc(r.getId())
                        .map(Quotation::getQuotationNumber).orElse(null));
        return m;
    }
    
    private Map<String, Object> missing(MissingField f) {
        QuotationRequest r = f.getQuotationRequest();
        Client c = r.getClient();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("requestId", r.getId());
        m.put("requestReference", RequestStatusInfo.reference(r.getId(), r.getCreatedAt()));
        m.put("requestStatus", r.getStatus());
        m.put("requestStatusLabel", RequestStatusInfo.label(r.getStatus()));
        m.put("clientId", c.getId());
        m.put("clientCode", c.getClientCode());
        m.put("clientName", c.getName());
        m.put("clientEmail", c.getEmail());
        m.put("insuranceType", masterData.insuranceTypeLabel(r.getPolicyType()));
        m.put("field", f.getRequiredField().getDisplayLabel());
        m.put("fieldName", f.getRequiredField().getFieldName());
        m.put("status", f.getStatus());
        m.put("createdAt", f.getCreatedAt());
        m.put("resolvedAt", f.getResolvedAt());
        return m;
    }

    private Map<String, Object> quotation(Quotation q) {
        QuotationRequest r = q.getQuotationRequest();
        Client c = r.getClient();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.getId());
        m.put("quotationNumber", q.getQuotationNumber());
        m.put("versionNo", q.getVersionNo());
        m.put("requestId", r.getId());
        m.put("requestReference", RequestStatusInfo.reference(r.getId(), r.getCreatedAt()));
        m.put("clientId", c.getId());
        m.put("clientCode", c.getClientCode());
        m.put("clientName", c.getName());
        m.put("clientEmail", c.getEmail());
        m.put("insuranceType", masterData.insuranceTypeLabel(r.getPolicyType()));
        m.put("premium", q.getPremiumAmount());
        m.put("tax", q.getTaxAmount());
        m.put("total", q.getTotalAmount());
        m.put("currency", q.getCurrency());
        m.put("status", q.getStatus());
        m.put("statusLabel", RequestStatusInfo.quotationStatusLabel(q.getStatus()));
        m.put("sent", "SENT".equals(q.getStatus()));
        m.put("hasPdf", q.getPdfPath() != null);
        m.put("createdAt", q.getCreatedAt());
        m.put("sentAt", q.getSentAt());
        return m;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
