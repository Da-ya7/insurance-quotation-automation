package com.insurance.quotation.entity;
import java.time.LocalDateTime;

import com.insurance.quotation.entity.enums.FieldSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "request_field_values",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_rfv_request_field",
            columnNames = {"quotation_request_id", "required_field_id"}
        )
    }
)
public class RequestFieldValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // quotation_request_id
    @ManyToOne
    @JoinColumn(name = "quotation_request_id", nullable = false)
    private QuotationRequest quotationRequest;

    // required_field_id
    @ManyToOne
    @JoinColumn(name = "required_field_id", nullable = false)
    private RequiredField requiredField;

    // field_value
    @Column(name = "field_value", nullable = false, columnDefinition = "TEXT")
    private String fieldValue;

    // source
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private FieldSource source;

    // extracted_from_email_id
    @ManyToOne
    @JoinColumn(name = "extracted_from_email_id")
    private IncomingEmail extractedFromEmail;

    // created_at
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // updated_at
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


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

    public RequiredField getRequiredField() {
        return requiredField;
    }

    public void setRequiredField(RequiredField requiredField) {
        this.requiredField = requiredField;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }

    public FieldSource getSource() {
        return source;
    }

    public void setSource(FieldSource source) {
        this.source = source;
    }

    public IncomingEmail getExtractedFromEmail() {
        return extractedFromEmail;
    }

    public void setExtractedFromEmail(IncomingEmail extractedFromEmail) {
        this.extractedFromEmail = extractedFromEmail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}