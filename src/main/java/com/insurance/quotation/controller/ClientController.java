package com.insurance.quotation.controller;

import com.insurance.quotation.dto.response.ClientResponse;
import com.insurance.quotation.entity.Client;
import com.insurance.quotation.repository.ClientRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientRepository clientRepository;

    public ClientController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @GetMapping
    public ResponseEntity<List<ClientResponse>> getAllClients() {

        List<ClientResponse> clients = clientRepository.findAll()
                .stream()
                .map(client -> new ClientResponse(
                        client.getId(),
                        client.getName(),
                        client.getEmail(),
                        client.getPhone()
                ))
                .toList();

        return ResponseEntity.ok(clients);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> getClientById(@PathVariable Long id) {

        Client client = clientRepository.findById(id).orElse(null);

        if (client == null) {
            return ResponseEntity.notFound().build();
        }

        ClientResponse response = new ClientResponse(
                client.getId(),
                client.getName(),
                client.getEmail(),
                client.getPhone()
        );

        return ResponseEntity.ok(response);
    }
}