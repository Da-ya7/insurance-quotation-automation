package com.insurance.quotation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "quotations")
public class Quotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "quotation_request_id", nullable = false)
    private QuotationRequest quotationRequest;

    // Version of this quotation for the request
    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    // Client record version used when quotation was created
    @Column(name = "client_version_no")
    private Integer clientVersionNo;

    @Column(name = "quotation_number", nullable = false, unique = true, length = 30)
    private String quotationNumber;

    @Column(name = "premium_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 6)
    private String currency = "INR";

    // Existing quotation status
    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "pdf_path", length = 255)
    private String pdfPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    // ==========================================
    // CLIENT RESPONSE TRACKING
    // ==========================================

    // PENDING, ACCEPTED, REJECTED
    @Column(name = "response_status", nullable = false)
    private String responseStatus = "PENDING";

    // Date and time client accepted or rejected
    @Column(name = "response_at")
    private LocalDateTime responseAt;

    // Secure token used in Accept/Reject email links
    @Column(name = "response_token", length = 64, unique = true)
    private String responseToken;

    // Date and time the last reminder was sent
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    // Number of reminders sent
    @Column(name = "reminder_count", nullable = false)
    private Integer reminderCount = 0;

    // Date and time quotation was closed
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // REJECTED or NO_RESPONSE
    @Column(name = "closure_reason", length = 50)
    private String closureReason;

    // ==========================================
    // GMAIL THREAD TRACKING
    // ==========================================

    // Gmail conversation ID of the original quotation email
    @Column(name = "gmail_thread_id", length = 255)
    private String gmailThreadId;

    // RFC Message-ID header of the original quotation email
    @Column(name = "gmail_message_id", length = 255)
    private String gmailMessageId;


    // ==========================================
    // GETTERS AND SETTERS
    // ==========================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public Integer getClientVersionNo() {
        return clientVersionNo;
    }

    public void setClientVersionNo(Integer clientVersionNo) {
        this.clientVersionNo = clientVersionNo;
    }

    public QuotationRequest getQuotationRequest() {
        return quotationRequest;
    }

    public void setQuotationRequest(QuotationRequest quotationRequest) {
        this.quotationRequest = quotationRequest;
    }

    public String getQuotationNumber() {
        return quotationNumber;
    }

    public void setQuotationNumber(String quotationNumber) {
        this.quotationNumber = quotationNumber;
    }

    public BigDecimal getPremiumAmount() {
        return premiumAmount;
    }

    public void setPremiumAmount(BigDecimal premiumAmount) {
        this.premiumAmount = premiumAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPdfPath() {
        return pdfPath;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    // Response tracking getters and setters

    public String getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(String responseStatus) {
        this.responseStatus = responseStatus;
    }

    public LocalDateTime getResponseAt() {
        return responseAt;
    }

    public void setResponseAt(LocalDateTime responseAt) {
        this.responseAt = responseAt;
    }

    public String getResponseToken() {
        return responseToken;
    }

    public void setResponseToken(String responseToken) {
        this.responseToken = responseToken;
    }

    public LocalDateTime getReminderSentAt() {
        return reminderSentAt;
    }

    public void setReminderSentAt(LocalDateTime reminderSentAt) {
        this.reminderSentAt = reminderSentAt;
    }

    public Integer getReminderCount() {
        return reminderCount;
    }

    public void setReminderCount(Integer reminderCount) {
        this.reminderCount = reminderCount;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getClosureReason() {
        return closureReason;
    }

    public void setClosureReason(String closureReason) {
        this.closureReason = closureReason;
    }

    // Gmail thread tracking getters and setters

    public String getGmailThreadId() {
        return gmailThreadId;
    }

    public void setGmailThreadId(String gmailThreadId) {
        this.gmailThreadId = gmailThreadId;
    }

    public String getGmailMessageId() {
        return gmailMessageId;
    }

    public void setGmailMessageId(String gmailMessageId) {
        this.gmailMessageId = gmailMessageId;
    }
}