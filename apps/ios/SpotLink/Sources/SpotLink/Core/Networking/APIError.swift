import Foundation

// MARK: - HTTP Method

public enum HTTPMethod: String, Sendable {
    case get    = "GET"
    case post   = "POST"
    case put    = "PUT"
    case patch  = "PATCH"
    case delete = "DELETE"
}

// MARK: - API Error

/// Mapira backend ApiErrorResponse na Swift error.
public enum APIError: Error, Sendable {
    case unauthorized
    case forbidden
    case notFound(String)
    case conflict(String)
    case validation([String: String])
    case serverError(Int, String)
    case offline
    case cancelled
    case decodingFailed(String)
    case unknown(Int, String)

    public var userFacingMessage: String {
        switch self {
        case .unauthorized:
            return "Niste prijavljeni. Prijavite se da biste nastavili."
        case .forbidden:
            return "Nemate dozvolu za ovu akciju."
        case .notFound(let msg):
            return msg.isEmpty ? "Trazeni resurs nije pronadjen." : msg
        case .conflict(let msg):
            return msg.isEmpty ? "Doslo je do konflikta. Pokusajte ponovo." : msg
        case .validation(let errors):
            return errors.values.joined(separator: "\n")
        case .serverError(_, let msg):
            return msg.isEmpty ? "Greska na serveru. Pokusajte ponovo." : msg
        case .offline:
            return "Nema internet veze. Proverite konekciju."
        case .cancelled:
            return "Zahtev je otkazan."
        case .decodingFailed(let detail):
            return "Greska pri obradi odgovora: \(detail)"
        case .unknown(let status, let msg):
            return "Nepoznata greska (\(status)): \(msg)"
        }
    }

    public var isAuthError: Bool {
        if case .unauthorized = self { return true }
        return false
    }
}

// MARK: - API Error Envelope

/// Odgovara backend ApiErrorResponse obliku.
struct APIErrorEnvelope: Decodable {
    let status: Int
    let code: String?
    let message: String
    let requestId: String?
    let details: [String: String]?

    enum CodingKeys: String, CodingKey {
        case status, code, message, requestId, details
    }
}

// MARK: - Paginated Response

public struct APIPage<T: Decodable & Sendable>: Decodable, Sendable {
    public let content: [T]
    public let totalElements: Int
    public let totalPages: Int
    public let page: Int
    public let size: Int

    public var isEmpty: Bool { content.isEmpty }
    public var hasMore: Bool { page + 1 < totalPages }
}
