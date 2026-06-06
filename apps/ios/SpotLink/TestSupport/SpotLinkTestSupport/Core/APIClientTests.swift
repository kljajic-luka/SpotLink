import Foundation
import Testing
@testable import SpotLinkCore

private actor MockHTTPResponseStore {
    static let shared = MockHTTPResponseStore()

    private var responses: [String: MockHTTPResponse] = [:]

    func set(_ response: MockHTTPResponse, for url: URL) {
        responses[url.absoluteString] = response
    }

    func take(for url: URL) -> MockHTTPResponse? {
        responses.removeValue(forKey: url.absoluteString)
    }
}

private struct MockHTTPResponse: Sendable {
    let statusCode: Int
    let headers: [String: String]
    let body: Data
}

private final class MockURLProtocol: URLProtocol, @unchecked Sendable {
    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let client, let url = request.url else {
            return
        }

        Task {
            guard let mockResponse = await MockHTTPResponseStore.shared.take(for: url) else {
                client.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
                return
            }

            let response = HTTPURLResponse(
                url: url,
                statusCode: mockResponse.statusCode,
                httpVersion: nil,
                headerFields: mockResponse.headers
            )!
            client.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client.urlProtocol(self, didLoad: mockResponse.body)
            client.urlProtocolDidFinishLoading(self)
        }
    }

    override func stopLoading() {}
}

private struct TestTokenProvider: TokenProvider {
    func currentToken() async -> String? {
        nil
    }
}

@Suite("APIClient - empty error handling")
struct APIClientTests {

    private let baseURL = URL(string: "https://spotlink.test")!

    @Test("204 prazan odgovor vraca EmptyResponse samo u uspešnom statusu")
    func empty204ReturnsTypedEmptyResponse() async throws {
        let path = "notifications/read-\(UUID().uuidString)"
        let url = baseURL.appendingPathComponent(path)
        await MockHTTPResponseStore.shared.set(
            MockHTTPResponse(statusCode: 204, headers: [:], body: Data("   ".utf8)),
            for: url
        )

        let client = makeClient()
        let response: EmptyResponse = try await client.get(path, query: nil)

        #expect(type(of: response) == EmptyResponse.self)
    }

    @Test("200 prazan odgovor vise ne prolazi kao EmptyResponse")
    func empty200DoesNotBypassDecoding() async {
        let path = "empty-200-\(UUID().uuidString)"
        let url = baseURL.appendingPathComponent(path)
        await MockHTTPResponseStore.shared.set(
            MockHTTPResponse(statusCode: 200, headers: [:], body: Data(" \n ".utf8)),
            for: url
        )

        let client = makeClient()

        do {
            let _: EmptyResponse = try await client.get(path, query: nil)
            Issue.record("Ocekivana je decoding greska za 200 prazan odgovor")
        } catch let error as APIError {
            if case .decodingFailed = error {
                return
            }
            Issue.record("Ocekivana je decoding greska, dobijeno: \(String(describing: error))")
        } catch {
            Issue.record("Ocekivan je APIError.decodingFailed, dobijeno: \(error)")
        }
    }

    @Test("404 prazan odgovor i dalje mapira notFound gresku")
    func empty404MapsToNotFound() async {
        let path = "missing-\(UUID().uuidString)"
        let url = baseURL.appendingPathComponent(path)
        await MockHTTPResponseStore.shared.set(
            MockHTTPResponse(statusCode: 404, headers: [:], body: Data("   ".utf8)),
            for: url
        )

        let client = makeClient()

        do {
            let _: EmptyResponse = try await client.get(path, query: nil)
            Issue.record("Ocekivana je notFound greska")
        } catch let error as APIError {
            if case .notFound = error {
                return
            }
            Issue.record("Ocekivana je notFound greska, dobijeno: \(String(describing: error))")
        } catch {
            Issue.record("Ocekivan je APIError.notFound, dobijeno: \(error)")
        }
    }

