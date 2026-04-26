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
    case noShow         = "NO_SHOW"

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
        case .noShow:         return "Nedolazak"
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
        case .noShow:         return "orange"
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
        case .draft, .pendingPayment, .confirmed, .active, .disputed: return true
        default: return false
        }
    }
}

// MARK: - Payment Mode

public enum PaymentMode: String, Codable, CaseIterable, Sendable {
    case online       = "ONLINE"
    case payOnArrival = "PAY_ON_ARRIVAL"

    public var displayName: String {
        switch self {
        case .online:       return "Online placanje"
        case .payOnArrival: return "Placanje na dolasku"
        }
    }

    public var detailText: String {
        switch self {
        case .online:
            return "Kartica se autorizuje pre dolaska. Mesto je zadrzano ograniceno vreme dok placanje nije potvrdjeno."
        case .payOnArrival:
            return "Placate kod partnera na lokaciji. Rezervacija je vazeca kada je sistem potvrdi."
        }
    }

    public var requiresOnlinePayment: Bool {
        self == .online
    }
}

// MARK: - Reservation

public struct Reservation: Decodable, Identifiable, Sendable {
    public let id: String
    public let customerId: String
    public let operatorId: String
    public let locationId: String
    public let resourceId: String
    public let inventoryPoolId: String?
    public let holdId: String?
    public let vehicleId: String?
    public let startsAt: Date
    public let endsAt: Date
    public let timezone: String
    public let status: ReservationStatus
    public let paymentMode: PaymentMode
    public let totalAmountCents: Int
    public let currency: String
    public let accessInstructionsVisible: Bool
    public let paymentExpiresAt: Date?
    public let createdAt: Date
    public let updatedAt: Date?

    public var totalAmountFormatted: String {
        formatCents(totalAmountCents, currency: currency)
    }

    public var bookingCodePlaceholder: String {
        "SL-\(id.prefix(8).uppercased())"
    }

    public var holdExpiresAt: Date? {
        paymentExpiresAt
    }

    public var hasActiveOnlinePaymentHold: Bool {
        paymentMode == .online && status == .pendingPayment && holdExpiresAt != nil
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
    public let paymentMode: PaymentMode
    public let idempotencyKey: String

    public init(
        resourceId: String,
        vehicleId: String? = nil,
        startsAt: Date,
        endsAt: Date,
        promoCode: String? = nil,
        quoteId: String? = nil,
        paymentMethodId: String? = nil,
        paymentMode: PaymentMode,
        idempotencyKey: String = IdempotencyKey.generate(prefix: "res")
    ) {
        self.resourceId = resourceId
        self.vehicleId = vehicleId
        self.startsAt = startsAt
        self.endsAt = endsAt
        self.promoCode = promoCode
        self.quoteId = quoteId
        self.paymentMethodId = paymentMethodId
        self.paymentMode = paymentMode
        self.idempotencyKey = idempotencyKey
    }
}

// MARK: - Cancel

public struct CancelReservationRequest: Encodable, Sendable {
    public let reason: String?
    public init(reason: String? = nil) { self.reason = reason }
}

// MARK: - Booking Detail

public enum BookingHoldStatus: String, Decodable, CaseIterable, Sendable {
    case active   = "ACTIVE"
    case consumed = "CONSUMED"
    case released = "RELEASED"
    case expired  = "EXPIRED"
}

public enum BookingActorType: String, Decodable, CaseIterable, Sendable {
    case customer        = "CUSTOMER"
    case operatorUser    = "OPERATOR"
    case admin           = "ADMIN"
    case system          = "SYSTEM"
    case paymentProvider = "PAYMENT_PROVIDER"
}

public enum BookingEventType: String, Decodable, CaseIterable, Sendable {
    case legacyImported    = "LEGACY_IMPORTED"
    case created           = "CREATED"
    case holdCreated       = "HOLD_CREATED"
    case holdExpired       = "HOLD_EXPIRED"
    case statusChanged     = "STATUS_CHANGED"
    case paymentAuthorized = "PAYMENT_AUTHORIZED"
    case paymentFailed     = "PAYMENT_FAILED"
    case confirmed         = "CONFIRMED"
    case cancelled         = "CANCELLED"
    case operatorCancelled = "OPERATOR_CANCELLED"
    case checkedIn         = "CHECKED_IN"
    case noShow            = "NO_SHOW"
    case adminOverride     = "ADMIN_OVERRIDE"
    case refundMarked      = "REFUND_MARKED"

