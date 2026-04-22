package com.spotlink.location;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MockGeocodeService implements GeocodeService {

    @Override
    public List<LocationDtos.GeocodeSuggestion> suggest(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        LocationDtos.AddressDto address = new LocationDtos.AddressDto(
                query.trim(),
                null,
                "Belgrade",
                null,
                null,
                "RS",
                query.trim());
        LocationDtos.GeoCoordinatesDto coordinates = new LocationDtos.GeoCoordinatesDto(
                new BigDecimal("44.812500"),
                new BigDecimal("20.461200"));
        return List.of(new LocationDtos.GeocodeSuggestion("mock-" + UUID.nameUUIDFromBytes(query.getBytes()), address, coordinates, 500));
    }
}
