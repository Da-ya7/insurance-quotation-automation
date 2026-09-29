package com.insurance.quotation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_logs")
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String recipientEmail;

    private String subject;

    private String emailType;

    private String status;

    private LocalDateTime sentAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}