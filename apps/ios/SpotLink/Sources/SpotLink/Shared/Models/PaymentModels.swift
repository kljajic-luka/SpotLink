import Foundation

// MARK: - Payment Status

public enum PaymentStatus: String, Decodable, CaseIterable, Sendable {
    case requiresMethod  = "REQUIRES_METHOD"
    case requiresAction  = "REQUIRES_ACTION"
    case authorized      = "AUTHORIZED"
    case captured        = "CAPTURED"
    case failed          = "FAILED"
    case refunded        = "REFUNDED"
    case cancelled       = "CANCELLED"

    public var displayName: String {
        switch self {
        case .requiresMethod:  return "Zahteva nacin placanja"
        case .requiresAction:  return "Zahteva akciju"
        case .authorized:      return "Autorizovano"
        case .captured:        return "Naplaceno"
        case .failed:          return "Neuspelo"
        case .refunded:        return "Refundirano"
        case .cancelled:       return "Otkazano"
        }
    }

    public var isTerminal: Bool {
        switch self {
        case .captured, .failed, .refunded, .cancelled: return true
        default: return false
        }
    }
}

// MARK: - Payment Intent

public struct PaymentIntent: Decodable, Identifiable, Sendable {
    public let id: String
    public let reservationId: String
    public let customerId: String?
    public let amountCents: Int
    public let currency: String
    public let status: PaymentStatus
    public let redirectUrl: String?
    public let clientSecret: String?
    public let providerReference: String?
    public let createdAt: Date?
    public let updatedAt: Date?

    public var amountFormatted: String {
        let amount = Double(amountCents) / 100.0
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = currency
        return formatter.string(from: NSNumber(value: amount)) ?? "\(currency) \(amount)"
    }
}

public struct PaymentProviderResult: Decodable, Sendable {
    public let status: PaymentStatus
    public let paymentIntentId: String
    public let redirectUrl: String?
    public let message: String?
}

// MARK: - Create Payment Intent

public struct CreatePaymentIntentRequest: Encodable, Sendable {
    public let reservationId: String
    public let paymentMethodId: String?
    public let idempotencyKey: String

    public init(reservationId: String, paymentMethodId: String? = nil) {
        self.reservationId = reservationId
        self.paymentMethodId = paymentMethodId
        self.idempotencyKey = IdempotencyKey.generate(prefix: "pay")
    }
}

// MARK: - Payment Method

public struct PaymentMethod: Decodable, Identifiable, Sendable {
    public let id: String
    public let type: String?
    public let displayName: String?
    public let last4: String?
    public let expiryMonth: Int?
    public let expiryYear: Int?
    public let brand: String?
    public let isDefault: Bool

    public var formattedDescription: String {
        if let last4 { return "\(brand ?? type ?? "Kartica") •••• \(last4)" }
        return displayName ?? type ?? "Nacin placanja"
    }

    enum CodingKeys: String, CodingKey {
        case id, brand, last4, type, displayName
        case expiryMonth = "expMonth"
        case expiryYear = "expYear"
        case isDefault = "default"
    }
}
