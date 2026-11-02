package com.insurance.quotation.mailbox;

/** Sends outgoing mail through whichever provider is configured. */
public interface OutboundMailer {

    void sendPlain(String to, String subject, String body);

    void sendWithAttachment(String to, String subject, String body, String pdfPath);
}
