package com.spotlink.location;

import com.spotlink.core.ApiPage;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LocationController {

    private final LocationService locationService;
    private final GeocodeService geocodeService;

    public LocationController(LocationService locationService, GeocodeService geocodeService) {
        this.locationService = locationService;
        this.geocodeService = geocodeService;
    }

    @GetMapping({"/locations/search", "/v1/locations/search"})
    ApiPage<LocationDtos.LocationSearchResult> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) BigDecimal radiusKm,
            @RequestParam(required = false) List<ParkingResourceType> resourceTypes,
            @RequestParam(required = false) Boolean evChargingRequired,
            @RequestParam(required = false) Instant startsAt,
            @RequestParam(required = false) Instant endsAt,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return locationService.search(new LocationDtos.SearchFilters(
                query,
                latitude,
                longitude,
                radiusKm,
                resourceTypes,
                evChargingRequired,
                startsAt,
                endsAt,
                page,
                size));
    }

    @GetMapping({"/locations/geocode", "/v1/locations/geocode"})
    List<LocationDtos.GeocodeSuggestion> geocode(@RequestParam String query) {
        return geocodeService.suggest(query);
    }

    @GetMapping({"/locations/{locationId}", "/v1/locations/{locationId}"})
    LocationDtos.ParkingLocationDto getLocation(@PathVariable UUID locationId) {
        return locationService.getLocation(locationId);
    }

    @GetMapping({"/locations/{locationId}/resources", "/v1/locations/{locationId}/resources"})
    List<LocationDtos.ParkingResourceDto> getResources(@PathVariable UUID locationId) {
        return locationService.getResources(locationId);
    }

    @PostMapping({"/locations", "/v1/locations"})
    @ResponseStatus(HttpStatus.CREATED)
    LocationDtos.ParkingLocationDto createLocation(@Valid @RequestBody LocationDtos.UpsertLocationRequest request) {
        return locationService.createLocation(request);
    }

    @PutMapping({"/locations/{locationId}", "/v1/locations/{locationId}"})
    LocationDtos.ParkingLocationDto updateLocation(
            @PathVariable UUID locationId,
            @Valid @RequestBody LocationDtos.UpsertLocationRequest request) {
        return locationService.updateLocation(locationId, request);
    }

    @PostMapping({"/locations/{locationId}/resources", "/v1/locations/{locationId}/resources"})
    @ResponseStatus(HttpStatus.CREATED)
    LocationDtos.ParkingResourceDto createResource(
            @PathVariable UUID locationId,
            @Valid @RequestBody LocationDtos.UpsertResourceRequest request) {
        return locationService.createResource(locationId, request);
    }

    @PutMapping({"/locations/{locationId}/resources/{resourceId}", "/v1/locations/{locationId}/resources/{resourceId}"})
    LocationDtos.ParkingResourceDto updateResource(
            @PathVariable UUID locationId,
            @PathVariable UUID resourceId,
            @Valid @RequestBody LocationDtos.UpsertResourceRequest request) {
        return locationService.updateResource(locationId, resourceId, request);
    }
}
