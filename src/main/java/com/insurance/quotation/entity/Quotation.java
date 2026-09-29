package com.insurance.quotation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "quotations")
public class Quotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String quotationNumber;

    @OneToOne
    @JoinColumn(name = "quotation_request_id", nullable = false)
    private QuotationRequest quotationRequest;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal premium;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;
}