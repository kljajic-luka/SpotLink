import Foundation
import SwiftUI

// MARK: - SwiftUI Environment Key

struct AppEnvironmentKey: EnvironmentKey {
    static let defaultValue: AppEnvironment = .local
}

public extension EnvironmentValues {
    var appEnvironment: AppEnvironment {
        get { self[AppEnvironmentKey.self] }
        set { self[AppEnvironmentKey.self] = newValue }
    }
}

/// SpotLink okruzenje (environment) – bira se pri pokretanju aplikacije.
public enum AppEnvironment: String, CaseIterable, Sendable {
    case local
    case localDevice
    case development
    case staging
    case production

    public var apiBaseURL: URL {
        switch self {
        case .local:
            return URL(string: "http://localhost:8080/api")!
        case .localDevice:
            return Self.configuredURL(named: "SPOTLINK_LOCAL_DEVICE_API_BASE_URL")
                ?? URL(string: "http://192.168.1.151:8080/api")!
        case .development:
            return URL(string: "https://api-dev.spotlink.app/api")!
        case .staging:
            return URL(string: "https://api-staging.spotlink.app/api")!
        case .production:
            return URL(string: "https://api.spotlink.app/api")!
        }
    }

    public var displayName: String {
        switch self {
        case .local:       return "Local"
        case .localDevice: return "Local Device"
        case .development: return "Development"
        case .staging:     return "Staging"
        case .production:  return "Production"
        }
    }

    public var isProduction: Bool {
        self == .production
    }

    /// Cita vrednost iz Info.plist kljuca SPOTLINK_ENV, ili vraca .local
    public static func current() -> AppEnvironment {
        #if DEBUG
        if let override = configuredValue(named: "SPOTLINK_ENV"),
           let env = AppEnvironment(rawValue: override) {
            return env
        }
        return .local
        #else
        if let raw = configuredValue(named: "SPOTLINK_ENV"),
           let env = AppEnvironment(rawValue: raw) {
            return env
        }
        return .production
        #endif
    }

    private static func configuredURL(named key: String) -> URL? {
        guard let raw = configuredValue(named: key) else { return nil }
        return URL(string: raw)
    }

    private static func configuredValue(named key: String) -> String? {
        if let raw = normalizedConfigValue(ProcessInfo.processInfo.environment[key]) {
            return raw
        }
        return normalizedConfigValue(Bundle.main.infoDictionary?[key] as? String)
    }

    private static func normalizedConfigValue(_ raw: String?) -> String? {
        guard let value = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty,
              !value.hasPrefix("$(") else {
            return nil
        }
        return value
    }
}
