import Foundation

// MARK: - Support Ticket

public enum TicketStatus: String, Decodable, CaseIterable, Sendable {
    case open       = "OPEN"
    case inProgress = "IN_PROGRESS"
    case resolved   = "RESOLVED"
    case closed     = "CLOSED"

    public var displayName: String {
        switch self {
        case .open:       return "Otvoreno"
        case .inProgress: return "U toku"
        case .resolved:   return "Reseno"
        case .closed:     return "Zatvoreno"
        }
    }
}

public enum TicketCategory: String, Codable, CaseIterable, Sendable {
    case reservationIssue   = "RESERVATION_ISSUE"
    case paymentIssue       = "PAYMENT_ISSUE"
    case accessIssue        = "ACCESS_ISSUE"
    case accountIssue       = "ACCOUNT_ISSUE"
    case generalInquiry     = "GENERAL_INQUIRY"
    case other              = "OTHER"

    public var displayName: String {
        switch self {
        case .reservationIssue: return "Problem sa rezervacijom"
        case .paymentIssue:     return "Problem sa placan­jem"
        case .accessIssue:      return "Problem sa pristupom"
        case .accountIssue:     return "Problem sa nalogom"
        case .generalInquiry:   return "Opste pitanje"
        case .other:            return "Ostalo"
        }
    }
}

public struct SupportTicket: Decodable, Identifiable, Sendable {
    public let id: String
    public let requesterUserId: String
    public let category: TicketCategory
    public let status: TicketStatus
    public let subject: String
    public let reservationId: String?
    public let locationId: String?
    public let createdAt: Date
    public let updatedAt: Date
}

public struct SupportMessage: Decodable, Identifiable, Sendable {
    public let id: String
    public let ticketId: String
    public let senderUserId: String
    public let senderName: String
    public let body: String
    public let attachmentUrl: String?
    public let createdAt: Date
}

// MARK: - Requests

public struct CreateTicketRequest: Encodable, Sendable {
    public let category: TicketCategory
    public let subject: String
    public let body: String
    public let reservationId: String?
    public let locationId: String?

    public init(category: TicketCategory, subject: String, body: String,
                reservationId: String? = nil, locationId: String? = nil) {
        self.category = category
        self.subject = subject
        self.body = body
        self.reservationId = reservationId
        self.locationId = locationId
    }
}

public struct AddMessageRequest: Encodable, Sendable {
    public let body: String
    public init(body: String) { self.body = body }
}
