package com.insurance.quotation.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.insurance.quotation.entity.ClientVersion;
import com.insurance.quotation.service.client.IdentityProtectionService;

public record ClientVersionResponse(
        Integer versionNo,
        String name,
        String email,
        String phone,
        LocalDate dateOfBirth,
        String gender,
        String nationality,
        String countryOfResidence,
        String identityCountry,
        String identityType,
        String identityNumberMasked,
        String changeSource,
        String changedFields,
        String changedBy,
        boolean otpVerified,
        LocalDateTime createdAt) {

    public static ClientVersionResponse from(ClientVersion v) {
        return new ClientVersionResponse(
                v.getVersionNo(), v.getName(), v.getEmail(), v.getPhone(),
                v.getDateOfBirth(), v.getGender(), v.getNationality(),
                v.getCountryOfResidence(), v.getIdentityCountry(),
                v.getIdentityType(),
                IdentityProtectionService.mask(v.getIdentityNumberLast4()),
                v.getChangeSource(), v.getChangedFields(), v.getChangedBy(),
                v.isOtpVerified(), v.getCreatedAt());
    }
}
