import Foundation

// MARK: - Auth Service

/// Servis za autentifikaciju i registraciju.
/// Komunicira sa SpotLink backendom preko /auth/* endpointa.
@MainActor
public final class AuthService: ObservableObject {

    private let apiClient: APIClientProtocol
    private let session: SessionManager
    private let pushLifecycle: PushDeviceTokenLifecycle?

    public init(
        apiClient: APIClientProtocol,
        session: SessionManager,
        pushLifecycle: PushDeviceTokenLifecycle? = nil
    ) {
        self.apiClient = apiClient
        self.session = session
        self.pushLifecycle = pushLifecycle
    }

    // MARK: - Login

    /// Prijava putem email/password. Cuva JWT token u Keychain.
    public func login(email: String, password: String) async throws {
        let request = MobileTokenRequest(email: email, password: password)
        let tokenResponse: MobileTokenResponse = try await apiClient.post("/auth/token", body: request)
        session.establish(tokenResponse)
        await syncPushTokenIfAuthenticated()
    }

    /// Registracija novog kupca.
    public func registerCustomer(_ request: RegisterCustomerRequest) async throws {
        let _: AuthResponseEnvelope = try await apiClient.post("/auth/register/customer", body: request)
        let tokenResponse: MobileTokenResponse = try await apiClient.post("/auth/token", body: MobileTokenRequest(
            email: request.email, password: request.password))
        session.establish(tokenResponse)
        await syncPushTokenIfAuthenticated()
    }

    /// Registracija novog operatora.
    public func registerOperator(_ request: RegisterOperatorRequest) async throws {
        let _: AuthResponseEnvelope = try await apiClient.post("/auth/register/operator", body: request)
        let tokenResponse: MobileTokenResponse = try await apiClient.post("/auth/token", body: MobileTokenRequest(
            email: request.email, password: request.password))
        session.establish(tokenResponse)
        await syncPushTokenIfAuthenticated()
    }

    // MARK: - Password Reset

    public func requestPasswordReset(email: String) async throws {
        let _: EmptyResponse = try await apiClient.post(
            "/auth/password/reset-request",
            body: PasswordResetRequest(email: email))
    }

    public func completePasswordReset(token: String, newPassword: String) async throws {
        let _: EmptyResponse = try await apiClient.post(
            "/auth/password/reset",
            body: CompletePasswordResetRequest(token: token, newPassword: newPassword))
    }

    // MARK: - Logout

    public func logout() async {
        await pushLifecycle?.unregisterKnownDeviceTokenBeforeLogout()
        if let refreshToken = session.currentRefreshToken() {
            do {
                let _: EmptyResponse = try await apiClient.post(
                    "/auth/token/revoke",
                    body: RevokeTokenRequest(refreshToken: refreshToken))
            } catch {
                // Local sign-out should still proceed if server revocation cannot complete.
            }
        }
        session.signOut()
    }

    public func refreshSession() async throws {
        guard let refreshToken = session.currentRefreshToken() else {
            throw APIError.unauthorized()
        }
        let tokenResponse: MobileTokenResponse = try await apiClient.post(
            "/auth/token/refresh",
            body: RefreshTokenRequest(refreshToken: refreshToken))
        session.establish(tokenResponse)
        await syncPushTokenIfAuthenticated()
    }

    // MARK: - Session Restore

    public func restoreSession() async {
        await session.restoreSession()
        await syncPushTokenIfAuthenticated()
    }

    private func syncPushTokenIfAuthenticated() async {
        guard session.state.isAuthenticated else { return }
        await pushLifecycle?.uploadKnownDeviceTokenIfAuthenticated()
    }
}

// MARK: - Private Helpers

private struct AuthResponseEnvelope: Decodable {
    let authenticated: Bool
    let message: String?
}
