
package com.insurance.quotation.dto.request;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body sent by the public quotation form.
 * Only the main applicant's details are collected.
 * The travellers field stores the total number of people in the group.
 */
public class QuotationFormRequest {

    @Valid
    @NotNull(message = "Applicant details are required")
    private TravellerForm applicant;

    @Valid
    @Size(max = 19, message = "Maximum 20 travellers are allowed")
    private List<TravellerForm> additionalTravellers = new ArrayList<>();

    // Total number of travellers in the group (1–20).
    // Only the applicant's personal details are collected.
    @NotNull(message = "Number of travellers is required")
    @Min(value = 1, message = "At least 1 traveller is required")
    @Max(value = 20, message = "Maximum 20 travellers are allowed")
    private Integer travellers = 1;

    // Issued by the backend after successful phone OTP verification.
    private String verificationToken;

    @NotBlank(message = "Departure country is required")
    @Size(max = 100)
    private String departureCountry;

    @NotBlank(message = "Destination country is required")
    @Size(max = 100)
    private String destinationCountry;

    @NotNull(message = "Travel start date is required")
    private LocalDate travelStartDate;

    @NotNull(message = "Travel end date is required")
    private LocalDate travelEndDate;

    @NotBlank(message = "Purpose of travel is required")
    @Size(max = 60)
    private String purposeOfTravel;

    public TravellerForm getApplicant() {
        return applicant;
    }

    public void setApplicant(TravellerForm applicant) {
        this.applicant = applicant;
    }

    public List<TravellerForm> getAdditionalTravellers() {
        return additionalTravellers;
    }

    public void setAdditionalTravellers(List<TravellerForm> travellers) {
        this.additionalTravellers = travellers == null
                ? new ArrayList<>()
                : travellers;
    }

    public Integer getTravellers() {
        return travellers;
    }

    public void setTravellers(Integer travellers) {
        this.travellers = travellers;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public String getDepartureCountry() {
        return departureCountry;
    }

    public void setDepartureCountry(String departureCountry) {
        this.departureCountry = departureCountry;
    }

    public String getDestinationCountry() {
        return destinationCountry;
    }

    public void setDestinationCountry(String destinationCountry) {
        this.destinationCountry = destinationCountry;
    }

    public LocalDate getTravelStartDate() {
        return travelStartDate;
    }

    public void setTravelStartDate(LocalDate travelStartDate) {
        this.travelStartDate = travelStartDate;
    }

    public LocalDate getTravelEndDate() {
        return travelEndDate;
    }

    public void setTravelEndDate(LocalDate travelEndDate) {
        this.travelEndDate = travelEndDate;
    }

    public String getPurposeOfTravel() {
        return purposeOfTravel;
    }

    public void setPurposeOfTravel(String purposeOfTravel) {
        this.purposeOfTravel = purposeOfTravel;
    }
}
