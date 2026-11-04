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
 * Handles two separate OTP flows:
 *
 * 1. Returning customers: OTP sent to the phone already saved on their
 *    personal form link.
 * 2. New customers: OTP sent to the phone they enter on the public form.
 *
 * OTP state is currently held in memory. This is suitable only for a
 * single-instance development/demo deployment.
 */
@Service
public class OtpService {

    public static final Duration OTP_TTL = Duration.ofMinutes(10);
    public static final Duration VERIFIED_TTL = Duration.ofMinutes(30);

    public static final int MAX_SENDS_PER_HOUR = 3;
    public static final int MAX_ATTEMPTS = 5;

    public record SendResult(String phoneMasked, int expiresInSeconds) { }

    public record VerifyResult(
            boolean verified,
            int attemptsLeft,
            String message
    ) { }

    public record NewCustomerSendResult(
            String challengeId,
            String phoneMasked,
            int expiresInSeconds
    ) { }

    public record NewCustomerVerifyResult(
            boolean verified,
            String verificationToken,
            int attemptsLeft,
            String message
    ) { }

    public enum Reason {
        NO_PHONE,
        TOO_MANY_REQUESTS,
        SEND_FAILED,
        INVALID_CHALLENGE
    }

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

    private static final class NewCustomerChallenge {

        String salt;
        String hash;
        String normalizedPhone;
        Instant expiresAt;
        int attempts;
    }

    private final SmsSender sms;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    // Existing returning-customer link OTP state.
    private final Map<String, Challenge> challenges =
            new ConcurrentHashMap<>();

    private final Map<String, Instant> verified =
            new ConcurrentHashMap<>();

    private final Map<String, Deque<Instant>> sends =
            new ConcurrentHashMap<>();

    // New-customer OTP state.
    private final Map<String, NewCustomerChallenge> newChallenges =
            new ConcurrentHashMap<>();

    private final Map<String, VerifiedPhone> newVerifiedPhones =
            new ConcurrentHashMap<>();

    // New-customer send limits are keyed by normalized phone, not challenge ID.
    private final Map<String, Deque<Instant>> phoneSends =
            new ConcurrentHashMap<>();

    private record VerifiedPhone(String normalizedPhone, Instant expiresAt) { }

    @Autowired
    public OtpService(SmsSender sms) {
        this(sms, Clock.systemUTC());
    }

    OtpService(SmsSender sms, Clock clock) {
        this.sms = sms;
        this.clock = clock;
    }

    // ================================================================
    // RETURNING CUSTOMER FLOW — existing personal form link
    // ================================================================

    public synchronized SendResult send(String token, String phone) {

        if (token == null || token.isBlank() || !isValidPhone(phone)) {
            throw new OtpException(
                    "There is no valid phone number on file to send a code to.",
                    Reason.NO_PHONE
            );
        }

        Instant now = clock.instant();

        Deque<Instant> recent =
                sends.computeIfAbsent(token, key -> new ArrayDeque<>());

        removeOldSendTimes(recent, now);

        if (recent.size() >= MAX_SENDS_PER_HOUR) {
            throw new OtpException(
                    "Too many codes requested. Please try again in an hour.",
                    Reason.TOO_MANY_REQUESTS
            );
        }

        String code = generateCode();

        Challenge challenge = new Challenge();
        challenge.salt = randomHex(16);
        challenge.hash = hash(challenge.salt, code);
        challenge.expiresAt = now.plus(OTP_TTL);
        challenge.attempts = 0;

        try {
            sms.send(
                    phoneForSms(phone),
                    messageFor(code)
            );
        } catch (RuntimeException ex) {
            throw new OtpException(
                    "We could not send the code right now. Please try again shortly.",
                    Reason.SEND_FAILED
            );
        }

        challenges.put(token, challenge);
        recent.addLast(now);

        return new SendResult(
                maskPhone(phone),
                (int) OTP_TTL.toSeconds()
        );
    }

