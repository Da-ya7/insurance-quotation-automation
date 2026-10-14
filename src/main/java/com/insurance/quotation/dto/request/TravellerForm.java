package com.insurance.quotation.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class TravellerForm {

    @NotBlank(message = "Traveller name is required")
    @Size(max = 150, message = "Name is too long")
    private String name;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Passport number is required")
    @Size(max = 20, message = "Passport number is too long")
    private String passportNumber;

    @Size(max = 30)
    private String gender;

    @NotBlank(message = "Nationality is required")
    @Size(max = 100)
    private String nationality;

    @NotBlank(message = "Country of residence is required")
    @Size(max = 100)
    private String countryOfResidence;

    @NotBlank(message = "Email is required")
    @Email(message = "Email is not valid")
    @Size(max = 150)
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^\\+?[0-9 ()-]{10,20}$", message = "Phone must have 10-15 digits")
    private String phone;

    private String preExistingMedicalConditions;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getPassportNumber() { return passportNumber; }
    public void setPassportNumber(String passportNumber) { this.passportNumber = passportNumber; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    public String getCountryOfResidence() { return countryOfResidence; }
    public void setCountryOfResidence(String countryOfResidence) { this.countryOfResidence = countryOfResidence; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPreExistingMedicalConditions() { return preExistingMedicalConditions; }
    public void setPreExistingMedicalConditions(String v) { this.preExistingMedicalConditions = v; }
}
