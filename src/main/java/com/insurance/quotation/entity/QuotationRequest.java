package com.insurance.quotation.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.insurance.quotation.entity.enums.RequestStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quotation_requests")
public class QuotationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // client_id
    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    // policy_type
    @Column(name = "policy_type", length = 60)
    private String policyType;

    // status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status = RequestStatus.EMAIL_RECEIVED;

    // assigned_to
    @ManyToOne
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    // created_at
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // updated_at
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationship with RequestFieldValue
    @OneToMany(mappedBy = "quotationRequest")
    private List<RequestFieldValue> fieldValues;

    // Relationship with MissingField
    @OneToMany(mappedBy = "quotationRequest")
    private List<MissingField> missingFields;

    // Relationship with Quotation
    @OneToOne(mappedBy = "quotationRequest")
    private Quotation quotation;


    // =========================
    // Getters and Setters
    // =========================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public String getPolicyType() {
        return policyType;
    }

    public void setPolicyType(String policyType) {
        this.policyType = policyType;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(User assignedTo) {
        this.assignedTo = assignedTo;
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

    public List<RequestFieldValue> getFieldValues() {
        return fieldValues;
    }

    public void setFieldValues(List<RequestFieldValue> fieldValues) {
        this.fieldValues = fieldValues;
    }

    public List<MissingField> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<MissingField> missingFields) {
        this.missingFields = missingFields;
    }

    public Quotation getQuotation() {
        return quotation;
    }

    public void setQuotation(Quotation quotation) {
        this.quotation = quotation;
    }
}