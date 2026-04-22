package com.spotlink.partner;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class PartnerDtos {

    private PartnerDtos() {}

    public record PartnerProfileDto(
            UUID id,
            UUID operatorId,
            PartnerType partnerType,
            OnboardingStatus onboardingStatus,
            Integer pilotFitScore,
            String contactName,
            String contactEmail,
            String contactPhone,
            ConfirmationMode defaultConfirmationMode,
            String notes,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record UpdatePartnerProfileRequest(
            PartnerType partnerType,
            OnboardingStatus onboardingStatus,
            @Min(0) @Max(100) Integer pilotFitScore,
            @Size(max = 160) String contactName,
            @Email @Size(max = 320) String contactEmail,
            @Size(max = 50) String contactPhone,
            ConfirmationMode defaultConfirmationMode,
            @Size(max = 2000) String notes,
            Boolean active
    ) {}
}
