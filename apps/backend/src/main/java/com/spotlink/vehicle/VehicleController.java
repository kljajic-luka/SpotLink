package com.spotlink.vehicle;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping({"/vehicles/me", "/v1/vehicles/me"})
    List<VehicleDtos.VehicleProfileDto> mine() {
        return vehicleService.mine();
    }

    @PostMapping({"/vehicles", "/v1/vehicles"})
    @ResponseStatus(HttpStatus.CREATED)
    VehicleDtos.VehicleProfileDto create(@Valid @RequestBody VehicleDtos.VehicleUpsertRequest request) {
        return vehicleService.create(request);
    }

    @PutMapping({"/vehicles/{vehicleId}", "/v1/vehicles/{vehicleId}"})
    VehicleDtos.VehicleProfileDto update(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody VehicleDtos.VehicleUpsertRequest request) {
        return vehicleService.update(vehicleId, request);
    }

    @DeleteMapping({"/vehicles/{vehicleId}", "/v1/vehicles/{vehicleId}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID vehicleId) {
        vehicleService.delete(vehicleId);
    }
}
