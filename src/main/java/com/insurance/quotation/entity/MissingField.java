package com.insurance.quotation.entity;
import java.time.LocalDateTime;

import com.insurance.quotation.entity.enums.MissingFieldStatus;

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

@Entity
@Table(name = "missing_fields")
public class MissingField {

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

    // status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MissingFieldStatus status = MissingFieldStatus.OPEN;

    // created_at
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // resolved_at
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;


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

    public MissingFieldStatus getStatus() {
        return status;
    }

    public void setStatus(MissingFieldStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}