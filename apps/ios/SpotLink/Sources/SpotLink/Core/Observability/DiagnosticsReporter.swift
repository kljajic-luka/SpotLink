import Foundation

// MARK: - Diagnostics Context

public struct AppDiagnosticsContext: Sendable, Equatable {
    public let environment: String
    public let appVersion: String
    public let appBuild: String

    public init(environment: String, appVersion: String, appBuild: String) {
        self.environment = DiagnosticsRedactor.sanitizedField(environment) ?? "unknown"
        self.appVersion = DiagnosticsRedactor.sanitizedField(appVersion) ?? "unknown"
        self.appBuild = DiagnosticsRedactor.sanitizedField(appBuild) ?? "unknown"
    }

    public static func current(environment: AppEnvironment, bundle: Bundle = .main) -> AppDiagnosticsContext {
        AppDiagnosticsContext(
            environment: environment.rawValue,
            appVersion: bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown",
            appBuild: bundle.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "unknown"
        )
    }
}

// MARK: - Diagnostic Events

public enum DiagnosticEventCategory: String, Codable, Sendable {
    case apiFailure = "api_failure"
}

public struct DiagnosticsEvent: Identifiable, Equatable, Sendable {
    public let id: UUID
    public let category: DiagnosticEventCategory
    public let backendCode: String?
    public let backendRequestId: String?
    public let httpStatus: Int?
    public let appEnvironment: String
    public let appVersion: String
    public let appBuild: String
    public let occurredAt: Date

    public init(
        id: UUID = UUID(),
        category: DiagnosticEventCategory,
        backendCode: String? = nil,
        backendRequestId: String? = nil,
        httpStatus: Int? = nil,
        appEnvironment: String,
        appVersion: String,
        appBuild: String,
        occurredAt: Date = Date()
    ) {
        self.id = id
        self.category = category
        self.backendCode = DiagnosticsRedactor.sanitizedField(backendCode)
        self.backendRequestId = DiagnosticsRedactor.sanitizedField(backendRequestId)
        self.httpStatus = DiagnosticsRedactor.sanitizedStatus(httpStatus)
        self.appEnvironment = DiagnosticsRedactor.sanitizedField(appEnvironment) ?? "unknown"
        self.appVersion = DiagnosticsRedactor.sanitizedField(appVersion) ?? "unknown"
        self.appBuild = DiagnosticsRedactor.sanitizedField(appBuild) ?? "unknown"
        self.occurredAt = occurredAt
    }

    public static func apiFailure(
        from error: APIError,
        context: AppDiagnosticsContext,
        occurredAt: Date = Date()
    ) -> DiagnosticsEvent {
        DiagnosticsEvent(
            category: .apiFailure,
            backendCode: error.code,
            backendRequestId: error.requestId,
            httpStatus: error.httpStatusCode,
            appEnvironment: context.environment,
            appVersion: context.appVersion,
            appBuild: context.appBuild,
            occurredAt: occurredAt
        )
    }
}

// MARK: - Reporter Protocols

public protocol DiagnosticsReporter: Sendable {
    func record(_ event: DiagnosticsEvent) async
    func isEnabled() async -> Bool
    func recentEvents(limit: Int) async -> [DiagnosticsEvent]
}

public struct NoopDiagnosticsReporter: DiagnosticsReporter {
    public init() {}

    public func record(_ event: DiagnosticsEvent) async {}

    public func isEnabled() async -> Bool {
        false
    }

    public func recentEvents(limit: Int) async -> [DiagnosticsEvent] {
        []
    }
}

public actor InMemoryDiagnosticsReporter: DiagnosticsReporter {
    private let enabled: Bool
    private let maxEvents: Int
    private var events: [DiagnosticsEvent] = []

    public init(enabled: Bool = true, maxEvents: Int = 25) {
        self.enabled = enabled
        self.maxEvents = max(1, maxEvents)
    }

    public func record(_ event: DiagnosticsEvent) async {
        guard enabled else { return }
        events.append(event)
        if events.count > maxEvents {
            events.removeFirst(events.count - maxEvents)
        }
    }

    public func isEnabled() async -> Bool {
        enabled
    }

    public func recentEvents(limit: Int) async -> [DiagnosticsEvent] {
        guard limit > 0 else { return [] }
        return Array(events.suffix(limit).reversed())
    }
}

// MARK: - Privacy Redaction

enum DiagnosticsRedactor {
    private static let allowedScalars = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "._:-"))
    private static let sensitivePatterns = [
        #"(?i)bearer\s+"#,
        #"(?i)access[_-]?token"#,
        #"(?i)refresh[_-]?token"#,
        #"(?i)reset[_-]?token"#,
        #"(?i)authorization"#,
        #"(?i)password"#,
        #"sl_reset_"#,
        #"eyJ[A-Za-z0-9_-]+"#,
        #"@"#
    ]

    static func sanitizedField(_ value: String?) -> String? {
        guard let raw = value?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty else {
            return nil
        }

        if containsSensitivePattern(raw) {
            return "redacted"
        }

        let trimmed = String(raw.prefix(128))
        guard trimmed.unicodeScalars.allSatisfy({ allowedScalars.contains($0) }) else {
            return "redacted"
        }
        return trimmed
    }

    static func sanitizedStatus(_ status: Int?) -> Int? {
        guard let status, (100...599).contains(status) else { return nil }
        return status
    }

    private static func containsSensitivePattern(_ value: String) -> Bool {
        sensitivePatterns.contains { pattern in
            value.range(of: pattern, options: .regularExpression) != nil
        }
    }
}
