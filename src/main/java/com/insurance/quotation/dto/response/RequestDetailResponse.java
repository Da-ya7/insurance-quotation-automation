package com.insurance.quotation.dto.response;

import java.time.LocalDateTime;

import com.insurance.quotation.entity.enums.RequestStatus;

public class RequestDetailResponse {

    private Long id;
    private Long clientId;
    private String clientName;
    private String clientEmail;
    private String clientPhone;
    private String policyType;
    private RequestStatus status;
    private Long assignedToId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RequestDetailResponse(
            Long id,
            Long clientId,
            String clientName,
            String clientEmail,
            String clientPhone,
            String policyType,
            RequestStatus status,
            Long assignedToId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        this.id = id;
        this.clientId = clientId;
        this.clientName = clientName;
        this.clientEmail = clientEmail;
        this.clientPhone = clientPhone;
        this.policyType = policyType;
        this.status = status;
        this.assignedToId = assignedToId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public String getClientEmail() {
        return clientEmail;
    }

    public String getClientPhone() {
        return clientPhone;
    }

    public String getPolicyType() {
        return policyType;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public Long getAssignedToId() {
        return assignedToId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}