package com.insurance.quotation.entity;

import com.insurance.quotation.entity.enums.RequestStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "quotation_requests")
public class QuotationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =========================
    // CLIENT
    // =========================

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;


    // =========================
    // POLICY TYPE
    // =========================

    @Column(name = "policy_type", length = 60)
    private String policyType;


    // =========================
    // STATUS
    // =========================

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status = RequestStatus.EMAIL_RECEIVED;


    // =========================
    // ASSIGNED USER
    // =========================

    @ManyToOne
    @JoinColumn(name = "assigned_to")
    private User assignedTo;


    // =========================
    // CREATED AT
    // =========================

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


    // =========================
    // UPDATED AT
    // =========================

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    // =========================
    // REQUEST FIELD VALUES
    // =========================

    @OneToMany(mappedBy = "quotationRequest")
    private List<RequestFieldValue> fieldValues;


    // =========================
    // MISSING FIELDS
    // =========================

    @OneToMany(mappedBy = "quotationRequest")
    private List<MissingField> missingFields;


    // =========================
    // QUOTATION
    // =========================

    @OneToOne(mappedBy = "quotationRequest")
    private Quotation quotation;


    // =========================
    // DATE/TIME METHODS
    // =========================

    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;
    }


    @PreUpdate
    protected void onUpdate() {

        updatedAt = LocalDateTime.now();
    }


    // =========================
    // GETTERS AND SETTERS
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
