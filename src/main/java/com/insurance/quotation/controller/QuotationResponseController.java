
package com.insurance.quotation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.insurance.quotation.service.quotation.QuotationResponseService;
import com.insurance.quotation.service.quotation.QuotationResponseService.ResponseResult;

@RestController
@RequestMapping("/api/public/quotation-response")
public class QuotationResponseController {

    private final QuotationResponseService responseService;

    public QuotationResponseController(
            QuotationResponseService responseService) {

        this.responseService = responseService;
    }

    @PostMapping("/{token}")
    public ResponseEntity<String> respond(
            @PathVariable String token,
            @RequestBody ResponseRequest request) {

        if (request == null || request.decision() == null) {
            return ResponseEntity
                    .badRequest()
                    .body("A quotation decision is required.");
        }

        ResponseResult result =
                responseService.respond(token, request.decision());

        return switch (result) {

            case ACCEPTED -> ResponseEntity.ok(
                    "Your quotation has been accepted successfully. "
                    + "Our team will process your acceptance manually.");

            case REJECTED -> ResponseEntity.ok(
                    "Your quotation has been rejected successfully. "
                    + "The quotation has been closed.");

            case ALREADY_RESPONDED -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("You have already responded to this quotation. "
                    + "Your previous response has been recorded and "
                    + "cannot be changed.");

            case CLOSED -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("This quotation is already closed and "
                    + "cannot accept any further responses.");

            case INVALID_TOKEN -> ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("This quotation response link is invalid.");

            case INVALID_RESPONSE -> ResponseEntity
                    .badRequest()
                    .body("Invalid quotation decision. "
                    + "Please use the Accept or Reject link "
                    + "provided in your quotation email.");
        };
    }

    public record ResponseRequest(String decision) {
    }
}
