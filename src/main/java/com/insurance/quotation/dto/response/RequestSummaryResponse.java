package com.insurance.quotation.dto.response;

public class RequestSummaryResponse {

    private Long id;
    private Long clientId;
    private String destination;
    private String departureDate;
    private String returnDate;
    private Integer travellerCount;
    private String status;

    public RequestSummaryResponse(
            Long id,
            Long clientId,
            String destination,
            String departureDate,
            String returnDate,
            Integer travellerCount,
            String status) {

        this.id = id;
        this.clientId = clientId;
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