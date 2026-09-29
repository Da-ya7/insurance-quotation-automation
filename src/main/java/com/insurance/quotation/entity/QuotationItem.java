package com.insurance.quotation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "quotation_items")
public class QuotationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    private String itemName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;
}