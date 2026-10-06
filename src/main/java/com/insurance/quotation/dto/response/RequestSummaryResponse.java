package com.insurance.quotation.dto.response;

import java.time.LocalDateTime;

import com.insurance.quotation.entity.enums.RequestStatus;

public class RequestSummaryResponse {

    private Long id;
    private Long clientId;
    private String policyType;
    private RequestStatus status;
    private Long assignedToId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RequestSummaryResponse(
            Long id,
            Long clientId,
            String policyType,
            RequestStatus status,
            Long assignedToId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        this.id = id;
        this.clientId = clientId;
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