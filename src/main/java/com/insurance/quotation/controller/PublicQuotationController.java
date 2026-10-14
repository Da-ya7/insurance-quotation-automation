package com.insurance.quotation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.insurance.quotation.dto.request.QuotationFormRequest;
import com.insurance.quotation.service.submission.FormSubmissionService;

import jakarta.validation.Valid;

/** Public endpoint used by the customer quotation form (no login). */
@RestController
@RequestMapping("/api/public")
public class PublicQuotationController {

    private final FormSubmissionService submissionService;

    public PublicQuotationController(FormSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping("/quotation-requests")
    public ResponseEntity<FormSubmissionService.SubmissionResult> submit(
            @Valid @RequestBody QuotationFormRequest form) {

        return ResponseEntity.ok(submissionService.submit(form));
    }
}