    public var isCustomerVisible: Bool {
        switch self {
        case .created, .holdCreated, .holdExpired, .paymentAuthorized, .paymentFailed,
             .confirmed, .cancelled, .operatorCancelled, .checkedIn, .noShow, .refundMarked:
            return true
        case .legacyImported, .statusChanged, .adminOverride:
            return false
        }
    }
}

public enum CheckinStatus: String, Decodable, CaseIterable, Sendable {
    case checkedIn = "CHECKED_IN"
    case completed = "COMPLETED"
    case noShow    = "NO_SHOW"
}

public enum ReservationPaymentAttemptStatus: String, Decodable, CaseIterable, Sendable {
    case pending        = "PENDING"
    case requiresAction = "REQUIRES_ACTION"
    case authorized     = "AUTHORIZED"
    case failed         = "FAILED"
    case cancelled      = "CANCELLED"
    case refundMarked   = "REFUND_MARKED"
}

public enum PaymentProviderEventStatus: String, Decodable, CaseIterable, Sendable {
    case received  = "RECEIVED"
    case processed = "PROCESSED"
    case failed    = "FAILED"
}

public enum RefundStatus: String, Decodable, CaseIterable, Sendable {
    case marked    = "MARKED"
    case processed = "PROCESSED"
    case failed    = "FAILED"
}

public struct BookingHold: Decodable, Identifiable, Sendable {
    public let id: String
    public let inventoryPoolId: String
    public let status: BookingHoldStatus
    public let expiresAt: Date
    public let paymentMode: PaymentMode
}

public struct BookingEvent: Decodable, Identifiable, Sendable {
    public let id: String
    public let eventType: BookingEventType
    public let actorType: BookingActorType
    public let actorId: String?
    public let notes: String?
    public let payload: [String: JSONValue]?
    public let occurredAt: Date
}

public indirect enum JSONValue: Decodable, Sendable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case object([String: JSONValue])
    case array([JSONValue])
    case null

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .null
        } else if let value = try? container.decode(Bool.self) {
            self = .bool(value)
        } else if let value = try? container.decode(Double.self) {
            self = .number(value)
        } else if let value = try? container.decode(String.self) {
            self = .string(value)
        } else if let value = try? container.decode([String: JSONValue].self) {
            self = .object(value)
        } else {
            self = .array(try container.decode([JSONValue].self))
        }
    }
}

public struct BookingCheckin: Decodable, Identifiable, Sendable {
    public let id: String
    public let status: CheckinStatus
    public let operatorUserId: String
    public let checkinAt: Date
    public let checkoutAt: Date?
    public let notes: String?
}

public struct ReservationPaymentProviderEvent: Decodable, Identifiable, Sendable {
    public let id: String
    public let provider: String
    public let externalEventId: String
    public let eventType: String
    public let status: PaymentProviderEventStatus
    public let processedAt: Date?
}

public struct ReservationPaymentAttempt: Decodable, Identifiable, Sendable {
    public let id: String
    public let reservationId: String
    public let provider: String
    public let status: ReservationPaymentAttemptStatus
    public let paymentMode: PaymentMode
    public let amountCents: Int
    public let currency: String
    public let providerReference: String?
    public let failureCode: String?
    public let failureMessage: String?
    public let lastTransitionAt: Date
    public let providerEvents: [ReservationPaymentProviderEvent]
}

public struct ReservationRefund: Decodable, Identifiable, Sendable {
    public let id: String
    public let reservationId: String
    public let paymentAttemptId: String?
    public let amountCents: Int
    public let currency: String
    public let status: RefundStatus
    public let reason: String?
    public let providerReference: String?
    public let markedByUserId: String?
    public let markedAt: Date
}

public struct BookingDetail: Decodable, Sendable {
    public let reservation: Reservation
    public let hold: BookingHold?
    public let checkin: BookingCheckin?
    public let timeline: [BookingEvent]
    public let paymentAttempts: [ReservationPaymentAttempt]
    public let refunds: [ReservationRefund]
    public let supportCases: [SupportTicket]

    public var customerVisibleTimeline: [BookingEvent] {
        timeline.filter { $0.eventType.isCustomerVisible }
    }
}
