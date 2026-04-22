import Foundation

// MARK: - Notification

public struct SpotLinkNotification: Decodable, Identifiable, Sendable {
    public let id: String
    public let userId: String
    public let type: String
    public let title: String
    public let body: String
    public let relatedEntityId: String?
    public let read: Bool
    public let createdAt: Date

    enum CodingKeys: String, CodingKey {
        case id, userId, type, title, body
        case relatedEntityId
        case read = "readFlag"
        case createdAt
    }
}

public struct NotificationUnreadCount: Decodable, Sendable {
    public let count: Int
}

// MARK: - Device Token

public struct RegisterDeviceTokenRequest: Encodable, Sendable {
    public let deviceToken: String
    public let platform: String

    public init(deviceToken: String) {
        self.deviceToken = deviceToken
        self.platform = "iOS"
    }
}
