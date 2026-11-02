package com.insurance.quotation.service.otp;

/** Sends a text message. Implementations are chosen by sms.mode. */
public interface SmsSender {

    void send(String phone, String message);
}
