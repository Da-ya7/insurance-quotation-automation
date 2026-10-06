package com.insurance.quotation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_logs")
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne
    @JoinColumn(name = "quotation_request_id")
    private QuotationRequest quotationRequest;

    @ManyToOne
    @JoinColumn(name = "quotation_id")
    private Quotation quotation;

    @ManyToOne
    @JoinColumn(name = "incoming_email_id")
    private IncomingEmail incomingEmail;

    @Column(name = "direction", nullable = false)
    private String direction;

    @Column(name = "email_type", nullable = false)
    private String emailType;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;


    // Getters and Setters

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

    public QuotationRequest getQuotationRequest() {
        return quotationRequest;
    }

    public void setQuotationRequest(QuotationRequest quotationRequest) {
        this.quotationRequest = quotationRequest;
    }

    public Quotation getQuotation() {
        return quotation;
    }

    public void setQuotation(Quotation quotation) {
        this.quotation = quotation;
    }

    public IncomingEmail getIncomingEmail() {
        return incomingEmail;
    }

    public void setIncomingEmail(IncomingEmail incomingEmail) {
        this.incomingEmail = incomingEmail;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getEmailType() {
        return emailType;
    }

    public void setEmailType(String emailType) {
        this.emailType = emailType;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}




// package com.insurance.quotation.entity;

// import com.insurance.quotation.entity.enums.EmailDirection;
// import com.insurance.quotation.entity.enums.EmailStatus;
// import com.insurance.quotation.entity.enums.EmailType;
// import jakarta.persistence.*;

// import java.time.LocalDateTime;

// @Entity
// @Table(
//     name = "email_logs",
//     indexes = {
//         @Index(
//             name = "idx_el_dedup",
//             columnList = "quotation_request_id,email_type,status"
//         )
//     }
// )
// public class EmailLog {

//     @Id
//     @GeneratedValue(strategy = GenerationType.IDENTITY)
//     private Long id;

//     @ManyToOne(fetch = FetchType.LAZY, optional = false)
//     @JoinColumn(name = "client_id", nullable = false)
//     private Client client;

//     @ManyToOne(fetch = FetchType.LAZY)
//     @JoinColumn(name = "quotation_request_id")
//     private QuotationRequest quotationRequest;

//     @ManyToOne(fetch = FetchType.LAZY)
//     @JoinColumn(name = "quotation_id")
//     private Quotation quotation;

//     @ManyToOne(fetch = FetchType.LAZY)
//     @JoinColumn(name = "incoming_email_id")
//     private IncomingEmail incomingEmail;

//     @Enumerated(EnumType.STRING)
//     @Column(name = "direction", nullable = false)
//     private EmailDirection direction;

//     @Enumerated(EnumType.STRING)
//     @Column(name = "email_type", nullable = false)
//     private EmailType emailType;

//     @Column(name = "subject", length = 255)
//     private String subject;

//     @Enumerated(EnumType.STRING)
//     @Column(name = "status", nullable = false)
//     private EmailStatus status;

//     @Column(name = "occurred_at", nullable = false)
//     private LocalDateTime occurredAt;

//     @PrePersist
//     protected void onCreate() {
//         if (occurredAt == null) {
//             occurredAt = LocalDateTime.now();
//         }
//     }

//     public Long getId() {
//         return id;
//     }

//     public void setId(Long id) {
//         this.id = id;
//     }

//     public Client getClient() {
//         return client;
//     }

//     public void setClient(Client client) {
//         this.client = client;
//     }

//     public QuotationRequest getQuotationRequest() {
//         return quotationRequest;
//     }

//     public void setQuotationRequest(QuotationRequest quotationRequest) {
//         this.quotationRequest = quotationRequest;
//     }

//     public Quotation getQuotation() {
//         return quotation;
//     }

//     public void setQuotation(Quotation quotation) {
//         this.quotation = quotation;
//     }

//     public IncomingEmail getIncomingEmail() {
//         return incomingEmail;
//     }

//     public void setIncomingEmail(IncomingEmail incomingEmail) {
//         this.incomingEmail = incomingEmail;
//     }

//     public EmailDirection getDirection() {
//         return direction;
//     }

//     public void setDirection(EmailDirection direction) {
//         this.direction = direction;
//     }

//     public EmailType getEmailType() {
//         return emailType;
//     }

//     public void setEmailType(EmailType emailType) {
//         this.emailType = emailType;
//     }

//     public String getSubject() {
//         return subject;
//     }

//     public void setSubject(String subject) {
//         this.subject = subject;
//     }

//     public EmailStatus getStatus() {
//         return status;
//     }

//     public void setStatus(EmailStatus status) {
//         this.status = status;
//     }

//     public LocalDateTime getOccurredAt() {
//         return occurredAt;
//     }

//     public void setOccurredAt(LocalDateTime occurredAt) {
//         this.occurredAt = occurredAt;
//     }
// }