package com.insurance.quotation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "incoming_emails")
public class IncomingEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // quotation_request_id
    @ManyToOne
    @JoinColumn(name = "quotation_request_id")
    private QuotationRequest quotationRequest;

    // message_id
    @Column(name = "message_id", nullable = false, unique = true, length = 255)
    private String messageId;

    // in_reply_to
    @Column(name = "in_reply_to", length = 255)
    private String inReplyTo;

    // sender_email
    @Column(name = "sender_email", nullable = false, length = 150)
    private String senderEmail;

    // subject
    @Column(name = "subject", length = 255)
    private String subject;

    // body
    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    // processing_status
    @Column(name = "processing_status", nullable = false)
    private String processingStatus = "RECEIVED";

    // received_at
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    // created_at
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Automatically set createdAt before saving
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public QuotationRequest getQuotationRequest() {
        return quotationRequest;
    }

    public void setQuotationRequest(QuotationRequest quotationRequest) {
        this.quotationRequest = quotationRequest;
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

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}