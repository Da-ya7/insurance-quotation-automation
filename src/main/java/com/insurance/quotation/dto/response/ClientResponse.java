package com.insurance.quotation.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.service.client.IdentityProtectionService;

/**
 * Client response DTO.
 *
 * Used by:
 * - Client Master screens.
 * - Mobile number search.
 * - Existing client selection.
 * - Autofilling personal details in the quotation form.
 *
 * Security:
 * - The complete identity number is never returned.
 * - Only the masked identity number is provided.
 * - The identity number remains encrypted in the database.
 */
public record ClientResponse(
        Long id,
        String clientCode,
        String name,
        String email,
        String phone,
        boolean phoneVerified,
        LocalDate dateOfBirth,
        String gender,
        String nationality,
        String countryOfResidence,
        String identityCountry,
        String identityType,
        String identityNumberMasked,
        Integer versionNo,
        long requestCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Creates a client response without exposing the complete
     * identity document number.
     */
    public static ClientResponse from(Client c, long requestCount) {

        return new ClientResponse(
                c.getId(),
                c.getClientCode(),
                c.getName(),
                c.getEmail(),
                c.getPhone(),
                c.getPhoneVerifiedAt() != null,
                c.getDateOfBirth(),
                c.getGender(),
                c.getNationality(),
                c.getCountryOfResidence(),
                c.getIdentityCountry(),
                c.getIdentityType(),
                IdentityProtectionService.mask(
                        c.getIdentityNumberLast4()),
                c.getVersionNo(),
                requestCount,
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
