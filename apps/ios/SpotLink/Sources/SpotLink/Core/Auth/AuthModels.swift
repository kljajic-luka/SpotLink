import Foundation

// MARK: - Auth Models

public struct LoginRequest: Encodable, Sendable {
    public let email: String
    public let password: String

    public init(email: String, password: String) {
        self.email = email
        self.password = password
    }
}

public struct RegisterCustomerRequest: Encodable, Sendable {
    public let firstName: String
    public let lastName: String
    public let email: String
    public let phone: String?
    public let password: String
    public let acceptsTerms: Bool

    public init(firstName: String, lastName: String, email: String,
                phone: String? = nil, password: String, acceptsTerms: Bool) {
        self.firstName = firstName
        self.lastName = lastName
        self.email = email
        self.phone = phone
        self.password = password
        self.acceptsTerms = acceptsTerms
    }
}

public struct RegisterOperatorRequest: Encodable, Sendable {
    public let firstName: String
    public let lastName: String
    public let email: String
    public let phone: String?
    public let password: String
    public let acceptsTerms: Bool
    public let companyName: String?
    public let operatorType: OperatorType
    public let acceptsOperatorAgreement: Bool

    public enum OperatorType: String, Encodable, Sendable {
        case individual = "INDIVIDUAL"
        case business   = "BUSINESS"
    }

    public init(firstName: String, lastName: String, email: String,
                phone: String? = nil, password: String, acceptsTerms: Bool,
                companyName: String? = nil, operatorType: OperatorType,
                acceptsOperatorAgreement: Bool) {
        self.firstName = firstName
        self.lastName = lastName
        self.email = email
        self.phone = phone
        self.password = password
        self.acceptsTerms = acceptsTerms
        self.companyName = companyName
        self.operatorType = operatorType
        self.acceptsOperatorAgreement = acceptsOperatorAgreement
    }
}

public struct PasswordResetRequest: Encodable, Sendable {
    public let email: String
    public init(email: String) { self.email = email }
}

public struct CompletePasswordResetRequest: Encodable, Sendable {
    public let token: String
    public let newPassword: String
    public init(token: String, newPassword: String) {
        self.token = token
        self.newPassword = newPassword
    }
}

/// Zahtev za mobile JWT token – koristi se za native iOS autentifikaciju.
public struct MobileTokenRequest: Encodable, Sendable {
    public let email: String
    public let password: String
    public init(email: String, password: String) {
        self.email = email
        self.password = password
    }
}

public struct RefreshTokenRequest: Encodable, Sendable {
    public let refreshToken: String
    public init(refreshToken: String) {
        self.refreshToken = refreshToken
    }
}

public struct RevokeTokenRequest: Encodable, Sendable {
    public let refreshToken: String?
    public let allForCurrentUser: Bool?

    public init(refreshToken: String? = nil, allForCurrentUser: Bool? = nil) {
        self.refreshToken = refreshToken
        self.allForCurrentUser = allForCurrentUser
    }
}

/// Odgovor sa JWT access tokenom.
public struct MobileTokenResponse: Decodable, Sendable {
    public let accessToken: String
    public let refreshToken: String
    public let expiresIn: Int
    public let expiresInSeconds: Int?
    public let refreshExpiresInSeconds: Int?
    public let issuedAt: Date?
    public let expiresAt: Date?
    public let refreshExpiresAt: Date?
    public let tokenType: String
    public let user: UserProfile

    enum CodingKeys: String, CodingKey {
        case accessToken, refreshToken, expiresIn, expiresInSeconds, refreshExpiresInSeconds
        case issuedAt, expiresAt, refreshExpiresAt, tokenType, user
    }
}

// MARK: - User Profile

public struct UserProfile: Codable, Identifiable, Sendable {
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

    public var fullName: String { "\(firstName) \(lastName)" }
    public var initials: String {
        let f = firstName.prefix(1)
        let l = lastName.prefix(1)
        return "\(f)\(l)".uppercased()
    }
    public var isOperator: Bool { roles.contains(.operator_) }
    public var isAdmin: Bool    { roles.contains(.admin) }
    public var isCustomer: Bool { roles.contains(.customer) }
    public var isSupport: Bool  { roles.contains(.support) }

    enum CodingKeys: String, CodingKey {
        case id, email, firstName, lastName, phone, avatarUrl, bio, roles
        case operatorId, registrationStatus, createdAt
    }
}

public enum UserRole: String, Codable, Sendable {
    case customer  = "CUSTOMER"
    case operator_ = "OPERATOR"
    case support   = "SUPPORT"
    case admin     = "ADMIN"
}

// MARK: - Auth State

public enum AuthState: Sendable {
    case loading
    case unauthenticated
    case authenticated(UserProfile)

    public var isAuthenticated: Bool {
        if case .authenticated = self { return true }
        return false
    }

    public var user: UserProfile? {
        if case .authenticated(let user) = self { return user }
        return nil
    }
}
