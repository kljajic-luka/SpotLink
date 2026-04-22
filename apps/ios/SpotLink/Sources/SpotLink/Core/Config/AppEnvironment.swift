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
    case development
    case staging
    case production

    public var apiBaseURL: URL {
        switch self {
        case .local:
            return URL(string: "http://localhost:8080/api")!
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
        if let override = ProcessInfo.processInfo.environment["SPOTLINK_ENV"],
           let env = AppEnvironment(rawValue: override) {
            return env
        }
        return .local
        #else
        if let raw = Bundle.main.infoDictionary?["SPOTLINK_ENV"] as? String,
           let env = AppEnvironment(rawValue: raw) {
            return env
        }
        return .production
        #endif
    }
}
