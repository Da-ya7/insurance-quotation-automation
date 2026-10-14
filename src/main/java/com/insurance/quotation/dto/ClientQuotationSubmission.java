
package com.insurance.quotation.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClientQuotationSubmission(

        @NotBlank
        @Size(max = 150)
        String name,

        @NotNull
        LocalDate dateOfBirth,

        Integer age,

        @NotBlank
        @Size(max = 20)
        String passportNumber,

        String gender,

        @NotBlank
        @Size(max = 100)
        String nationality,

        @NotBlank
        @Size(max = 100)
        String countryOfResidence,

        @NotBlank
        @Email
        @Size(max = 150)
        String email,

        @NotBlank
        @Size(max = 20)
        String phone,

        String preExistingMedicalConditions,

        @NotBlank
        @Size(max = 100)
        String departureCountry,

        @NotBlank
        @Size(max = 100)
        String destinationCountry,

        @NotNull
        LocalDate travelStartDate,

        @NotNull
        LocalDate travelEndDate,

        @NotBlank
        @Size(max = 60)
        String purposeOfTravel,

        Integer travellers,

        @Valid
        @Size(max = 19)
        List<TravellerSubmission> additionalTravellers

) {

    public record TravellerSubmission(

            @NotBlank
            @Size(max = 150)
            String name,

            @NotNull
            LocalDate dateOfBirth,

            Integer age,

            @NotBlank
            @Size(max = 20)
            String passportNumber,

            String gender,

            @NotBlank
            @Size(max = 100)
            String nationality,

            @NotBlank
            @Size(max = 100)
            String countryOfResidence,

            @NotBlank
            @Email
            @Size(max = 150)
            String email,

            @NotBlank
            @Size(max = 20)
            String phone,

            String preExistingMedicalConditions
    ) {
    }
}
