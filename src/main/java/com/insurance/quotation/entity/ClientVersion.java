package com.insurance.quotation.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;

/** One historical snapshot of a client record. Rows are never updated. */
@Entity
@Table(name = "client_versions")
public class ClientVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    private String name;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    private String gender;
    private String nationality;

    @Column(name = "country_of_residence")
    private String countryOfResidence;

    @Column(name = "identity_country")
    private String identityCountry;

    @Column(name = "identity_type")
    private String identityType;

    @Column(name = "identity_number_last4")
    private String identityNumberLast4;

    @Column(name = "change_source", nullable = false)
    private String changeSource;

    @Column(name = "changed_fields")
    private String changedFields;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "otp_verified", nullable = false)
    private boolean otpVerified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    public String getCountryOfResidence() { return countryOfResidence; }
    public void setCountryOfResidence(String v) { this.countryOfResidence = v; }
    public String getIdentityCountry() { return identityCountry; }
    public void setIdentityCountry(String v) { this.identityCountry = v; }
    public String getIdentityType() { return identityType; }
    public void setIdentityType(String v) { this.identityType = v; }
    public String getIdentityNumberLast4() { return identityNumberLast4; }
    public void setIdentityNumberLast4(String v) { this.identityNumberLast4 = v; }
    public String getChangeSource() { return changeSource; }
    public void setChangeSource(String changeSource) { this.changeSource = changeSource; }
    public String getChangedFields() { return changedFields; }
    public void setChangedFields(String changedFields) { this.changedFields = changedFields; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public boolean isOtpVerified() { return otpVerified; }
    public void setOtpVerified(boolean otpVerified) { this.otpVerified = otpVerified; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
