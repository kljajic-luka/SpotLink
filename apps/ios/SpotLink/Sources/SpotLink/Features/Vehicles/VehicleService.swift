import Foundation

// MARK: - Vehicle Service

public final class VehicleService: Sendable {
    private let apiClient: APIClientProtocol

    public init(apiClient: APIClientProtocol) {
        self.apiClient = apiClient
    }

    public func listMyVehicles() async throws -> [VehicleProfile] {
        try await apiClient.get("/vehicles/me", query: nil)
    }

    public func createVehicle(_ request: VehicleUpsertRequest) async throws -> VehicleProfile {
        try await apiClient.post("/vehicles", body: request)
    }

    public func updateVehicle(_ vehicleId: String, request: VehicleUpsertRequest) async throws -> VehicleProfile {
        try await apiClient.put("/vehicles/\(vehicleId)", body: request)
    }

    public func deleteVehicle(_ vehicleId: String) async throws {
        try await apiClient.delete("/vehicles/\(vehicleId)")
    }
}