    public synchronized VerifyResult verify(String token, String code) {

        Instant now = clock.instant();
        Challenge challenge = challenges.get(token);

        if (challenge == null || now.isAfter(challenge.expiresAt)) {
            challenges.remove(token);
            return new VerifyResult(
                    false,
                    0,
                    "The code has expired. Please request a new one."
            );
        }

        if (challenge.attempts >= MAX_ATTEMPTS) {
            return new VerifyResult(
                    false,
                    0,
                    "Too many wrong attempts. Please request a new code."
            );
        }

        challenge.attempts++;

        boolean correct = matches(challenge.salt, code, challenge.hash);

        if (!correct) {
            int left = MAX_ATTEMPTS - challenge.attempts;

            return new VerifyResult(
                    false,
                    left,
                    left > 0
                            ? "That code is not correct."
                            : "Too many wrong attempts. Please request a new code."
            );
        }

        challenges.remove(token);
        verified.put(token, now.plus(VERIFIED_TTL));

        return new VerifyResult(true, MAX_ATTEMPTS - challenge.attempts, "Verified");
    }

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

    // ================================================================
    // NEW CUSTOMER FLOW — phone entered on public form
    // ================================================================

    public synchronized NewCustomerSendResult sendNewCustomerOtp(String phone) {

        if (!isValidPhone(phone)) {
            throw new OtpException(
                    "Enter a valid phone number before requesting an OTP.",
                    Reason.NO_PHONE
            );
        }

        Instant now = clock.instant();
        String normalizedPhone = normalizePhone(phone);

        cleanupExpiredNewCustomerState(now);

        // Rate limit by phone, not by challenge ID.
        String phoneKey = hash("phone-rate-limit", normalizedPhone);

        Deque<Instant> recent =
                phoneSends.computeIfAbsent(phoneKey, key -> new ArrayDeque<>());

        removeOldSendTimes(recent, now);

        if (recent.size() >= MAX_SENDS_PER_HOUR) {
            throw new OtpException(
                    "Too many codes requested for this phone number. Please try again later.",
                    Reason.TOO_MANY_REQUESTS
            );
        }

        String code = generateCode();
        String challengeId = randomHex(32);

        NewCustomerChallenge challenge = new NewCustomerChallenge();
        challenge.salt = randomHex(16);
        challenge.hash = hash(challenge.salt, code);
        challenge.normalizedPhone = normalizedPhone;
        challenge.expiresAt = now.plus(OTP_TTL);
        challenge.attempts = 0;

        try {
            sms.send(phoneForSms(phone), messageFor(code));
        } catch (RuntimeException ex) {
            throw new OtpException(
                    "We could not send the code right now. Please try again shortly.",
                    Reason.SEND_FAILED
            );
        }

        newChallenges.put(challengeId, challenge);
        recent.addLast(now);

        return new NewCustomerSendResult(
                challengeId,
                maskPhone(phone),
                (int) OTP_TTL.toSeconds()
        );
    }

    public synchronized NewCustomerVerifyResult verifyNewCustomerOtp(
            String challengeId,
            String code
    ) {

        Instant now = clock.instant();

        if (challengeId == null || challengeId.isBlank()) {
            return new NewCustomerVerifyResult(
                    false, null, 0, "Request a new verification code."
            );
        }

        NewCustomerChallenge challenge = newChallenges.get(challengeId);

        if (challenge == null || now.isAfter(challenge.expiresAt)) {
            newChallenges.remove(challengeId);

            return new NewCustomerVerifyResult(
                    false,
                    null,
                    0,
                    "The code has expired. Please request a new one."
            );
        }

        if (challenge.attempts >= MAX_ATTEMPTS) {
            return new NewCustomerVerifyResult(
                    false,
                    null,
                    0,
                    "Too many wrong attempts. Please request a new code."
            );
        }

        challenge.attempts++;

        boolean correct = matches(challenge.salt, code, challenge.hash);

        if (!correct) {
            int left = MAX_ATTEMPTS - challenge.attempts;

            return new NewCustomerVerifyResult(
                    false,
                    null,
                    left,
                    left > 0
                            ? "That code is not correct."
                            : "Too many wrong attempts. Please request a new code."
            );
        }

        newChallenges.remove(challengeId);

        // This token is issued only after successful verification.
        String verificationToken = randomHex(32);

        newVerifiedPhones.put(
                verificationToken,
                new VerifiedPhone(
                        challenge.normalizedPhone,
                        now.plus(VERIFIED_TTL)
                )
        );

        return new NewCustomerVerifyResult(
                true,
                verificationToken,
                MAX_ATTEMPTS - challenge.attempts,
                "Phone number verified."
        );
    }

