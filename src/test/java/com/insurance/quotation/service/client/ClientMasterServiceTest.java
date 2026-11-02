package com.insurance.quotation.service.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.ClientVersion;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.ClientVersionRepository;
import com.insurance.quotation.service.client.ClientMasterService.ApplyResult;
import com.insurance.quotation.service.client.ClientMasterService.ClientDetails;

/** Rules for the client master: separate people, OTP-protected edits, versions. */
class ClientMasterServiceTest {

    private final Map<Long, Client> clients = new LinkedHashMap<>();
    private final List<ClientVersion> versions = new ArrayList<>();
    private long seq = 0;

    private IdentityProtectionService identity;
    private ClientMasterService service;

    @BeforeEach
    void setUp() {
        identity = new IdentityProtectionService("unit-test-key-unit-test-key-1234567");

        ClientRepository repo = mock(ClientRepository.class);
        when(repo.save(any(Client.class))).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(++seq);
            }
            clients.put(c.getId(), c);
            return c;
        });
        when(repo.findAllByEmailIgnoreCase(anyString())).thenAnswer(inv ->
                clients.values().stream()
                        .filter(c -> c.getEmail().equalsIgnoreCase(inv.getArgument(0)))
                        .toList());
        when(repo.findByIdentityNumberHash(anyString())).thenAnswer(inv ->
                clients.values().stream()
                        .filter(c -> inv.getArgument(0).equals(c.getIdentityNumberHash()))
                        .findFirst());

        ClientVersionRepository versionRepo = mock(ClientVersionRepository.class);
        when(versionRepo.save(any(ClientVersion.class))).thenAnswer(inv -> {
            versions.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        service = new ClientMasterService(repo, versionRepo, identity);
    }

    private ClientDetails details(String name, String email, String phone,
                                  String idType, String idNo) {
        return new ClientDetails(name, email, phone, null, null, null, null,
                "India", idType, idNo);
    }

    @Test
    void peopleSharingAnEmailGetSeparateRecords() {
        Client asha = service.findOrCreateAndApply(
                details("Asha Rao", "family@mail.com", "9876543210", "AADHAAR", "1111 2222 3333"),
                "FORM", "t", false).client();
        Client ravi = service.findOrCreateAndApply(
                details("Ravi Rao", "family@mail.com", "9876500000", "AADHAAR", "4444 5555 6666"),
                "FORM", "t", false).client();

        assertNotEquals(asha.getId(), ravi.getId());
        assertEquals("CL-000001", asha.getClientCode());
        assertEquals("CL-000002", ravi.getClientCode());
        assertEquals("Asha Rao", asha.getName());
        assertEquals("9876543210", asha.getPhone());
    }

    @Test
    void returningClientIsFoundByIdentityEvenWithNewEmail() {
        Client first = service.findOrCreateAndApply(
                details("Asha Rao", "a@mail.com", "9876543210", "AADHAAR", "1111 2222 3333"),
                "FORM", "t", false).client();
        Client again = service.findOrCreateAndApply(
                details("Asha Rao", "other@mail.com", "9876543210", "AADHAAR", "111122223333"),
                "FORM", "t", false).client();

        assertEquals(first.getId(), again.getId());
    }

    @Test
    void sharedEmailWithoutNameIsNotGuessed() {
        service.findOrCreateAndApply(details("Asha Rao", "f@mail.com", null, null, null), "EMAIL", "t", false);
        service.findOrCreateAndApply(details("Ravi Rao", "f@mail.com", null, "PASSPORT", "P1234567"), "EMAIL", "t", false);

        assertTrue(service.find(details(null, "f@mail.com", null, null, null)).isEmpty());
        assertTrue(service.find(details("ravi  rao", "f@mail.com", null, null, null)).isPresent());
    }

    @Test
    void sensitiveEditNeedsOtpAndKeepsHistory() {
        Client asha = service.findOrCreateAndApply(
                details("Asha Rao", "a@mail.com", "9876543210", "AADHAAR", "1111 2222 3333"),
                "FORM", "t", false).client();
        int before = asha.getVersionNo();

        ApplyResult blocked = service.apply(asha,
                details("Asha Rao", "a@mail.com", "9000000000", "AADHAAR", "1111 2222 3333"),
                "FORM", "t", false);
        assertTrue(blocked.blocked().contains("phone"));
        assertEquals("9876543210", asha.getPhone());
        assertEquals(before, asha.getVersionNo());

        ApplyResult applied = service.apply(asha,
                details("Asha Rao", "a@mail.com", "9000000000", "AADHAAR", "1111 2222 3333"),
                "FORM", "t", true);
        assertTrue(applied.changed().contains("phone"));
        assertEquals("9000000000", asha.getPhone());
        assertEquals(before + 1, asha.getVersionNo());

        // version 1 (created) and version 2 (phone change) are both kept
        assertEquals(2, versions.size());
        assertEquals("9876543210", versions.get(0).getPhone());
        assertEquals("9000000000", versions.get(1).getPhone());
        assertTrue(versions.get(1).isOtpVerified());
    }

    @Test
    void missingDetailsCanBeFilledWithoutOtp() {
        Client bare = service.findOrCreateAndApply(
                details(null, "new@mail.com", null, null, null), "EMAIL", "t", false).client();

        ApplyResult filled = service.apply(bare,
                details("Neha", "new@mail.com", "9111111111", null, null),
                "EMAIL", "t", false);

        assertTrue(filled.filled().containsAll(List.of("name", "phone")));
        assertTrue(filled.blocked().isEmpty());
    }

    @Test
    void identityOwnedByAnotherClientIsNeverTakenOver() {
        Client asha = service.findOrCreateAndApply(
                details("Asha Rao", "f@mail.com", null, "AADHAAR", "1111 2222 3333"),
                "FORM", "t", false).client();
        Client ravi = service.findOrCreateAndApply(
                details("Ravi Rao", "f@mail.com", null, "AADHAAR", "4444 5555 6666"),
                "FORM", "t", false).client();

        ApplyResult result = service.apply(ravi,
                details("Ravi Rao", "f@mail.com", null, "AADHAAR", "111122223333"),
                "STAFF", "admin", true);

        assertTrue(result.blocked().contains("identityNumber"));
        assertNotEquals(asha.getIdentityNumberHash(), ravi.getIdentityNumberHash());
    }
}
