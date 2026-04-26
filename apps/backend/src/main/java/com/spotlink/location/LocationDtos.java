package com.spotlink.location;

import com.spotlink.vehicle.VehicleDtos;
import com.spotlink.partner.ConfirmationMode;
import com.spotlink.reservation.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LocationDtos {

    private LocationDtos() {
    }

    public record GeoCoordinatesDto(
            @NotNull BigDecimal latitude,
            @NotNull BigDecimal longitude
    ) {
    }

    public record AddressDto(
            @NotBlank @Size(max = 180) String line1,
            @Size(max = 180) String line2,
            @NotBlank @Size(max = 100) String city,
            @Size(max = 100) String region,
            @Size(max = 30) String postalCode,
            @NotBlank @Size(min = 2, max = 2) String country,
            @Size(max = 500) String formattedAddress
    ) {
    }

    public record ParkingLocationDto(
            UUID id,
            UUID operatorId,
            String name,
            AddressDto address,
            GeoCoordinatesDto coordinates,
            String timezone,
            ParkingAccessType accessType,
            String publicNotes,
            boolean active
    ) {
    }

    public record ParkingResourceDto(
            UUID id,
            UUID locationId,
            ParkingResourceType type,
            String label,
            String floor,
            String bayNumber,
            VehicleDtos.VehicleFitRuleDto fitRule,
            long hourlyRateCents,
            Long dailyRateCents,
            String currency,
            boolean instantReserve,
            boolean active,
            int capacity,
            ConfirmationMode confirmationMode,
            boolean payOnArrivalEnabled,
            List<PaymentMode> supportedPaymentModes
    ) {
    }

    public record LocationSearchResult(
            ParkingLocationDto location,
            List<ParkingResourceDto> resources,
            Double distanceKm,
            Long startingPriceCents,
            long availableResourceCount
    ) {
    }

    public record GeocodeSuggestion(
            String id,
            AddressDto address,
            GeoCoordinatesDto coordinates,
            Integer accuracyMeters
    ) {
    }

    public record UpsertLocationRequest(
            @NotBlank @Size(max = 180) String name,
            @Valid @NotNull AddressDto address,
            @Valid @NotNull GeoCoordinatesDto coordinates,
            @NotBlank @Size(max = 80) String timezone,
            @NotNull ParkingAccessType accessType,
            @Size(max = 1000) String publicNotes,
            Boolean active
    ) {
    }

    public record UpsertResourceRequest(
            @NotNull ParkingResourceType type,
            @NotBlank @Size(max = 100) String label,
            @Size(max = 40) String floor,
            @Size(max = 40) String bayNumber,
            @Valid VehicleDtos.VehicleFitRuleDto fitRule,
            @Min(0) long hourlyRateCents,
            @Min(0) Long dailyRateCents,
            @NotBlank @Size(min = 3, max = 3) String currency,
            Boolean instantReserve,
            Boolean active,
            @Min(1) Integer capacity,
            ConfirmationMode confirmationMode
    ) {
    }

    public record LocationHoursDto(
            UUID id,
            String dayOfWeek,
            String openTime,
            String closeTime
    ) {
    }

    public record UpsertLocationHoursRequest(
            @NotNull List<@Valid LocationHoursEntry> entries
    ) {
    }

    public record LocationHoursEntry(
            @NotBlank @Size(max = 9) String dayOfWeek,
            @NotBlank @Pattern(regexp = "\\d{2}:\\d{2}") String openTime,
            @NotBlank @Pattern(regexp = "\\d{2}:\\d{2}") String closeTime
    ) {
    }

    public record AvailabilityExceptionDto(
            UUID id,
            String label,
            Instant startsAt,
            Instant endsAt
    ) {
    }

    public record CreateAvailabilityExceptionRequest(
            @Size(max = 180) String label,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt
    ) {
    }

    public record SearchFilters(
            String query,
            BigDecimal latitude,
            BigDecimal longitude,
            @DecimalMin("0.1") BigDecimal radiusKm,
            List<ParkingResourceType> resourceTypes,
            Boolean evChargingRequired,
            Instant startsAt,
            Instant endsAt,
            @Min(0) Integer page,
            @Min(1) @Max(100) Integer size
    ) {
    }
}
