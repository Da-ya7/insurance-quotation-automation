package com.insurance.quotation.mailbox;

import java.time.LocalDateTime;

public class EmailMessage {

    private String messageId;
    private String inReplyTo;
    private String senderEmail;
    private String subject;
    private String body;
    private LocalDateTime receivedAt;

    public EmailMessage() {
    }

    public EmailMessage(
            String messageId,
            String inReplyTo,
            String senderEmail,
            String subject,
            String body,
            LocalDateTime receivedAt) {

        this.messageId = messageId;
        this.inReplyTo = inReplyTo;
        this.senderEmail = senderEmail;
        this.subject = subject;
        this.body = body;
        this.receivedAt = receivedAt;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getInReplyTo() {
        return inReplyTo;
    }

    public void setInReplyTo(String inReplyTo) {
        this.inReplyTo = inReplyTo;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}