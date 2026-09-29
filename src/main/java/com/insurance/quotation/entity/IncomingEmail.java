package com.insurance.quotation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "incoming_emails")
public class IncomingEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String messageId;

    private String senderEmail;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    private String emailType;

    private String status;

    private LocalDateTime receivedAt;
}