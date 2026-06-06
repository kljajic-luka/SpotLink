import Foundation
import Testing
@testable import SpotLinkCore

private final class ProfileMockAPIClient: APIClientProtocol, @unchecked Sendable {
    var postPath: String?
    var encodedPostBody: Data?
    var patchPath: String?
    var encodedPatchBody: Data?
    private let ticket: SupportTicket
    private let profile: UserProfileDetails?

    init(ticket: SupportTicket, profile: UserProfileDetails? = nil) {
        self.ticket = ticket
        self.profile = profile
    }

    func get<T: Decodable>(_ path: String, query: [String: String]?) async throws -> T {
        throw APIError.unknown(500, APIErrorContext(message: "Mock nije konfigurisan za GET"))
    }

    func post<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        postPath = path
        encodedPostBody = try JSONEncoder().encode(body)
        guard let response = ticket as? T else {
            throw APIError.decodingFailed("Neocekivan tip odgovora u mock ProfileService testu")
        }
        return response
    }

    func put<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        throw APIError.unknown(500, APIErrorContext(message: "Mock nije konfigurisan za PUT"))
    }

    func patch<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        patchPath = path
        encodedPatchBody = try JSONEncoder().encode(body)
        guard let response = profile as? T else {
            throw APIError.decodingFailed("Neocekivan tip odgovora u mock ProfileService PATCH testu")
        }
        return response
    }

    func delete(_ path: String) async throws {
        throw APIError.unknown(500, APIErrorContext(message: "Mock nije konfigurisan za DELETE"))
    }
}

@Suite("ProfileService")
struct ProfileServiceTests {

    @Test("account deletion request posts to current user endpoint with empty body")
    func accountDeletionRequestPostsExpectedShape() async throws {
        let ticket = SupportTicket(
            id: "11111111-1111-1111-1111-111111111111",
            category: .account,
            status: .open,
            subject: "Account deletion request",
            reservationId: nil,
            locationId: nil,
            createdAt: Date(timeIntervalSince1970: 0),
            updatedAt: Date(timeIntervalSince1970: 0)
        )
        let client = ProfileMockAPIClient(ticket: ticket)
        let service = ProfileService(apiClient: client)

        let response = try await service.requestAccountDeletion()

        #expect(response.id == ticket.id)
        #expect(response.category == .account)
        #expect(client.postPath == "/users/me/deletion-request")

        guard let encodedPostBody = client.encodedPostBody else {
            Issue.record("Ocekivano je JSON telo za account deletion request")
            return
        }
        let object = try JSONSerialization.jsonObject(with: encodedPostBody) as? [String: Any]
        #expect(object?.isEmpty == true)
    }

    @Test("notification preference update patches current user profile with partial preference body")
    func notificationPreferencePatchEncodesExpectedShape() async throws {
        let ticket = SupportTicket(
            id: "11111111-1111-1111-1111-111111111111",
            category: .account,
            status: .open,
            subject: "Account deletion request",
            reservationId: nil,
            locationId: nil,
            createdAt: Date(timeIntervalSince1970: 0),
            updatedAt: Date(timeIntervalSince1970: 0)
        )
        let profile = UserProfileDetails(
            id: "22222222-2222-2222-2222-222222222222",
            email: "customer@spotlink.test",
            firstName: "Mila",
            lastName: "Ilic",
            phone: nil,
            avatarUrl: nil,
            bio: nil,
            roles: [.customer],
            operatorId: nil,
            registrationStatus: "ACTIVE",
            createdAt: "2026-06-05T13:00:52.000Z",
            stats: ProfileStats(
                completedReservations: 3,
                activeVehicles: 1,
                savedLocations: 2,
                supportTickets: 0
            ),
            preferences: UserPreferences(
                locale: "sr-RS",
                marketingOptIn: false,
                reservationAlerts: false,
                paymentAlerts: true,
                supportAlerts: true
            )
        )
        let client = ProfileMockAPIClient(ticket: ticket, profile: profile)
        let service = ProfileService(apiClient: client)

        let response = try await service.updateNotificationPreferences(UpdateUserPreferencesRequest(
            marketingOptIn: false,
            reservationAlerts: false,
            paymentAlerts: true
        ))

        #expect(response.preferences.reservationAlerts == false)
        #expect(response.preferences.paymentAlerts == true)
        #expect(client.patchPath == "/users/me/profile")

        guard let encodedPatchBody = client.encodedPatchBody else {
            Issue.record("Ocekivano je JSON telo za notification preferences PATCH")
            return
        }
        let object = try #require(JSONSerialization.jsonObject(with: encodedPatchBody) as? [String: Any])
        let preferences = try #require(object["preferences"] as? [String: Any])

        #expect(object["firstName"] == nil)
        #expect(object["phone"] == nil)
        #expect(preferences["marketingOptIn"] as? Bool == false)
        #expect(preferences["reservationAlerts"] as? Bool == false)
        #expect(preferences["paymentAlerts"] as? Bool == true)
        #expect(preferences["supportAlerts"] == nil)
        #expect(preferences["locale"] == nil)
    }
}
