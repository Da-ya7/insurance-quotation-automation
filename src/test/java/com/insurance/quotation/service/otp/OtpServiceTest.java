package com.insurance.quotation.service.otp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OtpServiceTest {

    private static class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-29T10:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final List<String> sent = new ArrayList<>();
    private TestClock clock;
    private OtpService service;

    @BeforeEach
    void setUp() {
        sent.clear();
        clock = new TestClock();
        service = new OtpService((phone, message) -> sent.add(phone + "|" + message), clock);
    }

    private String lastCode() {
        Matcher m = Pattern.compile("code is (\\d{6})").matcher(sent.get(sent.size() - 1));
        assertTrue(m.find());
        return m.group(1);
    }

    @Test
    void correctCodeVerifiesAndUnlocksTheLink() {
        OtpService.SendResult r = service.send("tok", "9876543210");
        assertEquals("XXXXXX3210", r.phoneMasked());
        assertFalse(service.isVerified("tok"));

        assertTrue(service.verify("tok", lastCode()).verified());
        assertTrue(service.isVerified("tok"));

        clock.advance(Duration.ofMinutes(31));
        assertFalse(service.isVerified("tok"));
    }

    @Test
    void codeGoesToTheNumberOnFileNotElsewhere() {
        service.send("tok", "+91 98765 43210");
        assertTrue(sent.get(0).startsWith("+919876543210|"));
    }

    @Test
    void wrongCodeIsRejectedAndLockedAfterFiveTries() {
        service.send("tok", "9876543210");
        String wrong = lastCode().equals("000000") ? "111111" : "000000";

        OtpService.VerifyResult r = null;
        for (int i = 0; i < 5; i++) {
            r = service.verify("tok", wrong);
            assertFalse(r.verified());
        }
        assertEquals(0, r.attemptsLeft());
        // even the right code no longer works until a new one is requested
        assertFalse(service.verify("tok", lastCode()).verified());
        assertFalse(service.isVerified("tok"));
    }

    @Test
    void expiredCodeIsRejected() {
        service.send("tok", "9876543210");
        String code = lastCode();
        clock.advance(Duration.ofMinutes(11));
        assertFalse(service.verify("tok", code).verified());
    }

    @Test
    void onlyThreeCodesPerHour() {
        service.send("tok", "9876543210");
        service.send("tok", "9876543210");
        service.send("tok", "9876543210");
        OtpService.OtpException e = assertThrows(OtpService.OtpException.class,
                () -> service.send("tok", "9876543210"));
        assertEquals(OtpService.Reason.TOO_MANY_REQUESTS, e.getReason());

        clock.advance(Duration.ofMinutes(61));
        service.send("tok", "9876543210");
    }

    @Test
    void missingPhoneCannotReceiveACode() {
        OtpService.OtpException e = assertThrows(OtpService.OtpException.class,
                () -> service.send("tok", null));
        assertEquals(OtpService.Reason.NO_PHONE, e.getReason());
    }

    @Test
    void smsFailureIsReportedAsSendFailed() {
        OtpService failing = new OtpService((p, m) -> { throw new IllegalStateException("down"); }, clock);
        OtpService.OtpException e = assertThrows(OtpService.OtpException.class,
                () -> failing.send("tok", "9876543210"));
        assertEquals(OtpService.Reason.SEND_FAILED, e.getReason());
    }

    @Test
    void oneLinksVerificationDoesNotUnlockAnother() {
        service.send("a", "9876543210");
        assertTrue(service.verify("a", lastCode()).verified());
        assertFalse(service.isVerified("b"));
    }
}
