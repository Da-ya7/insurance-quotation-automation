
package com.insurance.quotation.dto.request;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body sent by the public quotation form.
 *
 * The applicant contains the main customer's details.
 * Additional configured insurance fields are received through fields.
 * The travellers property stores the total number of people in the group.
 */
public class QuotationFormRequest {

    @Valid
    @NotNull(message = "Applicant details are required")
    private TravellerForm applicant;

    /**
     * Dynamic values submitted by the public form.
     * Keys correspond to RequiredField.fieldName in Master Data.
     */
    private Map<String, String> fields = new LinkedHashMap<>();

    @Valid
    @Size(max = 19, message = "Maximum 20 travellers are allowed")
    private List<TravellerForm> additionalTravellers = new ArrayList<>();

    // Total number of travellers in the group (1–20).
    @NotNull(message = "Number of travellers is required")
    @Min(value = 1, message = "At least 1 traveller is required")
    @Max(value = 20, message = "Maximum 20 travellers are allowed")
    private Integer travellers = 1;

    // Issued by the backend after successful phone OTP verification.
    private String verificationToken;

    // Configurable travel fields.
    // Mandatory validation is controlled by Master Data.
    @Size(max = 100)
    private String departureCountry;

    @Size(max = 100)
    private String destinationCountry;

    private LocalDate travelStartDate;

    private LocalDate travelEndDate;

    @Size(max = 60)
    private String purposeOfTravel;

    public TravellerForm getApplicant() {
        return applicant;
    }

    public void setApplicant(TravellerForm applicant) {
        this.applicant = applicant;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public void setFields(Map<String, String> fields) {
        this.fields = fields == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(fields);
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
