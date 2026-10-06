package com.insurance.quotation.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.insurance.quotation.dto.response.RequestDetailResponse;
import com.insurance.quotation.dto.response.RequestSummaryResponse;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.repository.QuotationRequestRepository;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final QuotationRequestRepository quotationRequestRepository;

    public RequestController(
            QuotationRequestRepository quotationRequestRepository) {
        this.quotationRequestRepository = quotationRequestRepository;
    }

    @GetMapping
    public ResponseEntity<?> getAllRequests(
            @RequestParam(required = false) String status) {

        List<QuotationRequest> requests;

        if (status != null && !status.isBlank()) {
            try {
                RequestStatus requestStatus =
                        RequestStatus.valueOf(status.trim().toUpperCase());

                requests = quotationRequestRepository
                        .findByStatus(requestStatus);

            } catch (IllegalArgumentException exception) {
                return ResponseEntity.badRequest()
                        .body("Invalid request status: " + status);
            }

        } else {
            requests = quotationRequestRepository.findAll();
        }

        List<RequestSummaryResponse> response = requests.stream()
                .map(request -> new RequestSummaryResponse(
                        request.getId(),
                        request.getClient().getId(),
                        request.getPolicyType(),
                        request.getStatus(),
                        request.getAssignedTo() != null
                                ? request.getAssignedTo().getId()
                                : null,
                        request.getCreatedAt(),
                        request.getUpdatedAt()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RequestDetailResponse> getRequestById(
            @PathVariable Long id) {

        QuotationRequest request =
                quotationRequestRepository.findById(id).orElse(null);

        if (request == null) {
            return ResponseEntity.notFound().build();
        }

        RequestDetailResponse response = new RequestDetailResponse(
                request.getId(),
                request.getClient().getId(),
                request.getClient().getName(),
                request.getClient().getEmail(),
                request.getClient().getPhone(),
                request.getPolicyType(),
                request.getStatus(),
                request.getAssignedTo() != null
                        ? request.getAssignedTo().getId()
                        : null,
                request.getCreatedAt(),
                request.getUpdatedAt()
        );

        return ResponseEntity.ok(response);
    }
}