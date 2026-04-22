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
        case .screenView(let n):                   return ["screen_name": n]
        case .registrationStarted(let t):          return ["type": t]
        case .registrationCompleted(let t):        return ["type": t]
        case .reservationCreated(let id):          return ["reservation_id": id]
        case .paymentIntentCreated(let id):        return ["payment_intent_id": id]
        case .error(let n, let d):                 return ["error_name": n, "description": d]
        case .custom(_, let props):                return props
        default:                                   return [:]
        }
    }
}

// MARK: - Analytics Service

/// Salje dogadjaje na SpotLink analytics backend.
/// Best-effort: greske se ignorisu da ne bi blokirale UX.
public actor AnalyticsService {

    private let apiClient: APIClientProtocol
    private var sessionId: String = UUID().uuidString

    public init(apiClient: APIClientProtocol) {
        self.apiClient = apiClient
    }

    public func track(_ event: AnalyticsEvent) {
        Task {
            await send(event)
        }
    }

    private func send(_ event: AnalyticsEvent) async {
        let payload = AnalyticsEventPayload(
            eventName: event.name,
            properties: event.properties,
            occurredAt: ISO8601DateFormatter().string(from: Date()),
            sessionId: sessionId)
        do {
            let _: AnalyticsIngestResponse = try await apiClient.post("/analytics/events", body: payload)
        } catch {
            SpotLinkLogger.debug("Analytics event '\(event.name)' nije poslat: \(error.localizedDescription)")
        }
    }
}

// MARK: - Payloads

private struct AnalyticsEventPayload: Encodable {
    let eventName: String
    let properties: [String: String]
    let occurredAt: String
    let sessionId: String
}

private struct AnalyticsIngestResponse: Decodable {}
