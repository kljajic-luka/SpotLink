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
    public let refreshToken: String
    public let tokenExpiresAt: Date
    public let refreshExpiresAt: Date

    public var isExpired: Bool { Date() >= tokenExpiresAt }
    public var isExpiringSoon: Bool { Date().addingTimeInterval(300) >= tokenExpiresAt }
    public var isRefreshExpired: Bool { Date() >= refreshExpiresAt }
}

// MARK: - Session Keys

enum SessionStorageKey {
    static let accessToken = "session.accessToken"
    static let refreshToken = "session.refreshToken"
    static let tokenExpiresAt = "session.tokenExpiresAt"
    static let refreshExpiresAt = "session.refreshExpiresAt"
    static let userProfile = "session.userProfile"
}

// MARK: - Session Manager

/// Centralni manager sesije za iOS app.
/// Cuva JWT token u Keychain, user profile u UserDefaults.
/// Implementira TokenProvider protokol za APIClient.
@MainActor
public final class SessionManager: ObservableObject, TokenProvider {

    public static let shared = SessionManager()
    public static let remoteUnauthorizedNotice = "Sesija je zavrsena. Ako je zahtev za brisanje naloga obradjen, nalog vise nije aktivan."

    @Published public private(set) var state: SessionState = .loading
    @Published public private(set) var signOutNotice: String?

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
        await refreshableAccessToken()
    }

    // MARK: - Public API

    /// Restauruje sesiju pri pokretanju iz Keychain.
    public func restoreSession() async {
        guard
            let token = keychain.read(forKey: SessionStorageKey.accessToken),
            let refreshToken = keychain.read(forKey: SessionStorageKey.refreshToken),
            let expiryString = preferences.string(forKey: SessionStorageKey.tokenExpiresAt),
            let expiryTimestamp = Double(expiryString),
            let refreshExpiryString = preferences.string(forKey: SessionStorageKey.refreshExpiresAt),
            let refreshExpiryTimestamp = Double(refreshExpiryString),
            let userData = preferences.string(forKey: SessionStorageKey.userProfile),
            let profile = try? JSONDecoder().decode(UserProfile.self, from: Data(userData.utf8))
        else {
            state = .unauthenticated
            return
        }

        let expiry = Date(timeIntervalSince1970: expiryTimestamp)
        let refreshExpiry = Date(timeIntervalSince1970: refreshExpiryTimestamp)
        if refreshExpiry <= Date() {
            // Refresh token istekao
            clearSession()
            state = .unauthenticated
            return
        }

        let info = SessionInfo(
            user: profile,
            accessToken: token,
            refreshToken: refreshToken,
            tokenExpiresAt: expiry,
            refreshExpiresAt: refreshExpiry)
        state = .authenticated(info)

        if expiry <= Date() {
            _ = await refreshAccessToken(refreshToken)
        }
    }

    /// Postavlja novu sesiju nakon uspesne prijave.
    public func establish(_ response: MobileTokenResponse) {
        let now = Date()
        let expiry = response.expiresAt ?? now.addingTimeInterval(TimeInterval(response.expiresIn))
        let refreshExpiry = response.refreshExpiresAt
            ?? now.addingTimeInterval(TimeInterval(response.refreshExpiresInSeconds ?? 0))
        persistSession(
            token: response.accessToken,
            refreshToken: response.refreshToken,
            expiry: expiry,
            refreshExpiry: refreshExpiry,
            user: response.user)
        let info = SessionInfo(
            user: response.user,
            accessToken: response.accessToken,
            refreshToken: response.refreshToken,
            tokenExpiresAt: expiry,
            refreshExpiresAt: refreshExpiry)
        state = .authenticated(info)
    }

    /// Azurira user profile bez promene tokena.
    public func updateUser(_ user: UserProfile) {
        guard case .authenticated(let info) = state else { return }
        persistUser(user)
        let updated = SessionInfo(
            user: user,
            accessToken: info.accessToken,
            refreshToken: info.refreshToken,
            tokenExpiresAt: info.tokenExpiresAt,
            refreshExpiresAt: info.refreshExpiresAt)
        state = .authenticated(updated)
    }

    public func currentRefreshToken() -> String? {
        state.sessionInfo?.refreshToken ?? keychain.read(forKey: SessionStorageKey.refreshToken)
    }

    /// Odjava – brise token i postavlja unauthenticated state.
    public func signOut(notice: String? = nil) {
        clearSession()
        signOutNotice = notice
        state = .unauthenticated
    }

    public func handleRemoteUnauthorized() {
        guard state.isAuthenticated else { return }
        signOut(notice: Self.remoteUnauthorizedNotice)
    }

    public func clearSignOutNotice() {
        signOutNotice = nil
    }

    // MARK: - Private

    private func persistSession(token: String, refreshToken: String, expiry: Date, refreshExpiry: Date, user: UserProfile) {
        try? keychain.save(token, forKey: SessionStorageKey.accessToken)
        try? keychain.save(refreshToken, forKey: SessionStorageKey.refreshToken)
        preferences.set("\(expiry.timeIntervalSince1970)", forKey: SessionStorageKey.tokenExpiresAt)
        preferences.set("\(refreshExpiry.timeIntervalSince1970)", forKey: SessionStorageKey.refreshExpiresAt)
        persistUser(user)
    }

    private func refreshableAccessToken() async -> String? {
        guard let info = state.sessionInfo else { return nil }
        if info.isRefreshExpired {
            signOut()
            return nil
        }
        guard info.isExpiringSoon else { return info.accessToken }
        return await refreshAccessToken(info.refreshToken)
    }

    private func refreshAccessToken(_ refreshToken: String) async -> String? {
        let url = AppEnvironment.current().apiBaseURL.appendingPathComponent("auth/token/refresh")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let encoder = JSONEncoder()
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601

        do {
            request.httpBody = try encoder.encode(RefreshTokenRequest(refreshToken: refreshToken))
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                signOut(notice: Self.remoteUnauthorizedNotice)
                return nil
            }
            let tokenResponse = try decoder.decode(MobileTokenResponse.self, from: data)
            establish(tokenResponse)
            return tokenResponse.accessToken
        } catch {
            signOut(notice: Self.remoteUnauthorizedNotice)
            return nil
        }
    }

    private func persistUser(_ user: UserProfile) {
        if let data = try? JSONEncoder().encode(user),
           let json = String(data: data, encoding: .utf8) {
            preferences.set(json, forKey: SessionStorageKey.userProfile)
        }
    }

    private func clearSession() {
        keychain.delete(forKey: SessionStorageKey.accessToken)
        keychain.delete(forKey: SessionStorageKey.refreshToken)
        preferences.remove(forKey: SessionStorageKey.tokenExpiresAt)
        preferences.remove(forKey: SessionStorageKey.refreshExpiresAt)
        preferences.remove(forKey: SessionStorageKey.userProfile)
    }
}
