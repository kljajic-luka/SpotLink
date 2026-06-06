import Foundation

// MARK: - Analytics Event

/// Tip analitike dogadjaja. Best-effort – ne blokira UX.
public enum AnalyticsEvent: Sendable {
    case appOpen
    case screenView(name: String)
    case login
    case logout
    case registrationStarted(type: String)
    case registrationCompleted(type: String)
    case reservationQuoteRequested
    case reservationCreated(id: String)
    case paymentIntentCreated(id: String)
    case supportTicketCreated
    case error(name: String, description: String)
    case custom(name: String, properties: [String: String])

    var name: String {
        switch self {
        case .appOpen:                          return "app_open"
        case .screenView:                       return "screen_view"
        case .login:                            return "login"
        case .logout:                           return "logout"
        case .registrationStarted:              return "registration_started"
        case .registrationCompleted:            return "registration_completed"
        case .reservationQuoteRequested:        return "reservation_quote_requested"
        case .reservationCreated:               return "reservation_created"
        case .paymentIntentCreated:             return "payment_intent_created"
        case .supportTicketCreated:             return "support_ticket_created"
        case .error:                            return "error"
        case .custom(let n, _):                 return n
        }
    }

    var properties: [String: String] {
        switch self {
        case .screenView(let n):                   return ["screen": n]
        case .registrationStarted(let t):          return ["registrationType": t]
        case .registrationCompleted(let t):        return ["registrationType": t]
        case .reservationCreated:                  return ["result": "created"]
        case .paymentIntentCreated:                return ["result": "created"]
        case .error(let n, _):                     return ["errorName": n]
        case .custom(_, let props):                return props
        default:                                   return [:]
        }
    }
}

public enum AnalyticsConsentState: Sendable {
    case disabled
    case enabled
}

// MARK: - Analytics Service

/// Salje dogadjaje na SpotLink analytics backend.
/// Best-effort: greske se ignorisu da ne bi blokirale UX.
public actor AnalyticsService {

    private let apiClient: APIClientProtocol
    private let sessionId: String
    private let dateProvider: @Sendable () -> Date
    private var consent: AnalyticsConsentState

    public init(
        apiClient: APIClientProtocol,
        consent: AnalyticsConsentState = .disabled,
        sessionId: String = UUID().uuidString,
        dateProvider: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.apiClient = apiClient
        self.consent = consent
        self.sessionId = sessionId
        self.dateProvider = dateProvider
    }

    public func setConsent(_ consent: AnalyticsConsentState) {
        self.consent = consent
    }

    public func track(_ event: AnalyticsEvent) {
        Task {
            await send(event)
        }
    }

    func trackNow(_ event: AnalyticsEvent) async {
        await send(event)
    }

    private func send(_ event: AnalyticsEvent) async {
        guard consent == .enabled else {
            return
        }
        guard let eventPayload = AnalyticsEventPayload.from(
            event,
            sessionId: sessionId,
            timestamp: ISO8601DateFormatter().string(from: dateProvider())
        ) else {
            SpotLinkLogger.debug("analytics_event_dropped reason=policy")
            return
        }
        let payload = AnalyticsBatchPayload(events: [eventPayload])
        do {
            let _: EmptyResponse = try await apiClient.post("/analytics/events", body: payload)
        } catch {
            SpotLinkLogger.debug("analytics_delivery_failed")
        }
    }
}

// MARK: - Payloads

private struct AnalyticsBatchPayload: Encodable {
    let events: [AnalyticsEventPayload]
}

private struct AnalyticsEventPayload: Encodable {
    let event: String
    let properties: [String: String]?
    let timestamp: String
    let sessionId: String

    static func from(_ event: AnalyticsEvent, sessionId: String, timestamp: String) -> AnalyticsEventPayload? {
        guard AnalyticsPrivacyPolicy.allowedEventNames.contains(event.name) else {
            return nil
        }
        let properties = AnalyticsPrivacyPolicy.sanitized(event.properties)
        return AnalyticsEventPayload(
            event: event.name,
            properties: properties.isEmpty ? nil : properties,
            timestamp: timestamp,
            sessionId: sessionId
        )
    }
}

private enum AnalyticsPrivacyPolicy {
    static let allowedEventNames: Set<String> = [
        "app_open",
        "screen_view",
        "login",
        "logout",
        "registration_started",
        "registration_completed",
        "search_performed",
        "reservation_quote_requested",
        "reservation_flow_started",
        "reservation_created",
        "reservation_create_failed",
        "payment_intent_created",
        "payment_unavailable",
        "support_ticket_created",
        "account_deletion_requested",
        "notification_preferences_updated",
        "profile_updated",
        "error"
    ]

    private static let allowedPropertyKeys: Set<String> = [
        "platform",
        "appVersion",
        "appBuild",
        "environment",
        "screen",
        "context",
        "source",
        "flow",
        "type",
        "result",
        "status",
        "reason",
        "category",
        "provider",
        "registrationType",
        "paymentMode",
        "reservationStatus",
        "notificationType",
        "errorName"
    ]

    private static let unsafeKeyFragments = [
        "email",
        "phone",
        "firstname",
        "lastname",
        "fullname",
        "licenseplate",
        "plate",
        "token",
        "authorization",
        "bearer",
        "password",
        "secret",
        "address",
        "latitude",
        "longitude",
        "coordinate",
        "card",
        "pan",
        "cvv",
        "paymentmethod",
        "apns",
        "description",
        "message"
    ]

    static func sanitized(_ properties: [String: String]) -> [String: String] {
        var safe: [String: String] = [:]
        for (key, value) in properties {
            guard allowedPropertyKeys.contains(key), !containsUnsafeKeyFragment(key) else {
                continue
            }
            let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            guard trimmed.count <= 120, !containsSensitiveValue(trimmed) else {
                continue
            }
            safe[key] = trimmed
        }
        return safe
    }

    private static func containsUnsafeKeyFragment(_ key: String) -> Bool {
        let normalized = key
            .lowercased()
            .filter { $0.isLetter || $0.isNumber }
        return unsafeKeyFragments.contains { normalized.contains($0) }
    }

    private static func containsSensitiveValue(_ value: String) -> Bool {
        matches(value, #"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}"#)
            || matches(value, #"\+?\d[\d\s().-]{7,}\d"#)
            || matches(value, #"(?i)\bbearer\s+[a-z0-9._~-]+"#)
            || matches(value, #"(?i)(sl_reset_|refresh[_-]?token|access[_-]?token|apns[_-]?token|eyJ[a-z0-9_-]+\.)"#)
            || matches(value, #"\b(?:\d[ -]*?){13,19}\b"#)
            || matches(value, #"\b[A-ZČĆŽŠĐ]{1,3}[-\s]?\d{2,5}[-\s]?[A-ZČĆŽŠĐ]{1,3}\b"#)
            || matches(value, #"\b-?\d{1,2}\.\d{4,}\s*,\s*-?\d{1,3}\.\d{4,}\b"#)
    }

    private static func matches(_ value: String, _ pattern: String) -> Bool {
        value.range(of: pattern, options: [.regularExpression, .caseInsensitive]) != nil
    }
}
