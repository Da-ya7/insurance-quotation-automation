package com.insurance.quotation.mailbox.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.insurance.quotation.mailbox.OutboundMailer;

/** Demo mode: outgoing mail is only written to the log. */
public class DemoOutboundMailer implements OutboundMailer {

    private static final Logger log = LoggerFactory.getLogger(DemoOutboundMailer.class);

    @Override
    public void sendPlain(String to, String subject, String body) {
        log.info("[DEMO MAIL] to={} subject={}\n{}", to, subject, body);
    }

    @Override
    public void sendWithAttachment(String to, String subject, String body, String pdfPath) {
        log.info("[DEMO MAIL] to={} subject={} attachment={}\n{}", to, subject, pdfPath, body);
    }
}
