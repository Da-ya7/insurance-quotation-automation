package com.insurance.quotation.mailbox;

/**
 * Sends outgoing mail through whichever provider is configured.
 */
public interface OutboundMailer {

    void sendPlain(
            String to,
            String subject,
            String body);

    void sendWithAttachment(
            String to,
            String subject,
            String body,
            String pdfPath);

    default void sendHtmlWithAttachment(
            String to,
            String subject,
            String htmlBody,
            String pdfPath) {

        sendWithAttachment(
                to,
                subject,
                htmlBody,
                pdfPath);
    }

    /**
     * Sends an HTML email with an attachment and returns
     * the provider's conversation and message identifiers.
     */
    default SendResult sendHtmlWithAttachmentAndThreadDetails(
            String to,
            String subject,
            String htmlBody,
            String pdfPath) {

        sendHtmlWithAttachment(
                to,
                subject,
                htmlBody,
                pdfPath);

        return new SendResult(null, null);
    }

    /**
     * Sends a reply to an existing email conversation.
     *
     * Providers that do not support threading will fall back
     * to sending a normal email.
     */
    default void sendReplyInThread(
            String to,
            String subject,
            String body,
            String threadId,
            String originalMessageId) {

        sendPlain(
                to,
                subject,
                body);
    }

    /**
     * Contains the identifiers returned by the email provider.
     */
    record SendResult(
            String threadId,
            String messageId) {
    }
}