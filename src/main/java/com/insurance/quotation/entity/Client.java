package com.insurance.quotation.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.*;

/**
 * Client master record. One row per PERSON: several people may share an email
 * address, so email is NOT unique. clientCode is the unique internal Client ID.
 */
@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_code", nullable = false, unique = true, length = 30)
    private String clientCode;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 30)
    private String gender;

    @Column(name = "nationality", length = 100)
    private String nationality;

    @Column(name = "country_of_residence", length = 100)
    private String countryOfResidence;

    // Identity document. The number itself is stored encrypted; only the
    // keyed hash (for matching) and the last 4 characters (for display) are
    // ever used by the application.
    @Column(name = "identity_country", length = 100)
    private String identityCountry;

    @Column(name = "identity_type", length = 40)
    private String identityType;

    @Column(name = "identity_number_enc", length = 400)
    private String identityNumberEnc;

    @Column(name = "identity_number_hash", length = 64)
    private String identityNumberHash;

    @Column(name = "identity_number_last4", length = 8)
    private String identityNumberLast4;

    @Column(name = "phone_verified_at")
    private LocalDateTime phoneVerifiedAt;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "client")
    private List<QuotationRequest> quotationRequests;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClientCode() { return clientCode; }
    public void setClientCode(String clientCode) { this.clientCode = clientCode; }
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
    public String getIdentityNumberEnc() { return identityNumberEnc; }
    public void setIdentityNumberEnc(String v) { this.identityNumberEnc = v; }
    public String getIdentityNumberHash() { return identityNumberHash; }
    public void setIdentityNumberHash(String v) { this.identityNumberHash = v; }
    public String getIdentityNumberLast4() { return identityNumberLast4; }
    public void setIdentityNumberLast4(String v) { this.identityNumberLast4 = v; }
    public LocalDateTime getPhoneVerifiedAt() { return phoneVerifiedAt; }
    public void setPhoneVerifiedAt(LocalDateTime v) { this.phoneVerifiedAt = v; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<QuotationRequest> getQuotationRequests() { return quotationRequests; }
    public void setQuotationRequests(List<QuotationRequest> v) { this.quotationRequests = v; }
}
