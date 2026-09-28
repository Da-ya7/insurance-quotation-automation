package com.insurance.quotation.dto.response;

public class RequestDetailResponse {

    private Long id;

    private Long clientId;
    private String clientName;
    private String clientEmail;
    private String clientPhone;

    private String destination;
    private String departureDate;
    private String returnDate;
    private Integer travellerCount;
    private String status;

    public RequestDetailResponse(
            Long id,
            Long clientId,
            String clientName,
            String clientEmail,
            String clientPhone,
            String destination,
            String departureDate,
            String returnDate,
            Integer travellerCount,
            String status) {

        this.id = id;
        this.clientId = clientId;
        this.clientName = clientName;
        this.clientEmail = clientEmail;
        this.clientPhone = clientPhone;
        this.destination = destination;
        this.departureDate = departureDate;
        this.returnDate = returnDate;
        this.travellerCount = travellerCount;
        this.status = status;
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

    public String getDestination() {
        return destination;
    }

    public String getDepartureDate() {
        return departureDate;
    }

    public String getReturnDate() {
        return returnDate;
    }

    public Integer getTravellerCount() {
        return travellerCount;
    }

    public String getStatus() {
        return status;
    }
}