import Foundation

// MARK: - Session State

public enum SessionState: Sendable {
    case loading
    case unauthenticated
    case authenticated(SessionInfo)

    public var isAuthenticated: Bool {
        if case .authenticated = self { return true }
        return false
    }

    public var sessionInfo: SessionInfo? {
        if case .authenticated(let info) = self { return info }
        return nil
    }

    public var user: UserProfile? { sessionInfo?.user }
}

public struct SessionInfo: Sendable {
    public let user: UserProfile
    public let accessToken: String
    public let tokenExpiresAt: Date

    public var isExpired: Bool { Date() >= tokenExpiresAt }
    public var isExpiringSoon: Bool { Date().addingTimeInterval(300) >= tokenExpiresAt }
}

// MARK: - Session Keys

enum SessionStorageKey {
    static let accessToken = "session.accessToken"
    static let tokenExpiresAt = "session.tokenExpiresAt"
    static let userProfile = "session.userProfile"
}

// MARK: - Session Manager

/// Centralni manager sesije za iOS app.
/// Cuva JWT token u Keychain, user profile u UserDefaults.
/// Implementira TokenProvider protokol za APIClient.
@MainActor
public final class SessionManager: ObservableObject, TokenProvider {

    public static let shared = SessionManager()

    @Published public private(set) var state: SessionState = .loading

    private let keychain: KeychainStorage
    private let preferences: PreferenceStorage

    public init(
        keychain: KeychainStorage = .shared,
        preferences: PreferenceStorage = .shared
    ) {
        self.keychain = keychain
        self.preferences = preferences
    }

    // MARK: - TokenProvider

    public nonisolated func currentToken() async -> String? {
        await MainActor.run { state.sessionInfo?.accessToken }
    }

    // MARK: - Public API

    /// Restauruje sesiju pri pokretanju iz Keychain.
    public func restoreSession() async {
        guard
            let token = keychain.read(forKey: SessionStorageKey.accessToken),
            let expiryString = preferences.string(forKey: SessionStorageKey.tokenExpiresAt),
            let expiryTimestamp = Double(expiryString),
            let userData = preferences.string(forKey: SessionStorageKey.userProfile),
            let profile = try? JSONDecoder().decode(UserProfile.self, from: Data(userData.utf8))
        else {
            state = .unauthenticated
            return
        }

        let expiry = Date(timeIntervalSince1970: expiryTimestamp)
        if expiry <= Date() {
            // Token istekao
            clearSession()
            state = .unauthenticated
            return
        }

        let info = SessionInfo(user: profile, accessToken: token, tokenExpiresAt: expiry)
        state = .authenticated(info)
    }

    /// Postavlja novu sesiju nakon uspesne prijave.
    public func establish(_ response: MobileTokenResponse) {
        let expiry = Date().addingTimeInterval(TimeInterval(response.expiresIn))
        persistSession(token: response.accessToken, expiry: expiry, user: response.user)
        let info = SessionInfo(user: response.user, accessToken: response.accessToken, tokenExpiresAt: expiry)
        state = .authenticated(info)
    }

    /// Azurira user profile bez promene tokena.
    public func updateUser(_ user: UserProfile) {
        guard case .authenticated(let info) = state else { return }
        persistUser(user)
        let updated = SessionInfo(user: user, accessToken: info.accessToken, tokenExpiresAt: info.tokenExpiresAt)
        state = .authenticated(updated)
    }

    /// Odjava – brise token i postavlja unauthenticated state.
    public func signOut() {
        clearSession()
        state = .unauthenticated
    }

    // MARK: - Private

    private func persistSession(token: String, expiry: Date, user: UserProfile) {
        try? keychain.save(token, forKey: SessionStorageKey.accessToken)
        preferences.set("\(expiry.timeIntervalSince1970)", forKey: SessionStorageKey.tokenExpiresAt)
        persistUser(user)
    }

    private func persistUser(_ user: UserProfile) {
        if let data = try? JSONEncoder().encode(user),
           let json = String(data: data, encoding: .utf8) {
            preferences.set(json, forKey: SessionStorageKey.userProfile)
        }
    }

    private func clearSession() {
        keychain.delete(forKey: SessionStorageKey.accessToken)
        preferences.remove(forKey: SessionStorageKey.tokenExpiresAt)
        preferences.remove(forKey: SessionStorageKey.userProfile)
    }
}
