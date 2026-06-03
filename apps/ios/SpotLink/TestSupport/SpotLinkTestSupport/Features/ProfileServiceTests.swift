import Foundation
import Testing
@testable import SpotLinkCore

private final class ProfileMockAPIClient: APIClientProtocol, @unchecked Sendable {
    var postPath: String?
    var encodedPostBody: Data?
    private let ticket: SupportTicket

    init(ticket: SupportTicket) {
        self.ticket = ticket
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
        throw APIError.unknown(500, APIErrorContext(message: "Mock nije konfigurisan za PATCH"))
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
}
