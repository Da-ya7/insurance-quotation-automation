package com.insurance.quotation.entity;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "quotation_requests")
public class QuotationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    private String destination;

    private String departureDate;

    private String returnDate;

    private Integer travellerCount;

    private String status;

    @OneToMany(mappedBy = "quotationRequest")
    private List<RequestFieldValue> fieldValues;

    @OneToMany(mappedBy = "quotationRequest")
    private List<MissingField> missingFields;

    @OneToOne(mappedBy = "quotationRequest")
    private Quotation quotation;

    public Long getId() {
        return id;
    }

    public Client getClient() {
        return client;
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

    public List<RequestFieldValue> getFieldValues() {
        return fieldValues;
    }

    public List<MissingField> getMissingFields() {
        return missingFields;
    }

    public Quotation getQuotation() {
        return quotation;
    }
}