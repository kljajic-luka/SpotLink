package com.spotlink.vehicle;

import com.spotlink.core.NotFoundException;
import com.spotlink.security.CurrentUserService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

    private final VehicleRepository vehicles;
    private final CurrentUserService currentUser;

    public VehicleService(VehicleRepository vehicles, CurrentUserService currentUser) {
        this.vehicles = vehicles;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<VehicleDtos.VehicleProfileDto> mine() {
        return vehicles.findByUserIdOrderByCreatedAtDesc(currentUser.userId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public VehicleDtos.VehicleProfileDto create(VehicleDtos.VehicleUpsertRequest request) {
        VehicleProfile vehicle = new VehicleProfile();
        vehicle.setUserId(currentUser.userId());
        apply(vehicle, request);
        return toDto(vehicles.save(vehicle));
    }

    @Transactional
    public VehicleDtos.VehicleProfileDto update(UUID vehicleId, VehicleDtos.VehicleUpsertRequest request) {
        VehicleProfile vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle was not found."));
        requireOwner(vehicle);
        apply(vehicle, request);
        return toDto(vehicle);
    }

    @Transactional
    public void delete(UUID vehicleId) {
        VehicleProfile vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle was not found."));
        requireOwner(vehicle);
        vehicles.delete(vehicle);
    }

    @Transactional(readOnly = true)
    public VehicleProfile requireOwnedEntity(UUID vehicleId, UUID userId) {
        VehicleProfile vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle was not found."));
        if (!vehicle.getUserId().equals(userId)) {
            throw new AccessDeniedException("Vehicle does not belong to the current user.");
        }
        return vehicle;
    }

    VehicleDtos.VehicleProfileDto toDto(VehicleProfile vehicle) {
        return new VehicleDtos.VehicleProfileDto(
                vehicle.getId(),
                vehicle.getUserId(),
                vehicle.getType(),
                vehicle.getNickname(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getColor(),
                vehicle.getLicensePlate(),
                vehicle.getHeightMeters(),
                vehicle.getLengthMeters(),
                vehicle.isEvCapable(),
                vehicle.getVerificationStatus(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt());
    }

    private void apply(VehicleProfile vehicle, VehicleDtos.VehicleUpsertRequest request) {
        vehicle.setType(request.type());
        vehicle.setNickname(request.nickname());
        vehicle.setMake(request.make());
        vehicle.setModel(request.model());
        vehicle.setColor(request.color());
        vehicle.setLicensePlate(request.licensePlate());
        vehicle.setHeightMeters(request.heightMeters());
        vehicle.setLengthMeters(request.lengthMeters());
        vehicle.setEvCapable(Boolean.TRUE.equals(request.evCapable()));
    }

    private void requireOwner(VehicleProfile vehicle) {
        if (!vehicle.getUserId().equals(currentUser.userId())) {
            throw new AccessDeniedException("Vehicle does not belong to the current user.");
        }
    }
}
