import Foundation

// MARK: - Admin Service

public final class AdminService: Sendable {
    private let apiClient: APIClientProtocol

    public init(apiClient: APIClientProtocol) {
        self.apiClient = apiClient
    }

    public func getDashboardSummary() async throws -> AdminDashboardSummary {
        try await apiClient.get("/admin/dashboard/summary", query: nil)
    }

    public func listUsers(page: Int = 0, size: Int = 20) async throws -> APIPage<AdminUserSummary> {
        try await apiClient.get("/admin/users", query: ["page": String(page), "size": String(size)])
    }

    public func listAuditEvents(page: Int = 0, size: Int = 50) async throws -> APIPage<AuditEvent> {
        try await apiClient.get("/admin/audit-events", query: ["page": String(page), "size": String(size)])
    }
}
