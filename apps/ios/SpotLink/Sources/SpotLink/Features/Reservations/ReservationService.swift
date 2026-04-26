import Foundation

// MARK: - Reservation Service

public final class ReservationService: Sendable {
    private let apiClient: APIClientProtocol

    public init(apiClient: APIClientProtocol) {
        self.apiClient = apiClient
    }

    public func listMine(page: Int = 0, size: Int = 20) async throws -> APIPage<Reservation> {
        try await apiClient.get("/reservations/me", query: ["page": String(page), "size": String(size)])
    }

    public func getReservation(_ id: String) async throws -> Reservation {
        try await apiClient.get("/reservations/\(id)", query: nil)
    }

    public func getReservationDetail(_ id: String) async throws -> BookingDetail {
        try await apiClient.get("/reservations/\(id)/detail", query: nil)
    }

    public func quote(_ request: ReservationQuoteRequest) async throws -> ReservationQuote {
        try await apiClient.post("/reservations/quote", body: request)
    }

    public func create(_ request: CreateReservationRequest) async throws -> Reservation {
        try await apiClient.post("/reservations", body: request)
    }

    public func cancel(_ id: String, reason: String? = nil) async throws -> Reservation {
        try await apiClient.post("/reservations/\(id)/cancel", body: CancelReservationRequest(reason: reason))
    }
}
