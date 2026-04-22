package com.spotlink.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserProfile(
            UUID id,
            String email,
            String firstName,
            String lastName,
            String phone,
            String avatarUrl,
            String bio,
            List<UserRole> roles,
            UUID operatorId,
            RegistrationStatus registrationStatus,
            Instant createdAt
    ) {
    }

    public record UserProfileDetails(
            UUID id,
            String email,
            String firstName,
            String lastName,
            String phone,
            String avatarUrl,
            String bio,
            List<UserRole> roles,
            UUID operatorId,
            RegistrationStatus registrationStatus,
            Instant createdAt,
            ProfileStats stats,
            UserPreferencesDto preferences
    ) {
    }

    public record ProfileStats(
            long completedReservations,
            long activeVehicles,
            long savedLocations,
            long supportTickets
    ) {
    }

    public record UserPreferencesDto(
            String locale,
            boolean marketingOptIn,
            boolean reservationAlerts,
            boolean paymentAlerts,
            boolean supportAlerts
    ) {
    }

    public record UpdateProfileRequest(
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 50) String phone,
            @Size(max = 500) String avatarUrl,
            @Size(max = 1000) String bio,
            @Valid PartialPreferences preferences
    ) {
    }

    public record PartialPreferences(
            @Size(max = 20) String locale,
            Boolean marketingOptIn,
            Boolean reservationAlerts,
            Boolean paymentAlerts,
            Boolean supportAlerts
    ) {
    }
}
