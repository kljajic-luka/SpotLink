import Foundation

// MARK: - Notification Service

public final class NotificationService: Sendable {
    private let apiClient: APIClientProtocol

    public init(apiClient: APIClientProtocol) {
        self.apiClient = apiClient
    }

    public func listNotifications(page: Int = 0, size: Int = 20) async throws -> APIPage<SpotLinkNotification> {
        try await apiClient.get("/notifications", query: ["page": String(page), "size": String(size)])
    }

    public func unreadCount() async throws -> Int {
        let response: NotificationUnreadCount = try await apiClient.get("/notifications/unread-count", query: nil)
        return response.count
    }

    public func markRead(_ notificationId: String) async throws {
        try await apiClient.delete("/notifications/\(notificationId)/read")
    }

    /// Registruje APNs device token na backendu.
    public func registerDeviceToken(_ token: String) async throws {
        let _: DeviceTokenResponse = try await apiClient.post(
            "/notifications/device-tokens",
            body: RegisterDeviceTokenRequest(deviceToken: token))
    }
}

private struct DeviceTokenResponse: Decodable {}
