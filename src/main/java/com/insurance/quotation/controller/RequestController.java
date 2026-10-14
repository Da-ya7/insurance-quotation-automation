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

import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.QuotationRequestTraveller;
import com.insurance.quotation.entity.RequestFieldValue;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.repository.QuotationRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.repository.QuotationRequestTravellerRepository;
import com.insurance.quotation.repository.RequestFieldValueRepository;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final QuotationRequestRepository requestRepository;
    private final QuotationRequestTravellerRepository travellerRepository;
    private final RequestFieldValueRepository fieldValueRepository;
    private final QuotationRepository quotationRepository;

    public RequestController(
            QuotationRequestRepository requestRepository,
            QuotationRequestTravellerRepository travellerRepository,
            RequestFieldValueRepository fieldValueRepository,
            QuotationRepository quotationRepository) {
        this.requestRepository = requestRepository;
        this.travellerRepository = travellerRepository;
        this.fieldValueRepository = fieldValueRepository;
        this.quotationRepository = quotationRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllRequests(
            @RequestParam(required = false) String status) {

        List<QuotationRequest> requests;

        if (status != null && !status.isBlank()) {
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

        return ResponseEntity.ok(requests.stream().map(this::row).toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getRequestById(@PathVariable Long id) {

        QuotationRequest request = requestRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> body = new HashMap<>(row(request));

        body.put("purposeOfTravel", request.getPurposeOfTravel());
        body.put("clientPhone", request.getClient().getPhone());

        List<Map<String, Object>> travellers = travellerRepository
                .findByQuotationRequestIdOrderByTravellerNumber(id)
                .stream().map(this::traveller).toList();
        body.put("travellerDetails", travellers);

        Map<String, String> fields = new HashMap<>();
        for (RequestFieldValue v : fieldValueRepository.findByQuotationRequestId(id)) {
            fields.put(v.getRequiredField().getFieldName(), v.getFieldValue());
        }
        body.put("fields", fields);

        quotationRepository.findByQuotationRequestId(id).ifPresent(q -> {
            body.put("quotationId", q.getId());
            body.put("quotationNumber", q.getQuotationNumber());
            body.put("totalAmount", q.getTotalAmount());
        });

        return ResponseEntity.ok(body);
    }

    private Map<String, Object> row(QuotationRequest r) {
        Map<String, String> fields = new HashMap<>();
        for (RequestFieldValue v : fieldValueRepository.findByQuotationRequestId(r.getId())) {
            fields.put(v.getRequiredField().getFieldName(), v.getFieldValue());
        }

        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("reference", String.format("REQ-%d-%04d",
                r.getCreatedAt() == null ? 0 : r.getCreatedAt().getYear(), r.getId()));
        m.put("clientId", r.getClient().getId());
        m.put("clientName", r.getClient().getName());
        m.put("clientEmail", r.getClient().getEmail());
        m.put("policyType", r.getPolicyType());
        m.put("purposeOfTravel", r.getPurposeOfTravel());
        m.put("destination", fields.get("destinationCountry"));
        m.put("origin", fields.get("originCountry"));
        m.put("travelStartDate", fields.get("travelStartDate"));
        m.put("travelEndDate", fields.get("travelEndDate"));
        long count = travellerRepository.countByQuotationRequestId(r.getId());
        m.put("travellers", count > 0 ? count : parse(fields.get("travellers")));
        m.put("status", r.getStatus());
        m.put("assignedToId", r.getAssignedTo() != null ? r.getAssignedTo().getId() : null);
        m.put("createdAt", r.getCreatedAt());
        m.put("updatedAt", r.getUpdatedAt());
        return m;
    }

    private Map<String, Object> traveller(QuotationRequestTraveller t) {
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
        m.put("preExistingMedicalConditions", t.getPreExistingMedicalConditions());
        return m;
    }

    private long parse(String v) {
        try {
            return v == null ? 0 : Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
