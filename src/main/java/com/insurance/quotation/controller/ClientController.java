package com.insurance.quotation.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.insurance.quotation.dto.response.ClientResponse;
import com.insurance.quotation.dto.response.ClientVersionResponse;
import com.insurance.quotation.entity.Client;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.ClientVersionRepository;
import com.insurance.quotation.repository.QuotationRequestRepository;
import com.insurance.quotation.service.client.ClientMasterService;

/** Client master for staff. Editing needs a staff login (see SecurityConfig). */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    /** Fields staff may change. The identity number is accepted but never returned. */
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
    private final ClientMasterService clientMasterService;

    public ClientController(
            ClientRepository clientRepository,
            ClientVersionRepository versionRepository,
            QuotationRequestRepository requestRepository,
            ClientMasterService clientMasterService) {
        this.clientRepository = clientRepository;
        this.versionRepository = versionRepository;
        this.requestRepository = requestRepository;
        this.clientMasterService = clientMasterService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ClientResponse> getAllClients() {
        return clientRepository.findAll().stream()
                .map(c -> ClientResponse.from(c, requestRepository.countByClientId(c.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ClientResponse> getClientById(@PathVariable Long id) {
        return clientRepository.findById(id)
                .map(c -> ResponseEntity.ok(
                        ClientResponse.from(c, requestRepository.countByClientId(c.getId()))))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Full change history of one client, newest version first. */
    @GetMapping("/{id}/versions")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ClientVersionResponse>> versions(@PathVariable Long id) {
        if (!clientRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(
                versionRepository.findByClientIdOrderByVersionNoDesc(id).stream()
                        .map(ClientVersionResponse::from).toList());
    }

    /** Staff edit. The logged-in staff user is authorised, so no OTP is asked. */
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

        ClientMasterService.ApplyResult result = clientMasterService.apply(
                client,
                new ClientMasterService.ClientDetails(
                        body.name(), body.email(), body.phone(), body.dateOfBirth(),
                        body.gender(), body.nationality(), body.countryOfResidence(),
                        body.identityCountry(), body.identityType(), body.identityNumber()),
                "STAFF", authentication.getName(), true);

        if (result.hasBlockedChanges()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    "Not changed: another client already uses this identity document ("
                            + String.join(", ", result.blocked()) + ").");
        }

        return ResponseEntity.ok(ClientResponse.from(
                result.client(), requestRepository.countByClientId(id)));
    }
}
