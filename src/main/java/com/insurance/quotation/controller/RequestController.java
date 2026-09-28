package com.insurance.quotation.controller;

import com.insurance.quotation.dto.response.RequestDetailResponse;
import com.insurance.quotation.dto.response.RequestSummaryResponse;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.repository.QuotationRequestRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final QuotationRequestRepository quotationRequestRepository;

    public RequestController(QuotationRequestRepository quotationRequestRepository) {
        this.quotationRequestRepository = quotationRequestRepository;
    }

    @GetMapping
    public ResponseEntity<List<RequestSummaryResponse>> getAllRequests(
            @RequestParam(required = false) String status) {

        List<QuotationRequest> requests;

        if (status != null && !status.isBlank()) {
            requests = quotationRequestRepository.findByStatus(status);
        } else {
            requests = quotationRequestRepository.findAll();
        }

        List<RequestSummaryResponse> response = requests.stream()
                .map(request -> new RequestSummaryResponse(
                        request.getId(),
                        request.getClient().getId(),
                        request.getDestination(),
                        request.getDepartureDate(),
                        request.getReturnDate(),
                        request.getTravellerCount(),
                        request.getStatus()
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
                request.getDestination(),
                request.getDepartureDate(),
                request.getReturnDate(),
                request.getTravellerCount(),
                request.getStatus()
        );

        return ResponseEntity.ok(response);
    }
}