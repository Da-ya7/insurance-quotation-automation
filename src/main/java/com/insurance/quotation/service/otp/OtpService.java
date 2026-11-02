package com.insurance.quotation.service.otp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * One-time passwords sent to the phone number ALREADY ON FILE for a client, so
 * only the real owner can unlock edits to saved details. State is kept in
 * memory (short lived by design). Limits: 3 SMS per hour per link, 5 wrong
 * codes per OTP.
 */
@Service
public class OtpService {

    public static final Duration OTP_TTL = Duration.ofMinutes(10);
    public static final Duration VERIFIED_TTL = Duration.ofMinutes(30);
    public static final int MAX_SENDS_PER_HOUR = 3;
    public static final int MAX_ATTEMPTS = 5;

    public record SendResult(String phoneMasked, int expiresInSeconds) { }

    public record VerifyResult(boolean verified, int attemptsLeft, String message) { }

    public enum Reason { NO_PHONE, TOO_MANY_REQUESTS, SEND_FAILED }

    public static class OtpException extends RuntimeException {
        private final Reason reason;

        public OtpException(String message, Reason reason) {
            super(message);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }

    private static final class Challenge {
        String salt;
        String hash;
        Instant expiresAt;
        int attempts;
    }

    private final SmsSender sms;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, Instant> verified = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> sends = new ConcurrentHashMap<>();

    @Autowired
    public OtpService(SmsSender sms) {
        this(sms, Clock.systemUTC());
    }

    OtpService(SmsSender sms, Clock clock) {
        this.sms = sms;
        this.clock = clock;
    }

    public synchronized SendResult send(String token, String phone) {
        if (phone == null || phone.replaceAll("\\D", "").length() < 10) {
            throw new OtpException(
                    "There is no phone number on file to send a code to.", Reason.NO_PHONE);
        }
        Instant now = clock.instant();

        Deque<Instant> recent = sends.computeIfAbsent(token, k -> new ArrayDeque<>());
        while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
            recent.pollFirst();
        }
        if (recent.size() >= MAX_SENDS_PER_HOUR) {
            throw new OtpException(
                    "Too many codes requested. Please try again in an hour.", Reason.TOO_MANY_REQUESTS);
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        Challenge c = new Challenge();
        c.salt = Long.toHexString(random.nextLong());
        c.hash = hash(c.salt, code);
        c.expiresAt = now.plus(OTP_TTL);
        c.attempts = 0;

        try {
            sms.send(phone.replaceAll("[^0-9+]", ""),
                    "Your insurance quotation verification code is " + code
                            + ". It is valid for 10 minutes. Do not share it with anyone.");
        } catch (RuntimeException e) {
            throw new OtpException(
                    "We could not send the code right now. Please try again shortly.",
                    Reason.SEND_FAILED);
        }

        challenges.put(token, c);
        recent.addLast(now);
        return new SendResult(maskPhone(phone), (int) OTP_TTL.toSeconds());
    }

    public synchronized VerifyResult verify(String token, String code) {
        Instant now = clock.instant();
        Challenge c = challenges.get(token);

        if (c == null || now.isAfter(c.expiresAt)) {
            challenges.remove(token);
            return new VerifyResult(false, 0, "The code has expired. Please request a new one.");
        }
        if (c.attempts >= MAX_ATTEMPTS) {
            return new VerifyResult(false, 0, "Too many wrong attempts. Please request a new code.");
        }
        c.attempts++;

        boolean ok = code != null && MessageDigest.isEqual(
                hash(c.salt, code.trim()).getBytes(StandardCharsets.UTF_8),
                c.hash.getBytes(StandardCharsets.UTF_8));

        if (!ok) {
            int left = MAX_ATTEMPTS - c.attempts;
            return new VerifyResult(false, left,
                    left > 0 ? "That code is not correct." : "Too many wrong attempts. Please request a new code.");
        }
        challenges.remove(token);
        verified.put(token, now.plus(VERIFIED_TTL));
        return new VerifyResult(true, MAX_ATTEMPTS - c.attempts, "Verified");
    }

    /** True while this link has a recent successful OTP check. */
    public boolean isVerified(String token) {
        if (token == null) {
            return false;
        }
        Instant until = verified.get(token);
        if (until == null) {
            return false;
        }
        if (clock.instant().isAfter(until)) {
            verified.remove(token);
            return false;
        }
        return true;
    }

    public static String maskPhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "XXXX";
        }
        return "XXXXXX" + digits.substring(digits.length() - 4);
    }

    private static String hash(String salt, String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((salt + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
