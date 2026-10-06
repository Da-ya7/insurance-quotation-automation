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

    @OneToOne
    @JoinColumn(name = "quotation_request_id", nullable = false, unique = true)
    private QuotationRequest quotationRequest;

    @Column(name = "quotation_number", nullable = false, unique = true, length = 30)
    private String quotationNumber;

    @Column(name = "premium_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 6)
    private String currency = "INR";

    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "pdf_path", length = 255)
    private String pdfPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;


    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public QuotationRequest getQuotationRequest() {
        return quotationRequest;
    }

    public void setQuotationRequest(QuotationRequest quotationRequest) {
        this.quotationRequest = quotationRequest;
    }

    public String getQuotationNumber() {
        return quotationNumber;
    }

    public void setQuotationNumber(String quotationNumber) {
        this.quotationNumber = quotationNumber;
    }

    public BigDecimal getPremiumAmount() {
        return premiumAmount;
    }

    public void setPremiumAmount(BigDecimal premiumAmount) {
        this.premiumAmount = premiumAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPdfPath() {
        return pdfPath;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}

// package com.insurance.quotation.entity;

// import jakarta.persistence.*;

// import java.math.BigDecimal;
// import java.time.LocalDateTime;
// import java.util.ArrayList;
// import java.util.List;

// @Entity
// @Table(
//     name = "quotations",
//     uniqueConstraints = {
//         @UniqueConstraint(
//             name = "uq_quotation_request",
//             columnNames = "quotation_request_id"
//         ),
//         @UniqueConstraint(
//             name = "uq_quotation_number",
//             columnNames = "quotation_number"
//         )
//     }
// )
// public class Quotation {

//     @Id
//     @GeneratedValue(strategy = GenerationType.IDENTITY)
//     private Long id;

//     @OneToOne(fetch = FetchType.LAZY, optional = false)
//     @JoinColumn(
//         name = "quotation_request_id",
//         nullable = false,
//         unique = true
//     )
//     private QuotationRequest quotationRequest;

//     @Column(name = "quotation_number", nullable = false, length = 30)
//     private String quotationNumber;

//     @Column(name = "premium_amount", nullable = false, precision = 12, scale = 2)
//     private BigDecimal premiumAmount;

//     @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
//     private BigDecimal taxAmount = BigDecimal.ZERO;

//     @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
//     private BigDecimal totalAmount;

//     @Column(name = "currency", nullable = false, length = 6)
//     private String currency = "INR";

//     @Column(name = "status", nullable = false)
//     private String status = "DRAFT";

//     @Column(name = "pdf_path", length = 255)
//     private String pdfPath;

//     @Column(name = "created_at", nullable = false, updatable = false)
//     private LocalDateTime createdAt;

//     @Column(name = "sent_at")
//     private LocalDateTime sentAt;

//     @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL)
//     private List<QuotationItem> items = new ArrayList<>();

//     @PrePersist
//     protected void onCreate() {
//         createdAt = LocalDateTime.now();

//         if (taxAmount == null) {
//             taxAmount = BigDecimal.ZERO;
//         }

//         if (currency == null) {
//             currency = "INR";
//         }

//         if (status == null) {
//             status = "DRAFT";
//         }
//     }

//     public Long getId() {
//         return id;
//     }

//     public void setId(Long id) {
//         this.id = id;
//     }

//     public QuotationRequest getQuotationRequest() {
//         return quotationRequest;
//     }

//     public void setQuotationRequest(QuotationRequest quotationRequest) {
//         this.quotationRequest = quotationRequest;
//     }

//     public String getQuotationNumber() {
//         return quotationNumber;
//     }

//     public void setQuotationNumber(String quotationNumber) {
//         this.quotationNumber = quotationNumber;
//     }

//     public BigDecimal getPremiumAmount() {
//         return premiumAmount;
//     }

//     public void setPremiumAmount(BigDecimal premiumAmount) {
//         this.premiumAmount = premiumAmount;
//     }

//     public BigDecimal getTaxAmount() {
//         return taxAmount;
//     }

//     public void setTaxAmount(BigDecimal taxAmount) {
//         this.taxAmount = taxAmount;
//     }

//     public BigDecimal getTotalAmount() {
//         return totalAmount;
//     }

//     public void setTotalAmount(BigDecimal totalAmount) {
//         this.totalAmount = totalAmount;
//     }

//     public String getCurrency() {
//         return currency;
//     }

//     public void setCurrency(String currency) {
//         this.currency = currency;
//     }

//     public String getStatus() {
//         return status;
//     }

//     public void setStatus(String status) {
//         this.status = status;
//     }

//     public String getPdfPath() {
//         return pdfPath;
//     }

//     public void setPdfPath(String pdfPath) {
//         this.pdfPath = pdfPath;
//     }

//     public LocalDateTime getCreatedAt() {
//         return createdAt;
//     }

//     public void setCreatedAt(LocalDateTime createdAt) {
//         this.createdAt = createdAt;
//     }

//     public LocalDateTime getSentAt() {
//         return sentAt;
//     }

//     public void setSentAt(LocalDateTime sentAt) {
//         this.sentAt = sentAt;
//     }

//     public List<QuotationItem> getItems() {
//         return items;
//     }

//     public void setItems(List<QuotationItem> items) {
//         this.items = items;
//     }
// }