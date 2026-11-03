// package com.insurance.quotation.controller;

// import java.time.LocalDate;
// import java.util.List;

// import org.springframework.http.HttpStatus;
// import org.springframework.http.ResponseEntity;
// import org.springframework.security.core.Authentication;
// import org.springframework.transaction.annotation.Transactional;
// import org.springframework.web.bind.annotation.*;

// import com.insurance.quotation.dto.response.ClientResponse;
// import com.insurance.quotation.dto.response.ClientVersionResponse;
// import com.insurance.quotation.entity.Client;
// import com.insurance.quotation.repository.ClientRepository;
// import com.insurance.quotation.repository.ClientVersionRepository;
// import com.insurance.quotation.repository.QuotationRequestRepository;
// import com.insurance.quotation.service.client.ClientMasterService;

// /** Client master for staff. Editing needs a staff login (see SecurityConfig). */
// @RestController
// @RequestMapping("/api/clients")
// public class ClientController {

//     /** Fields staff may change. The identity number is accepted but never returned. */
//     public record ClientEditRequest(
//             String name,
//             String email,
//             String phone,
//             LocalDate dateOfBirth,
//             String gender,
//             String nationality,
//             String countryOfResidence,
//             String identityCountry,
//             String identityType,
//             String identityNumber) {
//     }

//     private final ClientRepository clientRepository;
//     private final ClientVersionRepository versionRepository;
//     private final QuotationRequestRepository requestRepository;
//     private final ClientMasterService clientMasterService;

//     public ClientController(
//             ClientRepository clientRepository,
//             ClientVersionRepository versionRepository,
//             QuotationRequestRepository requestRepository,
//             ClientMasterService clientMasterService) {
//         this.clientRepository = clientRepository;
//         this.versionRepository = versionRepository;
//         this.requestRepository = requestRepository;
//         this.clientMasterService = clientMasterService;
//     }

//     @GetMapping
//     @Transactional(readOnly = true)
//     public List<ClientResponse> getAllClients() {
//         return clientRepository.findAll().stream()
//                 .map(c -> ClientResponse.from(c, requestRepository.countByClientId(c.getId())))
//                 .toList();
//     }

//     @GetMapping("/{id}")
//     @Transactional(readOnly = true)
//     public ResponseEntity<ClientResponse> getClientById(@PathVariable Long id) {
//         return clientRepository.findById(id)
//                 .map(c -> ResponseEntity.ok(
//                         ClientResponse.from(c, requestRepository.countByClientId(c.getId()))))
//                 .orElse(ResponseEntity.notFound().build());
//     }

//     /** Full change history of one client, newest version first. */
//     @GetMapping("/{id}/versions")
//     @Transactional(readOnly = true)
//     public ResponseEntity<List<ClientVersionResponse>> versions(@PathVariable Long id) {
//         if (!clientRepository.existsById(id)) {
//             return ResponseEntity.notFound().build();
//         }
//         return ResponseEntity.ok(
//                 versionRepository.findByClientIdOrderByVersionNoDesc(id).stream()
//                         .map(ClientVersionResponse::from).toList());
//     }

//     /** Staff edit. The logged-in staff user is authorised, so no OTP is asked. */
//     @PutMapping("/{id}")
//     @Transactional
//     public ResponseEntity<?> edit(
//             @PathVariable Long id,
//             @RequestBody ClientEditRequest body,
//             Authentication authentication) {

//         Client client = clientRepository.findById(id).orElse(null);
//         if (client == null) {
//             return ResponseEntity.notFound().build();
//         }

//         ClientMasterService.ApplyResult result = clientMasterService.apply(
//                 client,
//                 new ClientMasterService.ClientDetails(
//                         body.name(), body.email(), body.phone(), body.dateOfBirth(),
//                         body.gender(), body.nationality(), body.countryOfResidence(),
//                         body.identityCountry(), body.identityType(), body.identityNumber()),
//                 "STAFF", authentication.getName(), true);

//         if (result.hasBlockedChanges()) {
//             return ResponseEntity.status(HttpStatus.CONFLICT).body(
//                     "Not changed: another client already uses this identity document ("
//                             + String.join(", ", result.blocked()) + ").");
//         }

//         return ResponseEntity.ok(ClientResponse.from(
//                 result.client(), requestRepository.countByClientId(id)));
//     }
// }





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

/** Client master for staff. */
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

    private final ClientRepository clientRepository;
    private final ClientVersionRepository versionRepository;
    private final QuotationRequestRepository requestRepository;
    private final QuotationRepository quotationRepository;
    private final RequestFieldValueRepository fieldValueRepository;
    private final ClientMasterService clientMasterService;

    public ClientController(
            ClientRepository clientRepository,
            ClientVersionRepository versionRepository,
            QuotationRequestRepository requestRepository,
            QuotationRepository quotationRepository,
            RequestFieldValueRepository fieldValueRepository,
            ClientMasterService clientMasterService) {

        this.clientRepository = clientRepository;
        this.versionRepository = versionRepository;
        this.requestRepository = requestRepository;
        this.quotationRepository = quotationRepository;
        this.fieldValueRepository = fieldValueRepository;
        this.clientMasterService = clientMasterService;
    }

    // Get all clients
    @GetMapping
    @Transactional(readOnly = true)
    public List<ClientResponse> getAllClients() {

        return clientRepository.findAll().stream()
                .map(c -> ClientResponse.from(
                        c,
                        requestRepository.countByClientId(c.getId())))
                .toList();
    }

    // Get individual client details
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ClientResponse> getClientById(
            @PathVariable Long id) {

        return clientRepository.findById(id)
                .map(c -> ResponseEntity.ok(
                        ClientResponse.from(
                                c,
                                requestRepository.countByClientId(c.getId()))))
                .orElse(ResponseEntity.notFound().build());
    }

    // Get client insurance details
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

            // Retrieve additional insurance and travel information
            Map<String, String> fields = new HashMap<>();

            List<RequestFieldValue> fieldValues =
                    fieldValueRepository.findByQuotationRequestId(request.getId());

            for (RequestFieldValue value : fieldValues) {
                fields.put(
                        value.getRequiredField().getFieldName(),
                        value.getFieldValue());
            }

            details.put("additionalDetails", fields);

            // Include all quotation versions
            List<Map<String, Object>> quotations = new ArrayList<>();

            List<Quotation> quotationList =
                    quotationRepository
                            .findByQuotationRequestIdOrderByVersionNoDesc(
                                    request.getId());

            for (Quotation quotation : quotationList) {

                Map<String, Object> quotationDetails = new HashMap<>();

                quotationDetails.put("quotationId", quotation.getId());
                quotationDetails.put("quotationNumber",
                        quotation.getQuotationNumber());
                quotationDetails.put("versionNo",
                        quotation.getVersionNo());
                quotationDetails.put("premiumAmount",
                        quotation.getPremiumAmount());
                quotationDetails.put("taxAmount",
                        quotation.getTaxAmount());
                quotationDetails.put("totalAmount",
                        quotation.getTotalAmount());
                quotationDetails.put("currency",
                        quotation.getCurrency());
                quotationDetails.put("quotationStatus",
                        quotation.getStatus());
                quotationDetails.put("createdAt",
                        quotation.getCreatedAt());
                quotationDetails.put("sentAt",
                        quotation.getSentAt());

                quotations.add(quotationDetails);
            }

            details.put("quotations", quotations);

            insuranceDetails.add(details);
        }

        return ResponseEntity.ok(insuranceDetails);
    }

    // Get complete client version history
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

    // Edit client details
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
