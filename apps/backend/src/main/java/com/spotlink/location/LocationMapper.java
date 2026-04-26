package com.spotlink.location;

import com.spotlink.partner.ConfirmationMode;
import com.spotlink.inventory.InventoryPool;
import com.spotlink.reservation.PaymentMode;
import com.spotlink.vehicle.VehicleDtos;
import com.spotlink.vehicle.VehicleType;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LocationMapper {

    public LocationDtos.ParkingLocationDto toDto(ParkingLocation location) {
        return new LocationDtos.ParkingLocationDto(
                location.getId(),
                location.getOperatorId(),
                location.getName(),
                toDto(location.getAddress()),
                toDto(location.getCoordinates()),
                location.getTimezone(),
                location.getAccessType(),
                location.getPublicNotes(),
                location.isActive());
    }

    public LocationDtos.ParkingResourceDto toDto(ParkingResource resource) {
        return toDto(resource, null);
    }

    public LocationDtos.ParkingResourceDto toDto(ParkingResource resource, InventoryPool pool) {
        return new LocationDtos.ParkingResourceDto(
                resource.getId(),
                resource.getLocationId(),
                resource.getType(),
                resource.getLabel(),
                resource.getFloor(),
                resource.getBayNumber(),
                new VehicleDtos.VehicleFitRuleDto(
                        resource.getMaxHeightMeters(),
                        resource.getMaxLengthMeters(),
                        parseVehicleTypes(resource.getAllowedVehicleTypes()),
                        resource.isEvOnly() ? Boolean.TRUE : null),
                resource.getHourlyRateCents(),
                resource.getDailyRateCents(),
                resource.getCurrency(),
                resource.isInstantReserve(),
                resource.isActive(),
                resource.getCapacity(),
                resource.getConfirmationMode(),
                pool != null && pool.isPayOnArrivalEnabled(),
                supportedPaymentModes(pool));
    }

    public LocationDtos.AddressDto toDto(Address address) {
        return new LocationDtos.AddressDto(
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getRegion(),
                address.getPostalCode(),
                address.getCountry(),
                address.getFormattedAddress());
    }

    public LocationDtos.GeoCoordinatesDto toDto(GeoCoordinates coordinates) {
        return new LocationDtos.GeoCoordinatesDto(coordinates.getLatitude(), coordinates.getLongitude());
    }

    public Address toEntity(LocationDtos.AddressDto dto) {
        Address address = new Address();
        address.setLine1(dto.line1());
        address.setLine2(dto.line2());
        address.setCity(dto.city());
        address.setRegion(dto.region());
        address.setPostalCode(dto.postalCode());
        address.setCountry(dto.country());
        address.setFormattedAddress(dto.formattedAddress());
        return address;
    }

    public GeoCoordinates toEntity(LocationDtos.GeoCoordinatesDto dto) {
        GeoCoordinates coordinates = new GeoCoordinates();
        coordinates.setLatitude(dto.latitude());
        coordinates.setLongitude(dto.longitude());
        return coordinates;
    }

    public String vehicleTypesToCsv(List<VehicleType> vehicleTypes) {
        if (vehicleTypes == null || vehicleTypes.isEmpty()) {
            return null;
        }
        return String.join(",", vehicleTypes.stream().map(Enum::name).toList());
    }

    private List<VehicleType> parseVehicleTypes(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(VehicleType::valueOf)
                .toList();
    }

    private List<PaymentMode> supportedPaymentModes(InventoryPool pool) {
        if (pool == null) {
            return List.of(PaymentMode.ONLINE);
        }
        return pool.isPayOnArrivalEnabled()
                ? List.of(PaymentMode.ONLINE, PaymentMode.PAY_ON_ARRIVAL)
                : List.of(PaymentMode.ONLINE);
    }
}
