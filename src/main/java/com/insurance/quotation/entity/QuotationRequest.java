
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

    // Customer who submitted the quotation request
    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    // Insurance policy type
    @Column(name = "policy_type", length = 60)
    private String policyType;

    // Purpose of travel from the client quotation form
    @Column(name = "purpose_of_travel", length = 60)
    private String purposeOfTravel;

    // Current processing status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status = RequestStatus.EMAIL_RECEIVED;

    // Staff member assigned to the request
    @ManyToOne
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    // Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Configured request field values
    @OneToMany(mappedBy = "quotationRequest")
    private List<RequestFieldValue> fieldValues;

    // Missing or invalid required fields
    @OneToMany(mappedBy = "quotationRequest")
    private List<MissingField> missingFields;

    // All travellers associated with this request
    @OneToMany(mappedBy = "quotationRequest")
    private List<QuotationRequestTraveller> travellers;

    // Generated quotation
    @OneToOne(mappedBy = "quotationRequest")
    private Quotation quotation;

    // Getters and setters

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

    public String getPurposeOfTravel() {
        return purposeOfTravel;
    }

    public void setPurposeOfTravel(String purposeOfTravel) {
        this.purposeOfTravel = purposeOfTravel;
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

    public List<QuotationRequestTraveller> getTravellers() {
        return travellers;
    }

    public void setTravellers(
            List<QuotationRequestTraveller> travellers) {
        this.travellers = travellers;
    }

    public Quotation getQuotation() {
        return quotation;
    }

    public void setQuotation(Quotation quotation) {
        this.quotation = quotation;
    }
}
