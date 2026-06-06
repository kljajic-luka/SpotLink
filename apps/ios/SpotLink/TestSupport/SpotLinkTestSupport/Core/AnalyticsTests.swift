import Foundation
import Testing
@testable import SpotLinkCore

private final class AnalyticsMockAPIClient: APIClientProtocol, @unchecked Sendable {
    var postPath: String?
    var encodedPostBody: Data?
    var postCallCount = 0
    var error: Error?

    func get<T: Decodable>(_ path: String, query: [String: String]?) async throws -> T {
        throw APIError.unknown(500, APIErrorContext(message: "Mock nije konfigurisan za GET"))
    }

    func post<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        postPath = path
        encodedPostBody = try JSONEncoder().encode(body)
        postCallCount += 1
        if let error {
            throw error
        }
        guard let response = EmptyResponse() as? T else {
            throw APIError.decodingFailed("Neocekivan analytics response tip")
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

@Suite("Analytics privacy")
struct AnalyticsTests {

    @Test("analytics encodes backend batch shape")
    func encodesBackendBatchShape() async throws {
        let client = AnalyticsMockAPIClient()
        let service = AnalyticsService(
            apiClient: client,
            consent: .enabled,
            sessionId: "ios-session-test",
            dateProvider: { Date(timeIntervalSince1970: 0) }
        )

        await service.trackNow(.screenView(name: "search"))

        #expect(client.postPath == "/analytics/events")
        let event = try firstEncodedEvent(from: client)
        #expect(event["event"] as? String == "screen_view")
        #expect(event["timestamp"] as? String == "1970-01-01T00:00:00Z")
        #expect(event["sessionId"] as? String == "ios-session-test")
        #expect(event["eventName"] == nil)
        #expect(event["occurredAt"] == nil)
        #expect(event["url"] == nil)

        let properties = try #require(event["properties"] as? [String: Any])
        #expect(properties["screen"] as? String == "search")
    }

    @Test("analytics does not send when consent is disabled")
    func disabledConsentDoesNotSubmit() async {
        let client = AnalyticsMockAPIClient()
        let service = AnalyticsService(apiClient: client)

        await service.trackNow(.appOpen)

        #expect(client.postPath == nil)
        #expect(client.encodedPostBody == nil)
        #expect(client.postCallCount == 0)
    }

    @Test("analytics strips unsafe properties before submission")
    func stripsUnsafeProperties() async throws {
        let client = AnalyticsMockAPIClient()
        let service = AnalyticsService(
            apiClient: client,
            consent: .enabled,
            sessionId: "ios-session-safe",
            dateProvider: { Date(timeIntervalSince1970: 0) }
        )

        await service.trackNow(.custom(
            name: "screen_view",
            properties: [
                "screen": "profile",
                "email": "customer@spotlink.test",
                "context": "Bearer raw-token-value",
                "licensePlate": "BG-1234-AA",
                "description": "free-form sensitive text"
            ]
        ))

        let event = try firstEncodedEvent(from: client)
        let properties = try #require(event["properties"] as? [String: Any])
        #expect(properties["screen"] as? String == "profile")
        #expect(properties["email"] == nil)
        #expect(properties["context"] == nil)
        #expect(properties["licensePlate"] == nil)
        #expect(properties["description"] == nil)

        let encoded = String(data: try #require(client.encodedPostBody), encoding: .utf8) ?? ""
        #expect(!encoded.contains("customer@spotlink.test"))
        #expect(!encoded.contains("raw-token-value"))
        #expect(!encoded.contains("BG-1234-AA"))
        #expect(!encoded.contains("free-form sensitive text"))
    }

    @Test("analytics failures remain best effort and privacy safe")
    func failuresDoNotThrowIntoCallers() async throws {
        let client = AnalyticsMockAPIClient()
        client.error = APIError.serverError(
            500,
            APIErrorContext(message: "Bearer sensitive-error-token")
        )
        let service = AnalyticsService(
            apiClient: client,
            consent: .enabled,
            sessionId: "ios-session-failure",
            dateProvider: { Date(timeIntervalSince1970: 0) }
        )

        await service.trackNow(.error(
            name: "payment_failed",
            description: "customer@spotlink.test BG-1234-AA"
        ))

        #expect(client.postCallCount == 1)
        let encoded = String(data: try #require(client.encodedPostBody), encoding: .utf8) ?? ""
        #expect(encoded.contains("payment_failed"))
        #expect(!encoded.contains("customer@spotlink.test"))
        #expect(!encoded.contains("BG-1234-AA"))
        #expect(!encoded.contains("sensitive-error-token"))
    }

    private func firstEncodedEvent(from client: AnalyticsMockAPIClient) throws -> [String: Any] {
        let data = try #require(client.encodedPostBody)
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let events = try #require(object["events"] as? [[String: Any]])
        #expect(events.count == 1)
        return try #require(events.first)
    }
}
