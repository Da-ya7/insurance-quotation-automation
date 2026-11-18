
package com.insurance.quotation.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class TravellerForm {

    // Applicant personal details
    // Name remains mandatory for the current client workflow.
    @NotBlank(message = "Full name is required.")
    @Size(max = 150, message = "Full name cannot exceed 150 characters.")
    private String name;

    // Date of birth is controlled by Master Data.
    // If supplied, it must be a date in the past.
    @Past(message = "Date of birth must be in the past.")
    private LocalDate dateOfBirth;

    // Controlled by Master Data.
    private String gender;

    // Controlled by Master Data.
    private String nationality;

    // Controlled by Master Data.
    private String countryOfResidence;

    // Passport details are controlled by Master Data.
    private String passportNumber;

    private String passportCountry;

    // Government identity document details.
    // Identity document capture remains optional at form submission.
    private String identityCountry;
    private String identityType;
    private String identityNumber;

    // Contact details.
    // Email remains mandatory for the current submission workflow.
    @Email(message = "Please enter a valid email address.")
    @NotBlank(message = "Email is required.")
    @Size(max = 150)
    private String email;

    // Phone remains mandatory because OTP verification depends on it.
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
