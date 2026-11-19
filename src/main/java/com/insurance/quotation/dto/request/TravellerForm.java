
package com.insurance.quotation.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class TravellerForm {

    // Applicant personal details
    @NotBlank(message = "Full name is required.")
    @Size(max = 150, message = "Full name cannot exceed 150 characters.")
    private String name;

    // Optional date of birth
    @Past(message = "Date of birth must be in the past.")
    private LocalDate dateOfBirth;

    // Passport details are controlled by Master Data.
    // They are not hardcoded as mandatory here.
    private String passportNumber;
    private String passportCountry;

    // Optional PAN number
    @Pattern(
        regexp = "(?i)^[A-Z]{5}[0-9]{4}[A-Z]$",
        message = "Enter a valid PAN number"
    )
    private String panNumber;

    // Optional Aadhaar number
    @Pattern(
        regexp = "^[2-9][0-9]{11}$",
        message = "Enter a valid 12-digit Aadhaar number"
    )
    private String aadhaarNumber;

    @Size(max = 30)
    private String gender;

    // Controlled by Master Data.
    private String nationality;
    private String countryOfResidence;

    // Government identity document details.
    // Identity document capture remains optional at form submission.
    private String identityCountry;
    private String identityType;
    private String identityNumber;

    // Contact details.
    @Email(message = "Please enter a valid email address.")
    @NotBlank(message = "Email is required.")
    @Size(max = 150)
    private String email;

    // Phone is mandatory because OTP verification depends on it.
    @NotBlank(message = "Phone number is required.")
    @Pattern(
        regexp = "^[0-9+()\\-\\s]{7,20}$",
        message = "Please enter a valid phone number."
    )
    private String phone;

    // Controlled by Master Data if configured as a form field.
    private String preExistingMedicalConditions;

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

    public String getPassportNumber() {
        return passportNumber;
    }

    public void setPassportNumber(String passportNumber) {
        this.passportNumber = passportNumber;
    }

    public String getPassportCountry() {
        return passportCountry;
    }

    public void setPassportCountry(String passportCountry) {
        this.passportCountry = passportCountry;
    }

    public String getPanNumber() {
        return panNumber;
    }

    public void setPanNumber(String panNumber) {
        this.panNumber = panNumber == null || panNumber.isBlank()
                ? null : panNumber.trim().toUpperCase();
    }

    public String getAadhaarNumber() {
        return aadhaarNumber;
    }

    public void setAadhaarNumber(String aadhaarNumber) {
        this.aadhaarNumber = aadhaarNumber == null || aadhaarNumber.isBlank()
                ? null : aadhaarNumber.trim();
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

    public String getIdentityCountry() {
        return identityCountry;
    }

    public void setIdentityCountry(String identityCountry) {
        this.identityCountry = identityCountry;
    }

    public String getIdentityType() {
        return identityType;
    }

    public void setIdentityType(String identityType) {
        this.identityType = identityType;
    }

    public String getIdentityNumber() {
        return identityNumber;
    }

    public void setIdentityNumber(String identityNumber) {
        this.identityNumber = identityNumber;
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
        this.preExistingMedicalConditions = preExistingMedicalConditions;
    }
}
