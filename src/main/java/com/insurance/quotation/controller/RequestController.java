
package com.insurance.quotation.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.QuotationRequestTraveller;
import com.insurance.quotation.entity.RequestFieldValue;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.repository.IncomingEmailRepository;
import com.insurance.quotation.repository.MissingFieldRepository;
import com.insurance.quotation.repository.QuotationRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.repository.QuotationRequestTravellerRepository;
import com.insurance.quotation.repository.RequestFieldValueRepository;
import com.insurance.quotation.service.master.MasterDataService;
import com.insurance.quotation.service.stats.RequestStatusInfo;
import com.insurance.quotation.service.stats.RequestStatusInfo.Group;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final QuotationRequestRepository requestRepository;
    private final QuotationRequestTravellerRepository travellerRepository;
    private final RequestFieldValueRepository fieldValueRepository;
    private final QuotationRepository quotationRepository;
    private final MissingFieldRepository missingFieldRepository;
    private final IncomingEmailRepository emailRepository;
    private final MasterDataService masterData;

    public RequestController(
            QuotationRequestRepository requestRepository,
            QuotationRequestTravellerRepository travellerRepository,
            RequestFieldValueRepository fieldValueRepository,
            QuotationRepository quotationRepository,
            MissingFieldRepository missingFieldRepository,
            IncomingEmailRepository emailRepository,
            MasterDataService masterData) {

        this.missingFieldRepository = missingFieldRepository;
        this.emailRepository = emailRepository;
        this.masterData = masterData;
        this.requestRepository = requestRepository;
        this.travellerRepository = travellerRepository;
        this.fieldValueRepository = fieldValueRepository;
        this.quotationRepository = quotationRepository;
    }

    // =========================================================
    // GET ALL REQUESTS
    // =========================================================

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String group) {

        List<QuotationRequest> requests;

        if (group != null && !group.isBlank()) {

            try {

                requests = requestRepository.findByStatusIn(
                        RequestStatusInfo.statuses(
                                Group.valueOf(group.trim().toUpperCase())));

                requests = new java.util.ArrayList<>(requests);
                requests.sort((a, b) -> Long.compare(b.getId(), a.getId()));

            } catch (IllegalArgumentException e) {

                return ResponseEntity.badRequest()
                        .body("Invalid request group: " + group);
            }

        } else if (status != null && !status.isBlank()) {

            try {

                requests = requestRepository.findByStatus(
                        RequestStatus.valueOf(status.trim().toUpperCase()));

            } catch (IllegalArgumentException e) {

                return ResponseEntity.badRequest()
                        .body("Invalid request status: " + status);
            }

        } else {

            requests = requestRepository.findAllByOrderByCreatedAtDesc();
        }

        return ResponseEntity.ok(
                requests.stream().map(this::row).toList());
    }

    // =========================================================
    // REQUEST SUMMARY
    // =========================================================

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public Map<String, Object> summary() {

        Map<String, Object> m = new HashMap<>();

        // Existing request status counts - unchanged
        m.put("total", requestRepository.count());

        m.put("processing",
                requestRepository.countByStatusIn(
                        RequestStatusInfo.statuses(Group.PROCESSING)));

        m.put("missingDetails",
                requestRepository.countByStatusIn(
                        RequestStatusInfo.statuses(Group.MISSING_DETAILS)));

        m.put("sent",
                requestRepository.countByStatusIn(
                        RequestStatusInfo.statuses(Group.COMPLETED)));

        m.put("needsAttention",
                requestRepository.countByStatusIn(
                        RequestStatusInfo.statuses(Group.NEEDS_ATTENTION)));

        // =====================================================
        // CLIENT QUOTATION RESPONSE COUNTS
        // =====================================================

        long accepted = 0;
        long rejected = 0;
        long pending = 0;
        long closed = 0;

        List<QuotationRequest> allRequests = requestRepository.findAll();

        for (QuotationRequest request : allRequests) {

            // Only the latest quotation version is considered
            Quotation latestQuotation = quotationRepository
                    .findFirstByQuotationRequestIdOrderByVersionNoDesc(
                            request.getId())
                    .orElse(null);

            if (latestQuotation == null) {
                continue;
            }

            // Ignore quotations that have not actually been sent
            if (!isEligibleForClientResponse(latestQuotation)) {
                continue;
            }

            String responseStatus = latestQuotation.getResponseStatus();

            // Customer response takes priority over closure tracking
            if ("ACCEPTED".equalsIgnoreCase(responseStatus)) {

                accepted++;

            } else if ("REJECTED".equalsIgnoreCase(responseStatus)) {

                rejected++;

            } else if ("PENDING".equalsIgnoreCase(responseStatus)
                    && latestQuotation.getClosedAt() != null
                    && "NO_RESPONSE".equalsIgnoreCase(
                            latestQuotation.getClosureReason())) {

                // Automatically closed after no response
                closed++;

            } else if ("PENDING".equalsIgnoreCase(responseStatus)
                    && latestQuotation.getClosedAt() == null) {

                // Includes both before and after reminder
                pending++;
            }
        }

        m.put("accepted", accepted);
        m.put("rejected", rejected);
        m.put("pending", pending);
        m.put("closed", closed);

        return m;
    }

    // =========================================================
    // CHECK WHETHER QUOTATION WAS ACTUALLY SENT
    // =========================================================

    private boolean isEligibleForClientResponse(Quotation quotation) {

        if (quotation == null) {
            return false;
        }

        return "SENT".equalsIgnoreCase(quotation.getStatus())
                && quotation.getSentAt() != null
                && quotation.getPdfPath() != null
                && !quotation.getPdfPath().isBlank();
    }

    // =========================================================
    // GET REQUEST DETAILS
    // =========================================================

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getRequestById(@PathVariable Long id) {

        QuotationRequest request =
                requestRepository.findById(id).orElse(null);

        if (request == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> body = new HashMap<>(row(request));

        body.put("purposeOfTravel", request.getPurposeOfTravel());
        body.put("clientPhone", request.getClient().getPhone());

        // Traveller details
        List<Map<String, Object>> travellers = travellerRepository
                .findByQuotationRequestIdOrderByTravellerNumber(id)
                .stream()
                .map(this::traveller)
                .toList();

        body.put("travellerDetails", travellers);

        // Submitted field values
        Map<String, String> fields = new HashMap<>();

        for (RequestFieldValue v :
                fieldValueRepository.findByQuotationRequestId(id)) {

            fields.put(
                    v.getRequiredField().getFieldName(),
                    v.getFieldValue());
        }

        body.put("fields", fields);

        // Latest quotation details
        quotationRepository
                .findFirstByQuotationRequestIdOrderByVersionNoDesc(id)
                .ifPresent(q -> {

                    body.put("quotationId", q.getId());
                    body.put("quotationNumber", q.getQuotationNumber());
                    body.put("totalAmount", q.getTotalAmount());
                    body.put("quotationStatus", q.getStatus());
                    body.put("quotationSentAt", q.getSentAt());
                    body.put("quotationPdfAvailable",
                            q.getPdfPath() != null
                                    && !q.getPdfPath().isBlank());
                });

        // =====================================================
        // EMAIL HISTORY
        // =====================================================

        List<Map<String, Object>> emails = new java.util.ArrayList<>();

        for (IncomingEmail e :
                emailRepository.findByQuotationRequestIdOrderByReceivedAtDesc(id)) {

            Map<String, Object> em = new HashMap<>();

            em.put("id", e.getId());
            em.put("subject", e.getSubject());
            em.put("kind", e.getEmailKind());
            em.put("kindLabel",
                    RequestStatusInfo.emailKindLabel(e.getEmailKind()));
            em.put("receivedAt", e.getReceivedAt());
            em.put("status", e.getProcessingStatus());

            emails.add(em);
        }

        body.put("emails", emails);

        // =====================================================
        // QUOTATION HISTORY
        // =====================================================

        List<Map<String, Object>> quotations =
                new java.util.ArrayList<>();

        for (Quotation q :
                quotationRepository
                        .findByQuotationRequestIdOrderByVersionNoDesc(id)) {

            Map<String, Object> qm = new HashMap<>();

            qm.put("id", q.getId());
            qm.put("quotationNumber", q.getQuotationNumber());
            qm.put("versionNo", q.getVersionNo());
            qm.put("status", q.getStatus());
            qm.put("total", q.getTotalAmount());
            qm.put("createdAt", q.getCreatedAt());
            qm.put("sentAt", q.getSentAt());

            // Client response tracking
            qm.put("responseStatus", q.getResponseStatus());
            qm.put("responseAt", q.getResponseAt());
            qm.put("reminderCount", q.getReminderCount());
            qm.put("reminderSentAt", q.getReminderSentAt());
            qm.put("closedAt", q.getClosedAt());
            qm.put("closureReason", q.getClosureReason());

            quotations.add(qm);
        }

        body.put("quotations", quotations);

        // =====================================================
        // MISSING INFORMATION
        // =====================================================

        List<Map<String, Object>> missing =
                new java.util.ArrayList<>();

        for (MissingField f :
                missingFieldRepository
                        .findByQuotationRequestIdOrderByCreatedAtDesc(id)) {

            Map<String, Object> mm = new HashMap<>();

            mm.put("field",
                    f.getRequiredField().getDisplayLabel());

            mm.put("fieldName",
                    f.getRequiredField().getFieldName());

            mm.put("status", f.getStatus());

            missing.add(mm);
        }

        body.put("missing", missing);

        return ResponseEntity.ok(body);
    }

    // =========================================================
    // REQUEST LIST ROW
    // =========================================================

    private Map<String, Object> row(QuotationRequest r) {

        Map<String, String> fields = new HashMap<>();

        for (RequestFieldValue v :
                fieldValueRepository.findByQuotationRequestId(r.getId())) {

            fields.put(
                    v.getRequiredField().getFieldName(),
                    v.getFieldValue());
        }

        Map<String, Object> m = new HashMap<>();

        m.put("id", r.getId());

        m.put("reference",
                RequestStatusInfo.reference(
                        r.getId(),
                        r.getCreatedAt()));

        Client client = r.getClient();

        m.put("clientId", client.getId());
        m.put("clientCode", client.getClientCode());
        m.put("clientName", client.getName());
        m.put("clientEmail", client.getEmail());

        m.put("policyType", r.getPolicyType());

        m.put("insuranceType",
                masterData.insuranceTypeLabel(r.getPolicyType()));

        m.put("purposeOfTravel", r.getPurposeOfTravel());

        m.put("destination", fields.get("destinationCountry"));
        m.put("origin", fields.get("originCountry"));

        m.put("travelStartDate", fields.get("travelStartDate"));
        m.put("travelEndDate", fields.get("travelEndDate"));

        long count =
                travellerRepository.countByQuotationRequestId(r.getId());

        m.put("travellers",
                count > 0 ? count : parse(fields.get("travellers")));

        // Request status
        m.put("status", r.getStatus());

        m.put("statusLabel",
                RequestStatusInfo.label(r.getStatus()));

        m.put("statusGroup",
                RequestStatusInfo.group(r.getStatus()).name());

        // Missing fields
        m.put("missingFields",
                missingFieldRepository
                        .findByQuotationRequestIdAndStatus(
                                r.getId(), "OPEN")
                        .stream()
                        .map(f -> f.getRequiredField().getDisplayLabel())
                        .toList());

        // =====================================================
        // LATEST QUOTATION AND CLIENT RESPONSE
        // =====================================================

        Quotation latestQuotation = quotationRepository
                .findFirstByQuotationRequestIdOrderByVersionNoDesc(
                        r.getId())
                .orElse(null);

        if (latestQuotation != null) {

            m.put("quotationNumber",
                    latestQuotation.getQuotationNumber());

            m.put("quotationStatus",
                    latestQuotation.getStatus());

            m.put("quotationSentAt",
                    latestQuotation.getSentAt());

            m.put("quotationPdfAvailable",
                    latestQuotation.getPdfPath() != null
                            && !latestQuotation.getPdfPath().isBlank());

            m.put("responseStatus",
                    latestQuotation.getResponseStatus() != null
                            ? latestQuotation.getResponseStatus()
                            : "PENDING");

            m.put("responseAt",
                    latestQuotation.getResponseAt());

            // Closure tracking
            m.put("closedAt", latestQuotation.getClosedAt());
            m.put("closureReason", latestQuotation.getClosureReason());

        } else {

            m.put("quotationNumber", null);
            m.put("quotationStatus", null);
            m.put("quotationSentAt", null);
            m.put("quotationPdfAvailable", false);
            m.put("responseStatus", "PENDING");
            m.put("responseAt", null);
            m.put("closedAt", null);
            m.put("closureReason", null);
        }

        // Assignment
        m.put("assignedToId",
                r.getAssignedTo() != null
                        ? r.getAssignedTo().getId()
                        : null);

        // Timestamps
        m.put("createdAt", r.getCreatedAt());
        m.put("updatedAt", r.getUpdatedAt());

        return m;
    }

    // =========================================================
    // TRAVELLER
    // =========================================================

    private Map<String, Object> traveller(
            QuotationRequestTraveller t) {

        Map<String, Object> m = new HashMap<>();

        m.put("number", t.getTravellerNumber());
        m.put("name", t.getName());
        m.put("dateOfBirth", t.getDateOfBirth());
        m.put("age", t.getAgeAtSubmission());
        m.put("passportNumber", t.getPassportNumber());
        m.put("gender", t.getGender());
        m.put("nationality", t.getNationality());
        m.put("countryOfResidence", t.getCountryOfResidence());
        m.put("email", t.getEmail());
        m.put("phone", t.getPhone());
        m.put("preExistingMedicalConditions",
                t.getPreExistingMedicalConditions());

        return m;
    }

    // =========================================================
    // PARSE
    // =========================================================

    private long parse(String v) {

        try {
            return v == null ? 0 : Long.parseLong(v.trim());

        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
