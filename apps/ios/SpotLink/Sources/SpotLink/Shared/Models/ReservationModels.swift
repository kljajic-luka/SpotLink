import Foundation

// MARK: - Reservation Status

public enum ReservationStatus: String, Decodable, CaseIterable, Sendable {
    case draft          = "DRAFT"
    case pendingPayment = "PENDING_PAYMENT"
    case confirmed      = "CONFIRMED"
    case active         = "ACTIVE"
    case completed      = "COMPLETED"
    case cancelled      = "CANCELLED"
    case expired        = "EXPIRED"
    case disputed       = "DISPUTED"

    public var displayName: String {
        switch self {
        case .draft:          return "Nacrt"
        case .pendingPayment: return "Ceka placanje"
        case .confirmed:      return "Potvrdjeno"
        case .active:         return "Aktivno"
        case .completed:      return "Zavrseno"
        case .cancelled:      return "Otkazano"
        case .expired:        return "Isteklo"
        case .disputed:       return "Sporno"
        }
    }

    public var color: String {
        switch self {
        case .draft:          return "gray"
        case .pendingPayment: return "orange"
        case .confirmed:      return "blue"
        case .active:         return "green"
        case .completed:      return "gray"
        case .cancelled:      return "red"
        case .expired:        return "gray"
        case .disputed:       return "red"
        }
    }

    public var isTerminal: Bool {
        switch self {
        case .completed, .cancelled, .expired: return true
        default: return false
        }
    }

    public var canCancel: Bool {
        switch self {
        case .draft, .pendingPayment, .confirmed: return true
        default: return false
        }
    }
}

// MARK: - Reservation

public struct Reservation: Decodable, Identifiable, Sendable {
    public let id: String
    public let customerId: String
    public let operatorId: String
    public let locationId: String
    public let resourceId: String
    public let vehicleId: String?
    public let startsAt: Date
    public let endsAt: Date
    public let timezone: String
    public let status: ReservationStatus
    public let totalAmountCents: Int
    public let currency: String
    public let accessInstructionsVisible: Bool
    public let paymentExpiresAt: Date?
    public let createdAt: Date
    public let updatedAt: Date?

    public var totalAmountFormatted: String {
        let amount = Double(totalAmountCents) / 100.0
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = currency
        return formatter.string(from: NSNumber(value: amount)) ?? "\(currency) \(amount)"
    }

    public var bookingCodePlaceholder: String {
        "SL-\(id.prefix(8).uppercased())"
    }
}

// MARK: - Quote

public struct ReservationQuoteRequest: Encodable, Sendable {
    public let resourceId: String
    public let vehicleId: String?
    public let startsAt: Date
    public let endsAt: Date
    public let promoCode: String?

    public init(resourceId: String, vehicleId: String? = nil,
                startsAt: Date, endsAt: Date, promoCode: String? = nil) {
        self.resourceId = resourceId
        self.vehicleId = vehicleId
        self.startsAt = startsAt
        self.endsAt = endsAt
        self.promoCode = promoCode
    }
}

public struct ReservationQuote: Decodable, Sendable {
    public let resourceId: String
    public let startsAt: Date
    public let endsAt: Date
    public let subtotalCents: Int
    public let feesCents: Int
    public let discountCents: Int
    public let totalAmountCents: Int
    public let currency: String
    public let expiresAt: Date?

    public var totalAmountFormatted: String {
        let amount = Double(totalAmountCents) / 100.0
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = currency
        return formatter.string(from: NSNumber(value: amount)) ?? "\(currency) \(amount)"
    }

    public var subtotalFormatted: String {
        formatCents(subtotalCents, currency: currency)
    }

    public var feesFormatted: String {
        formatCents(feesCents, currency: currency)
    }

    public var discountFormatted: String {
        formatCents(discountCents, currency: currency)
    }
}

// MARK: - Create Reservation

public struct CreateReservationRequest: Encodable, Sendable {
    public let resourceId: String
    public let vehicleId: String?
    public let startsAt: Date
    public let endsAt: Date
    public let promoCode: String?
    public let quoteId: String?
    public let paymentMethodId: String?
    public let idempotencyKey: String

    public init(
        resourceId: String,
        vehicleId: String? = nil,
        startsAt: Date,
        endsAt: Date,
        promoCode: String? = nil,
        quoteId: String? = nil,
        paymentMethodId: String? = nil
        , idempotencyKey: String = IdempotencyKey.generate(prefix: "res")
    ) {
        self.resourceId = resourceId
        self.vehicleId = vehicleId
        self.startsAt = startsAt
        self.endsAt = endsAt
        self.promoCode = promoCode
        self.quoteId = quoteId
        self.paymentMethodId = paymentMethodId
        self.idempotencyKey = idempotencyKey
    }
}

// MARK: - Cancel

public struct CancelReservationRequest: Encodable, Sendable {
    public let reason: String?
    public init(reason: String? = nil) { self.reason = reason }
}
