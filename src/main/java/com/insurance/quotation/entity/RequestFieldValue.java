package com.insurance.quotation.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "request_field_values")
public class RequestFieldValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "quotation_request_id", nullable = false)
    private QuotationRequest quotationRequest;

    @ManyToOne
    @JoinColumn(name = "required_field_id", nullable = false)
    private RequiredField requiredField;

    @Column(columnDefinition = "TEXT")
    private String fieldValue;
}