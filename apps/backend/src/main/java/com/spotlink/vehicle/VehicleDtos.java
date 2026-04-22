package com.spotlink.vehicle;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class VehicleDtos {

    private VehicleDtos() {
    }

    public record VehicleProfileDto(
            UUID id,
            UUID userId,
            VehicleType type,
            String nickname,
            String make,
            String model,
            String color,
            String licensePlate,
            BigDecimal heightMeters,
            BigDecimal lengthMeters,
            boolean evCapable,
            VehicleVerificationStatus verificationStatus,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record VehicleUpsertRequest(
            @NotNull VehicleType type,
            @Size(max = 100) String nickname,
            @Size(max = 100) String make,
            @Size(max = 100) String model,
            @Size(max = 60) String color,
            @Size(max = 40) String licensePlate,
            @DecimalMin(value = "0.1", inclusive = true) BigDecimal heightMeters,
            @DecimalMin(value = "0.1", inclusive = true) BigDecimal lengthMeters,
            Boolean evCapable
    ) {
    }

    public record VehicleFitRuleDto(
            BigDecimal maxHeightMeters,
            BigDecimal maxLengthMeters,
            List<VehicleType> allowedVehicleTypes,
            Boolean evOnly
    ) {
    }
}
