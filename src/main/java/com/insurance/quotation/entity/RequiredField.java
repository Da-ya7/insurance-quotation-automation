package com.insurance.quotation.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "required_fields")
public class RequiredField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fieldName;

    private String fieldType;

    private Boolean required;
}