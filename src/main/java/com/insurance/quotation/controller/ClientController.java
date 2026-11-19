package com.insurance.quotation.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.insurance.quotation.dto.response.ClientResponse;
import com.insurance.quotation.dto.response.ClientVersionResponse;
import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.RequestFieldValue;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.ClientVersionRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.repository.QuotationRepository;
import com.insurance.quotation.repository.RequestFieldValueRepository;
import com.insurance.quotation.service.client.ClientMasterService;
import com.insurance.quotation.service.client.IdentityProtectionService;

/**
 * Client master controller.
 */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    public record ClientEditRequest(
            String name,
            String email,
            String phone,
            LocalDate dateOfBirth,
            String gender,
            String nationality,
            String countryOfResidence,
            String identityCountry,
            String identityType,
            String identityNumber) {
    }

    // Response used when staff requests the complete identity number.
    public record IdentityNumberResponse(String identityNumber) {
    }

    private final ClientRepository clientRepository;
    private final ClientVersionRepository versionRepository;
    private final QuotationRequestRepository requestRepository;
    private final QuotationRepository quotationRepository;
    private final RequestFieldValueRepository fieldValueRepository;
    private final ClientMasterService clientMasterService;
    private final IdentityProtectionService identityProtectionService;

    public ClientController(
            ClientRepository clientRepository,
            ClientVersionRepository versionRepository,
            QuotationRequestRepository requestRepository,
            QuotationRepository quotationRepository,
            RequestFieldValueRepository fieldValueRepository,
            ClientMasterService clientMasterService,
            IdentityProtectionService identityProtectionService) {

        this.clientRepository = clientRepository;
        this.versionRepository = versionRepository;
        this.requestRepository = requestRepository;
        this.quotationRepository = quotationRepository;
        this.fieldValueRepository = fieldValueRepository;
        this.clientMasterService = clientMasterService;
        this.identityProtectionService = identityProtectionService;
    }

    // --------------------------------------------------
    // SEARCH CLIENTS USING MOBILE NUMBER
    // --------------------------------------------------

    /**
     * Finds all clients registered with the entered mobile number.
     *
     * Multiple people can share the same mobile number.
     * Therefore, this endpoint returns all matching clients.
     *
     * No OTP is required just to search or select a client.
     *
     * Example:
     * GET /api/clients/search-by-mobile?mobile=9876543210
     *
     * If no clients are found, an empty list is returned.
     */
    @GetMapping("/search-by-mobile")
    @Transactional(readOnly = true)
    public ResponseEntity<?> searchClientsByMobile(
            @RequestParam("mobile") String mobile) {

        try {

            List<Client> matchingClients =
                    clientMasterService.findClientsByMobile(mobile);

            List<ClientResponse> response =
                    matchingClients.stream()
                            .map(client -> ClientResponse.from(
                                    client,
                                    requestRepository.countByClientId(
                                            client.getId())))
                            .toList();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );
        }
    }

    // --------------------------------------------------
    // GET ALL CLIENTS
    // --------------------------------------------------

    @GetMapping
    @Transactional(readOnly = true)
    public List<ClientResponse> getAllClients() {

        return clientRepository.findAll().stream()
                .map(client -> ClientResponse.from(
                        client,
                        requestRepository.countByClientId(
                                client.getId())))
                .toList();
    }

    // --------------------------------------------------
    // GET INDIVIDUAL CLIENT DETAILS
    // --------------------------------------------------

    /**
     * Retrieves the selected client's details.
     *
     * The frontend can call this endpoint after the user
     * selects a name from the mobile-number search results.
     *
     * Example:
     * GET /api/clients/23
     */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ClientResponse> getClientById(
            @PathVariable Long id) {

        return clientRepository.findById(id)
                .map(client -> ResponseEntity.ok(
                        ClientResponse.from(
                                client,
                                requestRepository.countByClientId(
                                        client.getId()))))
                .orElse(ResponseEntity.notFound().build());
    }

    // --------------------------------------------------
    // GET CLIENT INSURANCE DETAILS
    // --------------------------------------------------

    @GetMapping("/{id}/insurance-details")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getClientInsuranceDetails(
            @PathVariable Long id) {

        if (!clientRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        List<QuotationRequest> requests =
                requestRepository.findByClient_IdOrderByCreatedAtDesc(id);

        List<Map<String, Object>> insuranceDetails = new ArrayList<>();

        for (QuotationRequest request : requests) {

            Map<String, Object> details = new HashMap<>();

            details.put("requestId", request.getId());
            details.put("policyType", request.getPolicyType());
            details.put("purposeOfTravel", request.getPurposeOfTravel());
            details.put("requestStatus", request.getStatus());
            details.put("requestCreatedAt", request.getCreatedAt());

            // Retrieve additional insurance and travel information.
            Map<String, String> fields = new HashMap<>();

            List<RequestFieldValue> fieldValues =
                    fieldValueRepository.findByQuotationRequestId(
                            request.getId());

            for (RequestFieldValue value : fieldValues) {

                fields.put(
                        value.getRequiredField().getFieldName(),
                        value.getFieldValue());
            }

            details.put("additionalDetails", fields);

            // Include all quotation versions.
            List<Map<String, Object>> quotations = new ArrayList<>();

            List<Quotation> quotationList =
                    quotationRepository
                            .findByQuotationRequestIdOrderByVersionNoDesc(
                                    request.getId());

            for (Quotation quotation : quotationList) {

                Map<String, Object> quotationDetails = new HashMap<>();

                quotationDetails.put(
                        "quotationId",
                        quotation.getId());

                quotationDetails.put(
                        "quotationNumber",
                        quotation.getQuotationNumber());

                quotationDetails.put(
                        "versionNo",
                        quotation.getVersionNo());

                quotationDetails.put(
                        "premiumAmount",
                        quotation.getPremiumAmount());

                quotationDetails.put(
                        "taxAmount",
                        quotation.getTaxAmount());

                quotationDetails.put(
                        "totalAmount",
                        quotation.getTotalAmount());

                quotationDetails.put(
                        "currency",
                        quotation.getCurrency());

                quotationDetails.put(
                        "quotationStatus",
                        quotation.getStatus());

                quotationDetails.put(
                        "createdAt",
                        quotation.getCreatedAt());

                quotationDetails.put(
                        "sentAt",
                        quotation.getSentAt());

                quotations.add(quotationDetails);
            }

            details.put("quotations", quotations);

            insuranceDetails.add(details);
        }

        return ResponseEntity.ok(insuranceDetails);
    }

    // --------------------------------------------------
    // GET COMPLETE IDENTITY NUMBER
    // --------------------------------------------------

    /**
     * Gets the decrypted identity number for an authenticated
     * staff user. The number remains encrypted in the database.
     */
    @GetMapping("/{id}/identity-number")
    @Transactional(readOnly = true)
    public ResponseEntity<IdentityNumberResponse> getIdentityNumber(
            @PathVariable Long id) {

        Client client = clientRepository.findById(id).orElse(null);

        if (client == null
                || client.getIdentityNumberEnc() == null
                || client.getIdentityNumberEnc().isBlank()) {

            return ResponseEntity.notFound().build();
        }

        String identityNumber =
                identityProtectionService.decrypt(
                        client.getIdentityNumberEnc());

        return ResponseEntity.ok(
                new IdentityNumberResponse(identityNumber));
    }

    // --------------------------------------------------
    // GET CLIENT VERSION HISTORY
    // --------------------------------------------------

    @GetMapping("/{id}/versions")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ClientVersionResponse>> versions(
            @PathVariable Long id) {

        if (!clientRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                versionRepository
                        .findByClientIdOrderByVersionNoDesc(id)
                        .stream()
                        .map(ClientVersionResponse::from)
                        .toList());
    }

    // --------------------------------------------------
    // EDIT CLIENT DETAILS
    // --------------------------------------------------

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> edit(
            @PathVariable Long id,
            @RequestBody ClientEditRequest body,
            Authentication authentication) {

        Client client = clientRepository.findById(id).orElse(null);

        if (client == null) {
            return ResponseEntity.notFound().build();
        }

        ClientMasterService.ApplyResult result =
                clientMasterService.apply(
                        client,
                        new ClientMasterService.ClientDetails(
                                body.name(),
                                body.email(),
                                body.phone(),
                                body.dateOfBirth(),
                                body.gender(),
                                body.nationality(),
                                body.countryOfResidence(),
                                body.identityCountry(),
                                body.identityType(),
                                body.identityNumber()),
                        "STAFF",
                        authentication.getName(),
                        true);

        if (result.hasBlockedChanges()) {

            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    "Not changed: another client already uses this identity document ("
                            + String.join(", ", result.blocked()) + ").");
        }

        return ResponseEntity.ok(
                ClientResponse.from(
                        result.client(),
                        requestRepository.countByClientId(id)));
    }
}
