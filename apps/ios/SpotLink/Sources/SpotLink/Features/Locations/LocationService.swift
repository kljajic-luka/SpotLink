import Foundation

// MARK: - Location Service

public final class LocationService: Sendable {
    private let apiClient: APIClientProtocol

    public init(apiClient: APIClientProtocol) {
        self.apiClient = apiClient
    }

    public func search(_ filters: LocationSearchFilters) async throws -> APIPage<LocationSearchResult> {
        try await apiClient.get("/locations/search", query: filters.queryParameters)
    }

    public func geocode(_ query: String) async throws -> [GeocodeSuggestion] {
        try await apiClient.get("/locations/geocode", query: ["query": query])
    }

    public func getLocation(_ locationId: String) async throws -> ParkingLocation {
        try await apiClient.get("/locations/\(locationId)", query: nil)
    }

    public func listResources(locationId: String) async throws -> [ParkingResource] {
        try await apiClient.get("/locations/\(locationId)/resources", query: nil)
    }
}
