package com.spotlink.location;

import com.spotlink.core.ApiPage;
import com.spotlink.core.NotFoundException;
import com.spotlink.operator.OperatorAccount;
import com.spotlink.operator.OperatorAccountRepository;
import com.spotlink.security.CurrentUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService {

    private final ParkingLocationRepository locations;
    private final ParkingResourceRepository resources;
    private final OperatorAccountRepository operators;
    private final CurrentUserService currentUser;
    private final LocationMapper mapper;

    public LocationService(
            ParkingLocationRepository locations,
            ParkingResourceRepository resources,
            OperatorAccountRepository operators,
            CurrentUserService currentUser,
            LocationMapper mapper) {
        this.locations = locations;
        this.resources = resources;
        this.operators = operators;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ApiPage<LocationDtos.LocationSearchResult> search(LocationDtos.SearchFilters filters) {
        int page = filters.page() == null ? 0 : filters.page();
        int size = filters.size() == null ? 20 : Math.min(filters.size(), 100);
        Pageable pageable = PageRequest.of(page, size);
        var locationPage = locations.search(blankToNull(filters.query()), pageable);
        List<UUID> locationIds = locationPage.getContent().stream().map(ParkingLocation::getId).toList();
        Map<UUID, List<ParkingResource>> resourcesByLocation = resources.findByLocationIdInAndActiveTrue(locationIds)
                .stream()
                .filter(resource -> matchesResourceFilters(resource, filters))
                .collect(Collectors.groupingBy(ParkingResource::getLocationId));

        var mapped = locationPage.map(location -> {
            List<ParkingResource> activeResources = resourcesByLocation.getOrDefault(location.getId(), List.of());
            Long startingPrice = activeResources.stream()
                    .map(ParkingResource::getHourlyRateCents)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            return new LocationDtos.LocationSearchResult(
                    mapper.toDto(location),
                    activeResources.stream().map(mapper::toDto).toList(),
                    distanceKm(location, filters),
                    startingPrice,
                    activeResources.size());
        });
        return ApiPage.from(mapped);
    }

    @Transactional(readOnly = true)
    public LocationDtos.ParkingLocationDto getLocation(UUID locationId) {
        return mapper.toDto(locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Parking location was not found.")));
    }

    @Transactional(readOnly = true)
    public List<LocationDtos.ParkingResourceDto> getResources(UUID locationId) {
        if (!locations.existsById(locationId)) {
            throw new NotFoundException("Parking location was not found.");
        }
        return resources.findByLocationIdAndActiveTrueOrderByLabel(locationId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    public LocationDtos.ParkingLocationDto createLocation(LocationDtos.UpsertLocationRequest request) {
        OperatorAccount operator = currentOperator();
        ParkingLocation location = new ParkingLocation();
        location.setOperatorId(operator.getId());
        apply(location, request);
        return mapper.toDto(locations.save(location));
    }

    @Transactional
    public LocationDtos.ParkingLocationDto updateLocation(UUID locationId, LocationDtos.UpsertLocationRequest request) {
        ParkingLocation location = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Parking location was not found."));
        requireOperator(location);
        apply(location, request);
        return mapper.toDto(location);
    }

    @Transactional
    public LocationDtos.ParkingResourceDto createResource(UUID locationId, LocationDtos.UpsertResourceRequest request) {
        ParkingLocation location = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Parking location was not found."));
        requireOperator(location);
        ParkingResource resource = new ParkingResource();
        resource.setLocationId(locationId);
        apply(resource, request);
        return mapper.toDto(resources.save(resource));
    }

    @Transactional
    public LocationDtos.ParkingResourceDto updateResource(UUID locationId, UUID resourceId, LocationDtos.UpsertResourceRequest request) {
        ParkingLocation location = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Parking location was not found."));
        requireOperator(location);
        ParkingResource resource = resources.findById(resourceId)
                .orElseThrow(() -> new NotFoundException("Parking resource was not found."));
        if (!resource.getLocationId().equals(locationId)) {
            throw new NotFoundException("Parking resource was not found.");
        }
        apply(resource, request);
        return mapper.toDto(resource);
    }

    public ParkingResource requireResource(UUID resourceId) {
        return resources.findById(resourceId)
                .orElseThrow(() -> new NotFoundException("Parking resource was not found."));
    }

    public ParkingLocation requireLocation(UUID locationId) {
        return locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Parking location was not found."));
    }

    private void apply(ParkingLocation location, LocationDtos.UpsertLocationRequest request) {
        location.setName(request.name());
        location.setAddress(mapper.toEntity(request.address()));
        location.setCoordinates(mapper.toEntity(request.coordinates()));
        location.setTimezone(request.timezone());
        location.setAccessType(request.accessType());
        location.setPublicNotes(request.publicNotes());
        location.setActive(request.active() == null || request.active());
    }

    private void apply(ParkingResource resource, LocationDtos.UpsertResourceRequest request) {
        resource.setType(request.type());
        resource.setLabel(request.label());
        resource.setFloor(request.floor());
        resource.setBayNumber(request.bayNumber());
        if (request.fitRule() != null) {
            resource.setMaxHeightMeters(request.fitRule().maxHeightMeters());
            resource.setMaxLengthMeters(request.fitRule().maxLengthMeters());
            resource.setAllowedVehicleTypes(mapper.vehicleTypesToCsv(request.fitRule().allowedVehicleTypes()));
            resource.setEvOnly(Boolean.TRUE.equals(request.fitRule().evOnly()));
        }
        resource.setHourlyRateCents(request.hourlyRateCents());
        resource.setDailyRateCents(request.dailyRateCents());
        resource.setCurrency(request.currency());
        resource.setInstantReserve(request.instantReserve() == null || request.instantReserve());
        resource.setActive(request.active() == null || request.active());
    }

    private boolean matchesResourceFilters(ParkingResource resource, LocationDtos.SearchFilters filters) {
        if (filters.resourceTypes() != null && !filters.resourceTypes().isEmpty() && !filters.resourceTypes().contains(resource.getType())) {
            return false;
        }
        if (Boolean.TRUE.equals(filters.evChargingRequired()) && resource.getType() != ParkingResourceType.EV_CHARGER) {
            return false;
        }
        return true;
    }

    private OperatorAccount currentOperator() {
        return operators.findByUserId(currentUser.userId())
                .orElseThrow(() -> new AccessDeniedException("Operator account is required."));
    }

    private void requireOperator(ParkingLocation location) {
        OperatorAccount operator = currentOperator();
        if (!location.getOperatorId().equals(operator.getId())) {
            throw new AccessDeniedException("Parking location does not belong to the current operator.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Double distanceKm(ParkingLocation location, LocationDtos.SearchFilters filters) {
        if (filters.latitude() == null || filters.longitude() == null) {
            return null;
        }
        double lat1 = Math.toRadians(filters.latitude().doubleValue());
        double lat2 = Math.toRadians(location.getCoordinates().getLatitude().doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(location.getCoordinates().getLongitude().doubleValue() - filters.longitude().doubleValue());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(6371.0 * c).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
