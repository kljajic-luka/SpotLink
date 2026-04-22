import Foundation

// MARK: - Operator Service

public final class OperatorService: Sendable {
    private let apiClient: APIClientProtocol

    public init(apiClient: APIClientProtocol) {
        self.apiClient = apiClient
    }

    public func getMyAccount() async throws -> OperatorAccount {
        try await apiClient.get("/operator/me", query: nil)
    }

    public func getDashboardSummary() async throws -> OperatorDashboardSummary {
        try await apiClient.get("/operator/dashboard/summary", query: nil)
    }

    public func getResourceHealth() async throws -> [ResourceHealthItem] {
        try await apiClient.get("/operator/resources/health", query: nil)
    }
}
