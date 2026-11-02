package com.insurance.quotation.service.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IdentityProtectionServiceTest {

    private final IdentityProtectionService service =
            new IdentityProtectionService("unit-test-key-unit-test-key-1234567");

    @Test
    void encryptThenDecryptReturnsNormalizedNumber() {
        assertEquals("123456789012",
                service.decrypt(service.encrypt("1234 5678 9012")));
    }

    @Test
    void encryptionIsRandomizedPerCall() {
        assertNotEquals(service.encrypt("X1234567"), service.encrypt("X1234567"));
    }

    @Test
    void hashIgnoresSpacingAndCaseButNotType() {
        assertEquals(
                service.hash("India", "AADHAAR", "1234 5678 9012"),
                service.hash("india", "aadhaar", "123456789012"));
        assertNotEquals(
                service.hash("India", "AADHAAR", "123456789012"),
                service.hash("India", "PASSPORT", "123456789012"));
    }

    @Test
    void maskShowsOnlyLastFour() {
        assertEquals("XXXX9012",
                IdentityProtectionService.mask(service.last4("1234 5678 9012")));
        assertNull(IdentityProtectionService.mask(null));
    }

    @Test
    void shortKeyIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> new IdentityProtectionService("too-short"));
    }
}
