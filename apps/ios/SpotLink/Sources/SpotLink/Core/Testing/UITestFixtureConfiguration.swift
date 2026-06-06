#if DEBUG
import Foundation

enum UITestFixtureConfiguration {
    private static let testingArgument = "--uitesting"
    private static let resetSessionArgument = "--spotlink-uitest-reset-session"
    private static let authenticatedSessionArgument = "--spotlink-uitest-authenticated"
    private static let profileTabArgument = "--spotlink-uitest-open-profile"

    static var isUITesting: Bool {
        let process = ProcessInfo.processInfo
        return process.arguments.contains(testingArgument)
        || normalized(process.environment["SPOTLINK_UI_TESTING"]) == "1"
    }

    static var shouldResetSessionOnLaunch: Bool {
        guard isUITesting else { return false }
        let process = ProcessInfo.processInfo
        return process.arguments.contains(resetSessionArgument)
        || normalized(process.environment["SPOTLINK_UITEST_RESET_SESSION"]) == "1"
    }

    static var shouldUseAuthenticatedSession: Bool {
        guard isUITesting else { return false }
        let process = ProcessInfo.processInfo
        return process.arguments.contains(authenticatedSessionArgument)
        || normalized(process.environment["SPOTLINK_UITEST_FIXTURE"]) == "authenticated"
    }

    static var shouldSkipRemoteLogout: Bool {
        shouldUseAuthenticatedSession
    }

    static var shouldOpenProfileTab: Bool {
        guard isUITesting else { return false }
        return ProcessInfo.processInfo.arguments.contains(profileTabArgument)
    }

    static func authenticatedUserPreferences() -> UserPreferences {
        UserPreferences(
            locale: "sr-RS",
            marketingOptIn: false,
            reservationAlerts: true,
            paymentAlerts: true,
            supportAlerts: true
        )
    }

    static func authenticatedTokenResponse(now: Date = Date()) -> MobileTokenResponse {
        MobileTokenResponse(
            accessToken: "uitest-access-token",
            refreshToken: "uitest-refresh-token",
            expiresIn: 3_600,
            expiresInSeconds: 3_600,
            refreshExpiresInSeconds: 86_400,
            issuedAt: now,
            expiresAt: now.addingTimeInterval(3_600),
            refreshExpiresAt: now.addingTimeInterval(86_400),
            tokenType: "Bearer",
            user: UserProfile(
                id: "uitest-customer",
                email: "customer@spotlink.test",
                firstName: "Test",
                lastName: "Kupac",
                phone: nil,
                avatarUrl: nil,
                bio: nil,
                roles: [.customer],
                operatorId: nil,
                registrationStatus: "ACTIVE",
                createdAt: nil
            )
        )
    }

    private static func normalized(_ value: String?) -> String? {
        guard let raw = value?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty else {
            return nil
        }
        return raw
    }
}
#endif
