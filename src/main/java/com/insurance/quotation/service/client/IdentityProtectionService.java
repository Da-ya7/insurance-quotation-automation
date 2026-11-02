package com.insurance.quotation.service.client;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Protects government ID numbers: AES-GCM encryption for storage and a keyed
 * HMAC hash so the same document can be found again without being readable.
 */
@Service
public class IdentityProtectionService {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;
    private final SecureRandom random = new SecureRandom();

    public IdentityProtectionService(
            @Value("${app.encryption-key}") String secret) {

        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "app.encryption-key must be at least 32 characters.");
        }
        this.aesKey = new SecretKeySpec(derive(secret, "enc"), "AES");
        this.hmacKey = new SecretKeySpec(derive(secret, "hmac"), "HmacSHA256");
    }

    /** Upper-case and strip spaces/dashes so "1234 5678-9012" == "123456789012". */
    public static String normalize(String number) {
        return number == null
                ? ""
                : number.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    public String encrypt(String number) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(normalize(number).getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt identity number.", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] in = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey,
                    new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            return new String(cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not decrypt identity number.", e);
        }
    }

    /** Stable lookup hash of identity type + issuing country + number. */
    public String hash(String country, String type, String number) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            String input = key(type) + "|" + key(country) + "|" + normalize(number);
            return HexFormat.of().formatHex(
                    mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not hash identity number.", e);
        }
    }

    public String last4(String number) {
        String n = normalize(number);
        return n.length() <= 4 ? n : n.substring(n.length() - 4);
    }

    /** What staff screens may show: all but the last 4 characters hidden. */
    public static String mask(String last4) {
        return last4 == null || last4.isBlank() ? null : "XXXX" + last4;
    }

    private static String key(String value) {
        return value == null || value.isBlank()
                ? "-"
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private static byte[] derive(String secret, String purpose) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((purpose + ":" + secret).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
