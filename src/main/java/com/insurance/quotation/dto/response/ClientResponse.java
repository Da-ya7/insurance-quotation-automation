package com.insurance.quotation.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.service.client.IdentityProtectionService;

/**
 * What the staff screens see of a client. The identity number is never
 * returned: only its type, issuing country and a masked tail (XXXX1234).
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
                IdentityProtectionService.mask(c.getIdentityNumberLast4()),
                c.getVersionNo(),
                requestCount,
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
