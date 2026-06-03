import Foundation
import Testing
@testable import SpotLinkCore

private final class NotificationTokenMockAPIClient: APIClientProtocol, @unchecked Sendable {
    var postPath: String?
    var encodedPostBody: Data?

    func get<T: Decodable>(_ path: String, query: [String: String]?) async throws -> T {
        throw APIError.unknown(500, APIErrorContext(message: "Mock nije konfigurisan za GET"))
    }

    func post<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        postPath = path
        encodedPostBody = try JSONEncoder().encode(body)
        guard T.self == EmptyResponse.self, let response = EmptyResponse() as? T else {
            throw APIError.decodingFailed("Neocekivan tip odgovora u mock NotificationService testu")
        }
        return response
    }

    func put<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        throw APIError.unknown(500, APIErrorContext(message: "Mock nije konfigurisan za PUT"))
    }

    func patch<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        throw APIError.unknown(500, APIErrorContext(message: "Mock nije konfigurisan za PATCH"))
    }

    func delete(_ path: String) async throws {
        throw APIError.unknown(500, APIErrorContext(message: "Mock nije konfigurisan za DELETE"))
    }
}

private final class PushTokenServiceSpy: DeviceTokenLifecycleServicing, @unchecked Sendable {
    var registeredTokens: [String] = []
    var unregisteredTokens: [String] = []
    var failUnregister = false

    func registerDeviceToken(_ token: String) async throws {
        registeredTokens.append(token)
    }

    func unregisterDeviceToken(_ token: String) async throws {
        if failUnregister {
            throw APIError.offline
        }
        unregisteredTokens.append(token)
    }
}

@MainActor
private final class AuthPushLifecycleSpy: PushDeviceTokenLifecycle {
    var uploadCount = 0
    var unregisterCount = 0

    func uploadKnownDeviceTokenIfAuthenticated() async {
        uploadCount += 1
    }

    func unregisterKnownDeviceTokenBeforeLogout() async {
        unregisterCount += 1
    }
}

private final class AuthTokenMockAPIClient: APIClientProtocol, @unchecked Sendable {
    var postedPaths: [String] = []
    var failRevoke = false
    let tokenResponse: MobileTokenResponse

    init(tokenResponse: MobileTokenResponse = .testValue()) {
        self.tokenResponse = tokenResponse
    }

    func get<T: Decodable>(_ path: String, query: [String: String]?) async throws -> T {
        throw APIError.unknown(500, APIErrorContext(message: "Mock nije konfigurisan za GET"))
    }

    func post<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        postedPaths.append(path)
        if path == "/auth/token" {
            guard let response = tokenResponse as? T else {
                throw APIError.decodingFailed("Neocekivan token response tip")
            }
            return response
        }
        if path == "/auth/token/revoke", failRevoke {
            throw APIError.offline
        }
        guard T.self == EmptyResponse.self, let response = EmptyResponse() as? T else {
            throw APIError.decodingFailed("Neocekivan tip odgovora u mock AuthService testu")
        }
        return response
    }

    func put<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        throw APIError.unknown(500, APIErrorContext(message: "Mock nije konfigurisan za PUT"))
    }

    func patch<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        throw APIError.unknown(500, APIErrorContext(message: "Mock nije konfigurisan za PATCH"))
    }

    func delete(_ path: String) async throws {
        throw APIError.unknown(500, APIErrorContext(message: "Mock nije konfigurisan za DELETE"))
    }
}

@Suite("NotificationService device token lifecycle")
struct NotificationServiceTokenLifecycleTests {

    @Test("register device token posts expected endpoint and body")
    func registerDeviceTokenPostsExpectedShape() async throws {
        let client = NotificationTokenMockAPIClient()
        let service = NotificationService(apiClient: client)

        try await service.registerDeviceToken("apns-token")

        #expect(client.postPath == "/notifications/device-tokens")
        try assertDeviceTokenBody(client.encodedPostBody, expectedToken: "apns-token")
    }

    @Test("unregister device token posts expected endpoint and body")
    func unregisterDeviceTokenPostsExpectedShape() async throws {
        let client = NotificationTokenMockAPIClient()
        let service = NotificationService(apiClient: client)

        try await service.unregisterDeviceToken("apns-token")

        #expect(client.postPath == "/notifications/device-tokens/unregister")
        try assertDeviceTokenBody(client.encodedPostBody, expectedToken: "apns-token")
    }

    private func assertDeviceTokenBody(_ data: Data?, expectedToken: String) throws {
        guard let data else {
            Issue.record("Ocekivano je JSON telo za device token zahtev")
            return
        }
        let object = try JSONSerialization.jsonObject(with: data) as? [String: String]
        #expect(object?["deviceToken"] == expectedToken)
        #expect(object?["platform"] == "IOS")
    }
}

@Suite("PushNotificationManager token lifecycle")
@MainActor
struct PushNotificationManagerTokenLifecycleTests {

