import Foundation

// MARK: - User Profile Details

public struct UserProfileDetails: Decodable, Sendable {
    public let id: String
    public let email: String
    public let firstName: String
    public let lastName: String
    public let phone: String?
    public let avatarUrl: String?
    public let bio: String?
    public let roles: [UserRole]
    public let operatorId: String?
    public let registrationStatus: String?
    public let createdAt: String?
    public let stats: ProfileStats
    public let preferences: UserPreferences

    public var fullName: String { "\(firstName) \(lastName)" }
    public var initials: String { "\(firstName.prefix(1))\(lastName.prefix(1))".uppercased() }
}

public struct ProfileStats: Decodable, Sendable {
    public let completedReservations: Int
    public let activeVehicles: Int
    public let savedLocations: Int
    public let supportTickets: Int

    public var openSupportTickets: Int { supportTickets }
}

public struct UserPreferences: Decodable, Sendable {
    public let locale: String?
    public let marketingOptIn: Bool
    public let reservationAlerts: Bool
    public let paymentAlerts: Bool
    public let supportAlerts: Bool
}

// MARK: - Update Profile

public struct UpdateProfileRequest: Encodable, Sendable {
    public let firstName: String?
    public let lastName: String?
    public let phone: String?
    public let bio: String?

    public init(firstName: String? = nil, lastName: String? = nil,
                phone: String? = nil, bio: String? = nil) {
        self.firstName = firstName
        self.lastName = lastName
        self.phone = phone
        self.bio = bio
    }
}

// MARK: - Operator

public struct OperatorAccount: Decodable, Identifiable, Sendable {
    public let id: String
    public let userId: String
    public let displayName: String
    public let legalName: String?
    public let supportEmail: String?
    public let active: Bool
}

public struct OperatorDashboardSummary: Decodable, Sendable {
    public let activeLocations: Int
    public let totalResources: Int
    public let activeReservations: Int
    public let totalRevenueLastDayCents: Int?
    public let currency: String
}

public struct ResourceHealthItem: Decodable, Sendable {
    public let resourceId: String
    public let locationName: String
    public let label: String
    public let status: String
    public let activeReservations: Int
}

// MARK: - Admin

public struct AdminDashboardSummary: Decodable, Sendable {
    public let totalUsers: Int
    public let totalOperators: Int
    public let activeReservations: Int
    public let totalLocations: Int
    public let revenueLastDayCents: Int?
    public let currency: String
}

public struct AdminUserSummary: Decodable, Identifiable, Sendable {
    public let id: String
    public let email: String
    public let fullName: String
    public let roles: [UserRole]
    public let registrationStatus: String
    public let createdAt: String
}

public struct AuditEvent: Decodable, Identifiable, Sendable {
    public let id: String
    public let actorUserId: String?
    public let action: String
    public let resourceType: String
    public let resourceId: String?
    public let metadata: [String: String]?
    public let createdAt: Date
}
