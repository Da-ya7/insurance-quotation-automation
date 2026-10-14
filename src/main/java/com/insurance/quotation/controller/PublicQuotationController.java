
package com.insurance.quotation.controller;

import java.util.List;
import com.insurance.quotation.dto.ClientQuotationSubmission;
import com.insurance.quotation.service.submission.ClientQuotationSubmissionService;
import com.insurance.quotation.service.submission.ClientQuotationSubmissionService.SubmissionReceipt;
import com.insurance.quotation.service.submission.ClientQuotationSubmissionService.SubmissionResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/quotation-requests")
public class PublicQuotationController {

    private final ClientQuotationSubmissionService submissionService;

    public PublicQuotationController(
            ClientQuotationSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public ResponseEntity<SubmissionResponse> submit(
            @Valid @RequestBody ClientQuotationSubmission form) {

        SubmissionReceipt receipt =
                submissionService.persistSubmission(form);

        SubmissionResponse response;

        if (receipt.valid()) {
            response = new SubmissionResponse(
                    receipt.requestId(),
                    "PROCESSING",
                    "Your request was received. Your quotation is "
                            + "being prepared and will be emailed when ready.",
                    null,
                    List.of(),
                    List.of());
        } else {
            response = new SubmissionResponse(
                    receipt.requestId(),
                    "MISSING_INFORMATION",
                    "Your request was received. Please check the "
                            + "information identified below.",
                    null,
                    receipt.missingFields(),
                    receipt.invalidFields());
        }

        submissionService.processSubmissionAsync(receipt);

        return ResponseEntity.accepted().body(response);
    }
}
