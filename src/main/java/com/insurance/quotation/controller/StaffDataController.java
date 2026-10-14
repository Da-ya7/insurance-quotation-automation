package com.insurance.quotation.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
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
import org.springframework.web.bind.annotation.RestController;

import com.insurance.quotation.entity.IncomingEmail;
import com.insurance.quotation.entity.MissingField;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.entity.QuotationItem;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.IncomingEmailRepository;
import com.insurance.quotation.repository.MissingFieldRepository;
import com.insurance.quotation.repository.QuotationItemRepository;
import com.insurance.quotation.repository.QuotationRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;

/** Read-only data for the staff screens (all require a staff login). */
@RestController
@RequestMapping("/api")
public class StaffDataController {

    private final IncomingEmailRepository emailRepository;
    private final MissingFieldRepository missingFieldRepository;
    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository itemRepository;
    private final QuotationRequestRepository requestRepository;
    private final ClientRepository clientRepository;

    public StaffDataController(
            IncomingEmailRepository emailRepository,
            MissingFieldRepository missingFieldRepository,
            QuotationRepository quotationRepository,
            QuotationItemRepository itemRepository,
            QuotationRequestRepository requestRepository,
            ClientRepository clientRepository) {
        this.emailRepository = emailRepository;
        this.missingFieldRepository = missingFieldRepository;
        this.quotationRepository = quotationRepository;
        this.itemRepository = itemRepository;
        this.requestRepository = requestRepository;
        this.clientRepository = clientRepository;
    }

    // ---------- Incoming emails ----------
    @GetMapping("/emails")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> emails() {
        return emailRepository.findAllByOrderByReceivedAtDesc().stream()
                .map(this::email).toList();
    }

    // ---------- Missing information ----------
    @GetMapping("/missing-information")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> missingInformation() {
        return missingFieldRepository.findByStatusOrderByCreatedAtDesc("OPEN").stream()
                .map(this::missing).toList();
    }

    // ---------- Quotations ----------
    @GetMapping("/quotations")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> quotations() {
        return quotationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::quotation).toList();
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

    // ---------- Dashboard & reports ----------
    @GetMapping("/dashboard/stats")
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        long missing = requestRepository.findByStatus(RequestStatus.MISSING_INFORMATION).size()
                + requestRepository.findByStatus(RequestStatus.WAITING_FOR_CLIENT).size();

        long sent = requestRepository.findByStatus(RequestStatus.QUOTATION_SENT).size();

        long failed = 0;
        for (RequestStatus s : List.of(
                RequestStatus.EMAIL_PROCESSING_FAILED,
                RequestStatus.DATA_EXTRACTION_FAILED,
                RequestStatus.QUOTATION_GENERATION_FAILED,
                RequestStatus.EMAIL_SEND_FAILED,
                RequestStatus.MANUAL_REVIEW_REQUIRED)) {
            failed += requestRepository.findByStatus(s).size();
        }

        Map<String, Object> m = new HashMap<>();
        m.put("totalEmails", emailRepository.count());
        m.put("quotationRequests", requestRepository.count());
        m.put("clients", clientRepository.count());
        m.put("pendingDetails", missing);
        m.put("quotationsGenerated", quotationRepository.count());
        m.put("quotationsSent", sent);
        m.put("needsAttention", failed);
        return m;
    }

    @GetMapping("/reports/summary")
    @Transactional(readOnly = true)
    public Map<String, Object> report() {
        Map<RequestStatus, Long> byStatus = new EnumMap<>(RequestStatus.class);
        requestRepository.findAll().forEach(r -> byStatus.merge(r.getStatus(), 1L, Long::sum));

        Map<String, Long> statusCounts = new HashMap<>();
        byStatus.forEach((k, v) -> statusCounts.put(k.name(), v));

        java.math.BigDecimal totalValue = quotationRepository.findAll().stream()
                .map(Quotation::getTotalAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        Map<String, Object> m = new HashMap<>();
        m.put("requestsByStatus", statusCounts);
        m.put("totalQuotationValue", totalValue);
        m.put("generatedAt", LocalDateTime.now());
        return m;
    }

    // ---------- mappers ----------
    private Map<String, Object> email(IncomingEmail e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId());
        m.put("senderEmail", e.getSenderEmail());
        m.put("subject", e.getSubject());
        m.put("body", e.getBody());
        m.put("status", e.getProcessingStatus());
        m.put("receivedAt", e.getReceivedAt());
        m.put("requestId", e.getQuotationRequest() != null
                ? e.getQuotationRequest().getId() : null);
        m.put("requestStatus", e.getQuotationRequest() != null
                ? e.getQuotationRequest().getStatus() : null);
        return m;
    }

    private Map<String, Object> missing(MissingField f) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", f.getId());
        m.put("requestId", f.getQuotationRequest().getId());
        m.put("clientName", f.getQuotationRequest().getClient().getName());
        m.put("clientEmail", f.getQuotationRequest().getClient().getEmail());
        m.put("field", f.getRequiredField().getDisplayLabel());
        m.put("status", f.getStatus());
        m.put("createdAt", f.getCreatedAt());
        return m;
    }

    private Map<String, Object> quotation(Quotation q) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", q.getId());
        m.put("quotationNumber", q.getQuotationNumber());
        m.put("requestId", q.getQuotationRequest().getId());
        m.put("clientName", q.getQuotationRequest().getClient().getName());
        m.put("clientEmail", q.getQuotationRequest().getClient().getEmail());
        m.put("premium", q.getPremiumAmount());
        m.put("tax", q.getTaxAmount());
        m.put("total", q.getTotalAmount());
        m.put("currency", q.getCurrency());
        m.put("status", q.getStatus());
        m.put("hasPdf", q.getPdfPath() != null);
        m.put("createdAt", q.getCreatedAt());
        m.put("sentAt", q.getSentAt());
        return m;
    }
}
