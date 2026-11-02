package com.insurance.quotation.service.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development sender: prints the message (and so the OTP) to the log. */
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void send(String phone, String message) {
        log.warn("[DEV SMS to {}] {}", phone, message);
    }
}
