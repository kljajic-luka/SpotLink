import Foundation

// MARK: - Auth Service

/// Servis za autentifikaciju i registraciju.
/// Komunicira sa SpotLink backendom preko /auth/* endpointa.
@MainActor
public final class AuthService: ObservableObject {

    private let apiClient: APIClientProtocol
    private let session: SessionManager

    public init(apiClient: APIClientProtocol, session: SessionManager) {
        self.apiClient = apiClient
        self.session = session
    }

    // MARK: - Login

    /// Prijava putem email/password. Cuva JWT token u Keychain.
    public func login(email: String, password: String) async throws {
        let request = MobileTokenRequest(email: email, password: password)
        let tokenResponse: MobileTokenResponse = try await apiClient.post("/auth/token", body: request)
        session.establish(tokenResponse)
    }

    /// Registracija novog kupca.
    public func registerCustomer(_ request: RegisterCustomerRequest) async throws {
        let _: AuthResponseEnvelope = try await apiClient.post("/auth/register/customer", body: request)
        let tokenResponse: MobileTokenResponse = try await apiClient.post("/auth/token", body: MobileTokenRequest(
            email: request.email, password: request.password))
        session.establish(tokenResponse)
    }

    /// Registracija novog operatora.
    public func registerOperator(_ request: RegisterOperatorRequest) async throws {
        let _: AuthResponseEnvelope = try await apiClient.post("/auth/register/operator", body: request)
        let tokenResponse: MobileTokenResponse = try await apiClient.post("/auth/token", body: MobileTokenRequest(
            email: request.email, password: request.password))
        session.establish(tokenResponse)
    }

    // MARK: - Password Reset

    public func requestPasswordReset(email: String) async throws {
        let _: NoContentResponse = try await apiClient.post(
            "/auth/password/reset-request",
            body: PasswordResetRequest(email: email))
    }

    public func completePasswordReset(token: String, newPassword: String) async throws {
        let _: NoContentResponse = try await apiClient.post(
            "/auth/password/reset",
            body: CompletePasswordResetRequest(token: token, newPassword: newPassword))
    }

    // MARK: - Logout

    public func logout() async {
        session.signOut()
    }

    // MARK: - Session Restore

    public func restoreSession() async {
        await session.restoreSession()
    }
}

// MARK: - Private Helpers

private struct AuthResponseEnvelope: Decodable {
    let authenticated: Bool
    let message: String?
}

private struct NoContentResponse: Decodable {}
