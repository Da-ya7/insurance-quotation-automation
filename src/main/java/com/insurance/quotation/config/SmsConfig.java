package com.insurance.quotation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.insurance.quotation.service.otp.LoggingSmsSender;
import com.insurance.quotation.service.otp.SmsSender;
import com.insurance.quotation.service.otp.TwilioSmsSender;

@Configuration
public class SmsConfig {

    /** sms.mode=log (default, development) or twilio. */
    @Bean
    public SmsSender smsSender(
            @Value("${sms.mode:log}") String mode,
            @Value("${sms.twilio.account-sid:}") String sid,
            @Value("${sms.twilio.auth-token:}") String token,
            @Value("${sms.twilio.from-number:}") String from,
            @Value("${sms.default-country-code:+91}") String defaultCountryCode) {

        if ("twilio".equalsIgnoreCase(mode)) {
            return new TwilioSmsSender(sid, token, from, defaultCountryCode);
        }
        return new LoggingSmsSender();
    }
}
