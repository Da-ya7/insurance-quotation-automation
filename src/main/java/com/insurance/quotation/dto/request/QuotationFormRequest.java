package com.insurance.quotation.dto.request;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body sent by the public quotation form.
 * The main applicant is traveller #1; additionalTravellers are the rest.
 */
public class QuotationFormRequest {

    @Valid
    @NotNull(message = "Applicant details are required")
    private TravellerForm applicant;

    @Valid
    @Size(max = 19, message = "Maximum 20 travellers are allowed")
    private List<TravellerForm> additionalTravellers = new ArrayList<>();

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

    public TravellerForm getApplicant() { return applicant; }
    public void setApplicant(TravellerForm applicant) { this.applicant = applicant; }
    public List<TravellerForm> getAdditionalTravellers() { return additionalTravellers; }
    public void setAdditionalTravellers(List<TravellerForm> t) { this.additionalTravellers = t == null ? new ArrayList<>() : t; }
    public String getDepartureCountry() { return departureCountry; }
    public void setDepartureCountry(String v) { this.departureCountry = v; }
    public String getDestinationCountry() { return destinationCountry; }
    public void setDestinationCountry(String v) { this.destinationCountry = v; }
    public LocalDate getTravelStartDate() { return travelStartDate; }
    public void setTravelStartDate(LocalDate v) { this.travelStartDate = v; }
    public LocalDate getTravelEndDate() { return travelEndDate; }
    public void setTravelEndDate(LocalDate v) { this.travelEndDate = v; }
    public String getPurposeOfTravel() { return purposeOfTravel; }
    public void setPurposeOfTravel(String v) { this.purposeOfTravel = v; }
}
