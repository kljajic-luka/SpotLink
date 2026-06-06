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

public struct APIErrorContext: Sendable, Equatable {
    public let message: String
    public let code: String?
    public let requestId: String?
    public let details: [String: String]

    public init(
        message: String,
        code: String? = nil,
        requestId: String? = nil,
        details: [String: String] = [:]
    ) {
        self.message = message
        self.code = code
        self.requestId = requestId
        self.details = details
    }
}

/// Mapira backend ApiErrorResponse na Swift error.
public enum APIError: Error, Sendable {
    case unauthorized(APIErrorContext? = nil)
    case forbidden
    case notFound(APIErrorContext)
    case conflict(APIErrorContext)
    case locked(APIErrorContext)
    case validation(APIErrorContext)
    case serverError(Int, APIErrorContext)
    case offline
    case cancelled
    case decodingFailed(String)
    case unknown(Int, APIErrorContext)

    public var userFacingMessage: String {
        switch self {
        case .unauthorized:
            return "Niste prijavljeni. Prijavite se da biste nastavili."
        case .forbidden:
            return "Nemate dozvolu za ovu akciju."
        case .notFound(let context):
            return context.message.isEmpty ? "Trazeni resurs nije pronadjen." : context.message
        case .conflict(let context):
            return context.message.isEmpty ? "Doslo je do konflikta. Pokusajte ponovo." : context.message
        case .locked:
            return "Nalog je privremeno zakljucan zbog vise neuspesnih pokusaja prijave. Pokusajte ponovo kasnije."
        case .validation(let context):
            if !context.details.isEmpty {
                return context.details.values.joined(separator: "\n")
            }
            return context.message.isEmpty ? "Proverite unete podatke." : context.message
        case .serverError(_, let context):
            return context.message.isEmpty ? "Greska na serveru. Pokusajte ponovo." : context.message
        case .offline:
            return "Nema internet veze. Proverite konekciju."
        case .cancelled:
            return "Zahtev je otkazan."
        case .decodingFailed(let detail):
            return "Greska pri obradi odgovora: \(detail)"
        case .unknown(let status, let context):
            return "Nepoznata greska (\(status)): \(context.message)"
        }
    }

    public var isAuthError: Bool {
        if case .unauthorized = self { return true }
        return false
    }

    public var code: String? {
        switch self {
        case .unauthorized(let context):
            return context?.code
        case .notFound(let context), .conflict(let context), .locked(let context), .validation(let context):
            return context.code
        case .serverError(_, let context), .unknown(_, let context):
            return context.code
        default:
            return nil
        }
    }

    public var requestId: String? {
        switch self {
        case .unauthorized(let context):
            return context?.requestId
        case .notFound(let context), .conflict(let context), .locked(let context), .validation(let context):
            return context.requestId
        case .serverError(_, let context), .unknown(_, let context):
            return context.requestId
        default:
            return nil
        }
    }

    public var supportReference: String? {
        requestId ?? code
    }

    public var userFacingMessageWithReference: String {
        guard let supportReference, !supportReference.isEmpty else {
            return userFacingMessage
        }
        return "\(userFacingMessage)\nRef: \(supportReference)"
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
