import Foundation

public struct LegalConfiguration: Equatable, Sendable {
    public static let privacyPolicyKey = "SPOTLINK_PRIVACY_POLICY_URL"
    public static let termsKey = "SPOTLINK_TERMS_URL"
    public static let supportURLKey = "SPOTLINK_SUPPORT_URL"
    public static let supportEmailKey = "SPOTLINK_SUPPORT_EMAIL"
    public static let accountDeletionURLKey = "SPOTLINK_ACCOUNT_DELETION_URL"

    public static let defaultPrivacyPolicyURL = URL(string: "https://spotlink.app/privacy")!
    public static let defaultTermsURL = URL(string: "https://spotlink.app/terms")!
    public static let defaultSupportURL = URL(string: "https://spotlink.app/support")!
    public static let defaultSupportEmail = "support@spotlink.app"
    public static let defaultAccountDeletionURL = URL(string: "https://spotlink.app/account-deletion")!

    public let privacyPolicyURL: URL
    public let termsURL: URL
    public let supportURL: URL
    public let supportEmail: String
    public let accountDeletionURL: URL

    public var supportMailURL: URL {
        URL(string: "mailto:\(supportEmail)") ?? Self.defaultSupportMailURL
    }

    private static var defaultSupportMailURL: URL {
        URL(string: "mailto:\(defaultSupportEmail)")!
    }

    public static func current(
        bundle: Bundle = .main,
        processInfo: ProcessInfo = .processInfo
    ) -> LegalConfiguration {
        resolve { key in
            if let value = processInfo.environment[key] {
                return value
            }
            return bundle.object(forInfoDictionaryKey: key) as? String
        }
    }

    static func resolve(valueForKey: (String) -> String?) -> LegalConfiguration {
        LegalConfiguration(
            privacyPolicyURL: configuredURL(
                valueForKey(privacyPolicyKey),
                fallback: defaultPrivacyPolicyURL
            ),
            termsURL: configuredURL(valueForKey(termsKey), fallback: defaultTermsURL),
            supportURL: configuredURL(valueForKey(supportURLKey), fallback: defaultSupportURL),
            supportEmail: configuredEmail(valueForKey(supportEmailKey), fallback: defaultSupportEmail),
            accountDeletionURL: configuredURL(
                valueForKey(accountDeletionURLKey),
                fallback: defaultAccountDeletionURL
            )
        )
    }

    private static func configuredURL(_ rawValue: String?, fallback: URL) -> URL {
        guard let value = normalize(rawValue),
              let url = URL(string: value),
              url.scheme == "https" || url.scheme == "mailto" else {
            return fallback
        }
        return url
    }

    private static func configuredEmail(_ rawValue: String?, fallback: String) -> String {
        guard let value = normalize(rawValue),
              value.contains("@"),
              !value.hasSuffix(".local") else {
            return fallback
        }
        return value
    }

    private static func normalize(_ rawValue: String?) -> String? {
        guard let value = rawValue?.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty,
              !value.hasPrefix("$(") else {
            return nil
        }
        return value
    }
}
