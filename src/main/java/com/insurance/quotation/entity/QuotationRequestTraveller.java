package com.insurance.quotation.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "quotation_request_travellers",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_request_traveller_number",
        columnNames = {"quotation_request_id", "traveller_number"}
    )
)
public class QuotationRequestTraveller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_request_id", nullable = false)
    private QuotationRequest quotationRequest;

    @Column(name = "traveller_number", nullable = false)
    private Integer travellerNumber;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "age_at_submission", nullable = false)
    private Integer ageAtSubmission;

    @Column(name = "passport_number", nullable = false, length = 20)
    private String passportNumber;

    @Column(length = 30)
    private String gender;

    @Column(nullable = false, length = 100)
    private String nationality;

    @Column(name = "country_of_residence", nullable = false, length = 100)
    private String countryOfResidence;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(
        name = "pre_existing_medical_conditions",
        columnDefinition = "TEXT"
    )
    private String preExistingMedicalConditions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

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

    public Integer getTravellerNumber() {
        return travellerNumber;
    }

    public void setTravellerNumber(Integer travellerNumber) {
        this.travellerNumber = travellerNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Integer getAgeAtSubmission() {
        return ageAtSubmission;
    }

    public void setAgeAtSubmission(Integer ageAtSubmission) {
        this.ageAtSubmission = ageAtSubmission;
    }

    public String getPassportNumber() {
        return passportNumber;
    }

    public void setPassportNumber(String passportNumber) {
        this.passportNumber = passportNumber;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getCountryOfResidence() {
        return countryOfResidence;
    }

    public void setCountryOfResidence(String countryOfResidence) {
        this.countryOfResidence = countryOfResidence;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPreExistingMedicalConditions() {
        return preExistingMedicalConditions;
    }

    public void setPreExistingMedicalConditions(
            String preExistingMedicalConditions) {
        this.preExistingMedicalConditions =
                preExistingMedicalConditions;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}