    @Test("didRegister persists token and uploads it without logging raw token")
    func didRegisterPersistsAndUploadsToken() async {
        let preferences = makePreferences()
        let service = PushTokenServiceSpy()
        let manager = PushNotificationManager(notificationService: service, preferences: preferences)

        await manager.didRegisterDeviceToken(Data([0xde, 0xad, 0xbe, 0xef]))

        #expect(manager.deviceToken == "deadbeef")
        #expect(service.registeredTokens == ["deadbeef"])

        let restarted = PushNotificationManager(notificationService: service, preferences: preferences)
        #expect(restarted.deviceToken == "deadbeef")
    }

    @Test("known token uploads after authenticated session restore")
    func knownTokenUploadsAfterAuthenticatedRestore() async {
        let preferences = makePreferences()
        let service = PushTokenServiceSpy()
        let manager = PushNotificationManager(notificationService: service, preferences: preferences)

        await manager.didRegisterDeviceToken(Data([0xca, 0xfe]))
        service.registeredTokens.removeAll()

        let restarted = PushNotificationManager(notificationService: service, preferences: preferences)
        await restarted.uploadKnownDeviceTokenIfAuthenticated()

        #expect(service.registeredTokens == ["cafe"])
    }

    @Test("successful unregister clears local token state")
    func successfulUnregisterClearsLocalState() async {
        let preferences = makePreferences()
        let service = PushTokenServiceSpy()
        let manager = PushNotificationManager(notificationService: service, preferences: preferences)

        await manager.didRegisterDeviceToken(Data([0xab, 0xcd]))
        await manager.unregisterKnownDeviceTokenBeforeLogout()

        #expect(service.unregisteredTokens == ["abcd"])
        #expect(manager.deviceToken == nil)

        let restarted = PushNotificationManager(notificationService: service, preferences: preferences)
        #expect(restarted.deviceToken == nil)
    }

    @Test("failed unregister keeps token for later retry")
    func failedUnregisterKeepsTokenForRetry() async {
        let preferences = makePreferences()
        let service = PushTokenServiceSpy()
        service.failUnregister = true
        let manager = PushNotificationManager(notificationService: service, preferences: preferences)

        await manager.didRegisterDeviceToken(Data([0x12, 0x34]))
        await manager.unregisterKnownDeviceTokenBeforeLogout()

        #expect(manager.deviceToken == "1234")

        let restarted = PushNotificationManager(notificationService: service, preferences: preferences)
        #expect(restarted.deviceToken == "1234")
    }

    private func makePreferences() -> PreferenceStorage {
        let suiteName = "spotlink.push-token.tests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)
        return PreferenceStorage(prefix: "tests.", defaults: defaults)
    }
}

@Suite("AuthService push token lifecycle")
@MainActor
struct AuthServicePushTokenLifecycleTests {

    @Test("login reuploads known push token after establishing session")
    func loginReuploadsKnownPushToken() async throws {
        let session = makeSession()
        let pushLifecycle = AuthPushLifecycleSpy()
        let client = AuthTokenMockAPIClient()
        let service = AuthService(apiClient: client, session: session, pushLifecycle: pushLifecycle)

        try await service.login(email: "user@spotlink.test", password: "CorrectHorse123")

        #expect(session.state.isAuthenticated)
        #expect(pushLifecycle.uploadCount == 1)
    }

    @Test("logout attempts push unregister and still signs out if revoke fails")
    func logoutAttemptsPushUnregisterAndStillSignsOut() async {
        let session = makeSession()
        session.establish(.testValue())
        let pushLifecycle = AuthPushLifecycleSpy()
        let client = AuthTokenMockAPIClient()
        client.failRevoke = true
        let service = AuthService(apiClient: client, session: session, pushLifecycle: pushLifecycle)

        await service.logout()

        #expect(pushLifecycle.unregisterCount == 1)
        #expect(!session.state.isAuthenticated)
        #expect(client.postedPaths.contains("/auth/token/revoke"))
    }

    private func makeSession() -> SessionManager {
        let suiteName = "spotlink.auth-push.tests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)
        return SessionManager(
            keychain: KeychainStorage(service: suiteName),
            preferences: PreferenceStorage(prefix: "tests.", defaults: defaults)
        )
    }
}

private extension MobileTokenResponse {
    static func testValue() -> MobileTokenResponse {
        MobileTokenResponse(
            accessToken: "access-token",
            refreshToken: "refresh-token",
            expiresIn: 900,
            expiresInSeconds: 900,
            refreshExpiresInSeconds: 2_592_000,
            issuedAt: Date(timeIntervalSince1970: 0),
            expiresAt: Date(timeIntervalSinceNow: 900),
            refreshExpiresAt: Date(timeIntervalSinceNow: 2_592_000),
            tokenType: "Bearer",
            user: UserProfile(
                id: "user-1",
                email: "user@spotlink.test",
                firstName: "Push",
                lastName: "Tester",
                phone: nil,
                avatarUrl: nil,
                bio: nil,
                roles: [.customer],
                operatorId: nil,
                registrationStatus: "ACTIVE",
                createdAt: nil
            )
        )
    }
}
