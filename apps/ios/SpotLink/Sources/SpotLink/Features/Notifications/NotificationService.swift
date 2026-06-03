import Foundation

public protocol DeviceTokenLifecycleServicing: Sendable {
    func registerDeviceToken(_ token: String) async throws
    func unregisterDeviceToken(_ token: String) async throws
}

// MARK: - Notification Service

public final class NotificationService: DeviceTokenLifecycleServicing {
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
        let _: EmptyResponse = try await apiClient.post("/notifications/\(notificationId)/read", body: EmptyPayload())
    }

    /// Registruje APNs device token na backendu.
    public func registerDeviceToken(_ token: String) async throws {
        let _: EmptyResponse = try await apiClient.post(
            "/notifications/device-tokens",
            body: RegisterDeviceTokenRequest(deviceToken: token))
    }

    /// Odjavljuje APNs device token sa backenda za trenutnog korisnika.
    public func unregisterDeviceToken(_ token: String) async throws {
        let _: EmptyResponse = try await apiClient.post(
            "/notifications/device-tokens/unregister",
            body: UnregisterDeviceTokenRequest(deviceToken: token))
    }
}

private struct EmptyPayload: Encodable {}
