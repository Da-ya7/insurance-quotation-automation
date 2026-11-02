package com.insurance.quotation.service.otp;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/** Sends SMS through Twilio's REST API (no extra library needed). */
public class TwilioSmsSender implements SmsSender {

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final String defaultCountryCode;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public TwilioSmsSender(String accountSid, String authToken, String fromNumber,
                           String defaultCountryCode) {
        if (accountSid == null || accountSid.isBlank() || authToken == null
                || authToken.isBlank() || fromNumber == null || fromNumber.isBlank()) {
            throw new IllegalStateException(
                    "sms.mode=twilio needs sms.twilio.account-sid, auth-token and from-number.");
        }
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.defaultCountryCode = defaultCountryCode == null ? "+91" : defaultCountryCode;
    }

    @Override
    public void send(String phone, String message) {
        String to = phone.startsWith("+") ? phone
                : (phone.length() == 10 ? defaultCountryCode + phone : "+" + phone);
        String form = "To=" + enc(to) + "&From=" + enc(fromNumber) + "&Body=" + enc(message);
        String auth = Base64.getEncoder().encodeToString(
                (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("SMS provider rejected the message (HTTP "
                        + response.statusCode() + ").");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SMS sending was interrupted.", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not reach the SMS provider.", e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
