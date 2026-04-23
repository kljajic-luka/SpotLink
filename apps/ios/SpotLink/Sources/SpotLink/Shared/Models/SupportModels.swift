import Foundation

// MARK: - Support Ticket

public enum TicketStatus: String, Decodable, CaseIterable, Sendable {
    case open              = "OPEN"
    case waitingOnCustomer = "WAITING_ON_CUSTOMER"
    case waitingOnOperator = "WAITING_ON_OPERATOR"
    case resolved          = "RESOLVED"

    public var displayName: String {
        switch self {
        case .open:              return "Otvoreno"
        case .waitingOnCustomer: return "Ceka vas odgovor"
        case .waitingOnOperator: return "Ceka partnera"
        case .resolved:          return "Reseno"
        }
    }
}

public enum TicketCategory: String, Codable, CaseIterable, Sendable {
    case reservation   = "RESERVATION"
    case payment       = "PAYMENT"
    case locationAccess = "LOCATION_ACCESS"
    case safety        = "SAFETY"
    case account       = "ACCOUNT"
    case other         = "OTHER"

    public var displayName: String {
        switch self {
        case .reservation:    return "Rezervacija"
        case .payment:        return "Placanje"
        case .locationAccess: return "Ulaz i pristup"
        case .safety:         return "Bezbednost"
        case .account:        return "Nalog"
        case .other:          return "Ostalo"
        }
    }
}

public struct SupportTicket: Decodable, Identifiable, Sendable {
    public let id: String
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
