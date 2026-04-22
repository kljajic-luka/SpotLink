package com.spotlink.location;

import java.util.List;

public interface GeocodeService {

    List<LocationDtos.GeocodeSuggestion> suggest(String query);
}