    @Test("500 prazan odgovor i dalje mapira server gresku")
    func empty500MapsToServerError() async {
        let path = "server-\(UUID().uuidString)"
        let url = baseURL.appendingPathComponent(path)
        await MockHTTPResponseStore.shared.set(
            MockHTTPResponse(statusCode: 500, headers: [:], body: Data()),
            for: url
        )

        let client = makeClient()

        do {
            let _: EmptyResponse = try await client.get(path, query: nil)
            Issue.record("Ocekivana je server greska")
        } catch let error as APIError {
            if case .serverError(500, _) = error {
                return
            }
            Issue.record("Ocekivana je server greska 500, dobijeno: \(String(describing: error))")
        } catch {
            Issue.record("Ocekivan je APIError.serverError, dobijeno: \(error)")
        }
    }

    @Test("423 auth lockout mapira jasnu korisnicku poruku")
    func authLockoutMapsToLockedMessage() async {
        let path = "auth/token-\(UUID().uuidString)"
        let url = baseURL.appendingPathComponent(path)
        await MockHTTPResponseStore.shared.set(
            MockHTTPResponse(
                statusCode: 423,
                headers: ["X-Request-Id": "req-lockout"],
                body: Data("""
                        {
                          "status": 423,
                          "code": "AUTH_TEMPORARILY_LOCKED",
                          "message": "Too many failed sign-in attempts. Try again later.",
                          "requestId": "req-lockout",
                          "details": {"retryAfterSeconds": "60"},
                          "timestamp": "2026-06-06T12:00:00Z",
                          "path": "/auth/token"
                        }
                        """.utf8)
            ),
            for: url
        )

        let client = makeClient()

        do {
            let _: EmptyResponse = try await client.post(path, body: ["noop": "true"])
            Issue.record("Ocekivana je locked auth greska")
        } catch let error as APIError {
            guard case .locked = error else {
                Issue.record("Ocekivana je APIError.locked, dobijeno: \(String(describing: error))")
                return
            }
            #expect(error.code == "AUTH_TEMPORARILY_LOCKED")
            #expect(error.requestId == "req-lockout")
            #expect(error.userFacingMessage == "Nalog je privremeno zakljucan zbog vise neuspesnih pokusaja prijave. Pokusajte ponovo kasnije.")
            #expect(error.userFacingMessageWithReference.contains("Ref: req-lockout"))
        } catch {
            Issue.record("Ocekivan je APIError.locked, dobijeno: \(error)")
        }
    }

    @Test("401 invalid credentials ostaje standardna auth greska")
    func invalidCredentialsRemainUnauthorized() async {
        let path = "auth/login-\(UUID().uuidString)"
        let url = baseURL.appendingPathComponent(path)
        await MockHTTPResponseStore.shared.set(
            MockHTTPResponse(
                statusCode: 401,
                headers: ["X-Request-Id": "req-invalid"],
                body: Data("""
                        {
                          "status": 401,
                          "code": "UNAUTHORIZED",
                          "message": "Invalid email or password",
                          "requestId": "req-invalid",
                          "timestamp": "2026-06-06T12:00:00Z",
                          "path": "/auth/login"
                        }
                        """.utf8)
            ),
            for: url
        )

        let client = makeClient()

        do {
            let _: EmptyResponse = try await client.post(path, body: ["noop": "true"])
            Issue.record("Ocekivana je unauthorized auth greska")
        } catch let error as APIError {
            guard case .unauthorized = error else {
                Issue.record("Ocekivana je APIError.unauthorized, dobijeno: \(String(describing: error))")
                return
            }
            #expect(error.code == "UNAUTHORIZED")
            #expect(error.userFacingMessage == "Niste prijavljeni. Prijavite se da biste nastavili.")
        } catch {
            Issue.record("Ocekivan je APIError.unauthorized, dobijeno: \(error)")
        }
    }

    private func makeClient() -> APIClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        return APIClient(baseURL: baseURL, tokenProvider: TestTokenProvider(), session: session)
    }
}
