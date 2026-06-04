import Foundation

// MARK: - API Client Protocol

/// Apstraktan interfejs za HTTP komunikaciju.
/// Olaksava testiranje – mock implementacija umesto pravog URLSession.
public protocol APIClientProtocol: Sendable {
    func get<T: Decodable>(_ path: String, query: [String: String]?) async throws -> T
    func post<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T
    func put<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T
    func patch<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T
    func delete(_ path: String) async throws
}

// MARK: - API Client

/// Tipizovan HTTP klijent koji komunicira sa SpotLink backendom.
/// - Automatski dodaje Authorization: Bearer header ako postoji token.
/// - Dekoduje backend APIErrorResponse u APIError.
/// - Dodaje X-Request-Id korelacioni header.
public final class APIClient: APIClientProtocol, @unchecked Sendable {

    private let session: URLSession
    private let baseURL: URL
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder
    private let tokenProvider: TokenProvider
    private let unauthorizedHandler: (@Sendable () async -> Void)?

    public convenience init(
        baseURL: URL,
        tokenProvider: TokenProvider,
        unauthorizedHandler: (@Sendable () async -> Void)? = nil
    ) {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        self.init(
            baseURL: baseURL,
            tokenProvider: tokenProvider,
            session: URLSession(configuration: config),
            unauthorizedHandler: unauthorizedHandler
        )
    }

    init(
        baseURL: URL,
        tokenProvider: TokenProvider,
        session: URLSession,
        unauthorizedHandler: (@Sendable () async -> Void)? = nil
    ) {
        self.baseURL = baseURL
        self.tokenProvider = tokenProvider
        self.unauthorizedHandler = unauthorizedHandler

        self.session = session

        self.encoder = JSONEncoder()
        self.encoder.dateEncodingStrategy = .iso8601
        self.encoder.keyEncodingStrategy = .useDefaultKeys

        self.decoder = .spotLinkBackend()
    }

    // MARK: APIClientProtocol

    public func get<T: Decodable>(_ path: String, query: [String: String]? = nil) async throws -> T {
        let request = try await buildRequest(.get, path: path, query: query, body: Optional<EmptyBody>.none)
        return try await send(request)
    }

    public func post<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        let request = try await buildRequest(.post, path: path, query: nil, body: body)
        return try await send(request)
    }

    public func put<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        let request = try await buildRequest(.put, path: path, query: nil, body: body)
        return try await send(request)
    }

    public func patch<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        let request = try await buildRequest(.patch, path: path, query: nil, body: body)
        return try await send(request)
    }

    public func delete(_ path: String) async throws {
        let request = try await buildRequest(.delete, path: path, query: nil, body: Optional<EmptyBody>.none)
        let _: EmptyResponse = try await send(request)
    }

    // MARK: - Private

    private func buildRequest<Body: Encodable>(
        _ method: HTTPMethod,
        path: String,
        query: [String: String]?,
        body: Body?
    ) async throws -> URLRequest {
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: true)!
        if let query = query, !query.isEmpty {
            components.queryItems = query.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        guard let url = components.url else {
            throw APIError.unknown(0, APIErrorContext(message: "Neispravna URL putanja: \(path)"))
        }

        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(RequestCorrelation.generateRequestId(), forHTTPHeaderField: "X-Request-Id")

        if let token = await tokenProvider.currentToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        if let body = body, !(body is EmptyBody?) {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try encoder.encode(body)
        }

        return request
    }

    private func send<T: Decodable>(_ request: URLRequest) async throws -> T {
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await session.data(for: request)
        } catch let urlError as URLError {
            if urlError.code == .cancelled {
                throw APIError.cancelled
            }
            throw APIError.offline
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.unknown(0, APIErrorContext(message: "Neocekivan tip odgovora"))
        }

        if (200..<300).contains(httpResponse.statusCode) {
            if allowsEmptySuccessResponse(data: data, statusCode: httpResponse.statusCode) {
                if T.self == EmptyResponse.self {
                    return EmptyResponse() as! T
                }
                throw APIError.decodingFailed("Ocekivao se sadrzaj ali je odgovor bio prazan")
            }

            do {
                return try decoder.decode(T.self, from: data)
            } catch {
                throw APIError.decodingFailed(error.localizedDescription)
            }
        }

        // Greska – pokusaj parsirati APIErrorEnvelope
        let errorEnvelope = try? decoder.decode(APIErrorEnvelope.self, from: data)
        let context = APIErrorContext(
            message: errorEnvelope?.message ?? HTTPURLResponse.localizedString(forStatusCode: httpResponse.statusCode),
            code: errorEnvelope?.code,
            requestId: errorEnvelope?.requestId ?? httpResponse.value(forHTTPHeaderField: "X-Request-Id"),
            details: errorEnvelope?.details ?? [:]
        )

        switch httpResponse.statusCode {
        case 401:
            await unauthorizedHandler?()
            throw APIError.unauthorized(context)
        case 403:
            throw APIError.forbidden
        case 404:
            throw APIError.notFound(context)
        case 409:
            throw APIError.conflict(context)
        case 422:
            throw APIError.validation(context)
        case 500...599:
            throw APIError.serverError(httpResponse.statusCode, context)
        default:
            throw APIError.unknown(httpResponse.statusCode, context)
        }
    }

    private func allowsEmptySuccessResponse(data: Data, statusCode: Int) -> Bool {
        guard statusCode == 202 || statusCode == 204 else {
            return false
        }
        return data.isEmpty || data.trimmingWhitespaceAndNewlines.isEmpty
    }
}

// MARK: - Token Provider

/// Apstrakcija za dostavljanje Bearer tokena.
/// SessionManager implementira ovaj protokol.
public protocol TokenProvider: Sendable {
    func currentToken() async -> String?
}

// MARK: - Helpers

private struct EmptyBody: Encodable {}

public struct EmptyResponse: Decodable, Sendable {
    public init() {}
}

private extension Data {
    var trimmingWhitespaceAndNewlines: Data {
        guard let string = String(data: self, encoding: .utf8) else { return self }
        return Data(string.trimmingCharacters(in: .whitespacesAndNewlines).utf8)
    }
}