    /**
     * Returns the normalized phone number belonging to a valid OTP
     * verification token. Returns null if the token is missing or expired.
     *
     * The browser does not get to choose the phone number used for lookup.
     */
    public String getVerifiedPhone(String verificationToken) {

        if (verificationToken == null || verificationToken.isBlank()) {
            return null;
        }

        VerifiedPhone verifiedPhone = newVerifiedPhones.get(verificationToken);

        if (verifiedPhone == null) {
            return null;
        }

        if (clock.instant().isAfter(verifiedPhone.expiresAt())) {
            newVerifiedPhones.remove(verificationToken);
            return null;
        }

        return verifiedPhone.normalizedPhone();
    }

    /**
     * Checks that the submitted phone is exactly the phone verified by
     * the new-customer OTP flow.
     */
    public boolean isPhoneVerified(String verificationToken, String submittedPhone) {

        if (verificationToken == null || verificationToken.isBlank()
                || !isValidPhone(submittedPhone)) {
            return false;
        }

        VerifiedPhone verifiedPhone = newVerifiedPhones.get(verificationToken);

        if (verifiedPhone == null) {
            return false;
        }

        if (clock.instant().isAfter(verifiedPhone.expiresAt())) {
            newVerifiedPhones.remove(verificationToken);
            return false;
        }

        return verifiedPhone.normalizedPhone().equals(normalizePhone(submittedPhone));
    }

    /**
     * Call only after the quotation submission has succeeded.
     * A verification token cannot be reused for another submission.
     */
    public boolean consumePhoneVerification(
            String verificationToken,
            String submittedPhone
    ) {

        if (!isPhoneVerified(verificationToken, submittedPhone)) {
            return false;
        }

        newVerifiedPhones.remove(verificationToken);
        return true;
    }

    // ================================================================
    // COMMON HELPERS
    // ================================================================

    public static String maskPhone(String phone) {

        String digits = phone == null ? "" : phone.replaceAll("\\D", "");

        if (digits.length() < 4) {
            return "XXXX";
        }

        return "XXXXXX" + digits.substring(digits.length() - 4);
    }

    private boolean isValidPhone(String phone) {

        if (phone == null) {
            return false;
        }

        String digits = phone.replaceAll("\\D", "");

        return digits.length() >= 10 && digits.length() <= 15;
    }

    /**
     * Normalization used for matching a submitted phone against the
     * phone verified by OTP. Formatting characters are ignored.
     */
    private String normalizePhone(String phone) {
        return phone.replaceAll("\\D", "");
    }

    /**
     * Preserve an explicitly supplied international + prefix.
     * Otherwise the configured SMS sender applies its default country code.
     */
    private String phoneForSms(String phone) {
        String trimmed = phone.trim();

        if (trimmed.startsWith("+")) {
            return "+" + trimmed.substring(1).replaceAll("\\D", "");
        }

        return trimmed.replaceAll("\\D", "");
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private String messageFor(String code) {
        return "Your insurance quotation verification code is " + code
                + ". It is valid for 10 minutes. Do not share it with anyone.";
    }

    private boolean matches(String salt, String suppliedCode, String expectedHash) {

        if (suppliedCode == null || !suppliedCode.trim().matches("\\d{6}")) {
            return false;
        }

        return MessageDigest.isEqual(
                hash(salt, suppliedCode.trim()).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void removeOldSendTimes(Deque<Instant> recent, Instant now) {

        Instant cutoff = now.minus(Duration.ofHours(1));

        while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
            recent.pollFirst();
        }
    }

    private void cleanupExpiredNewCustomerState(Instant now) {

        newChallenges.entrySet().removeIf(
                entry -> now.isAfter(entry.getValue().expiresAt)
        );

        newVerifiedPhones.entrySet().removeIf(
                entry -> now.isAfter(entry.getValue().expiresAt())
        );

        Instant cutoff = now.minus(Duration.ofHours(1));

        phoneSends.entrySet().removeIf(entry -> {
            Deque<Instant> times = entry.getValue();
            removeOldSendTimes(times, now);
            return times.isEmpty();
        });
    }

    private static String hash(String salt, String value) {

        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest((salt + ":" + value).getBytes(StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}