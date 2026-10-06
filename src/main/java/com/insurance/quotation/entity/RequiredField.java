package com.insurance.quotation.entity;
import com.insurance.quotation.entity.enums.FieldDataType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "required_fields")
public class RequiredField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_name", nullable = false, unique = true, length = 80)
    private String fieldName;

    @Column(name = "display_label", nullable = false, length = 120)
    private String displayLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private FieldDataType dataType;

    @Column(name = "policy_type", length = 60)
    private String policyType;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;


    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public void setDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public FieldDataType getDataType() {
        return dataType;
    }

   

    public void setDataType(FieldDataType dataType) {
    this.dataType = dataType;
    }       

    public String getPolicyType() {
        return policyType;
    }

    public void setPolicyType(String policyType) {
        this.policyType = policyType;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
