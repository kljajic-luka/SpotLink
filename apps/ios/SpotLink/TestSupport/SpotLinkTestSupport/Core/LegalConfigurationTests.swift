import Foundation
import Testing
@testable import SpotLinkCore

@Suite("LegalConfiguration")
struct LegalConfigurationTests {

    @Test("default legal/support destinations are public SpotLink URLs")
    func defaultsArePublicSpotLinkDestinations() {
        let config = LegalConfiguration.resolve { _ in nil }

        #expect(config.privacyPolicyURL.absoluteString == "https://spotlink.app/privacy")
        #expect(config.termsURL.absoluteString == "https://spotlink.app/terms")
        #expect(config.supportURL.absoluteString == "https://spotlink.app/support")
        #expect(config.accountDeletionURL.absoluteString == "https://spotlink.app/account-deletion")
        #expect(config.supportEmail == "support@spotlink.app")
        #expect(!config.supportEmail.hasSuffix(".local"))
    }

    @Test("valid runtime overrides are accepted")
    func validOverridesAreAccepted() {
        let values = [
            LegalConfiguration.privacyPolicyKey: "https://legal.test/privacy",
            LegalConfiguration.termsKey: "https://legal.test/terms",
            LegalConfiguration.supportURLKey: "https://help.test/support",
            LegalConfiguration.supportEmailKey: "support@help.test",
            LegalConfiguration.accountDeletionURLKey: "https://legal.test/account-deletion"
        ]

        let config = LegalConfiguration.resolve { values[$0] }

        #expect(config.privacyPolicyURL.absoluteString == "https://legal.test/privacy")
        #expect(config.termsURL.absoluteString == "https://legal.test/terms")
        #expect(config.supportURL.absoluteString == "https://help.test/support")
        #expect(config.supportEmail == "support@help.test")
        #expect(config.accountDeletionURL.absoluteString == "https://legal.test/account-deletion")
    }

    @Test("placeholders and local support email fall back to public defaults")
    func unsafeValuesFallBackToDefaults() {
        let values = [
            LegalConfiguration.privacyPolicyKey: "$(SPOTLINK_PRIVACY_POLICY_URL)",
            LegalConfiguration.termsKey: "http://insecure.example/terms",
            LegalConfiguration.supportURLKey: "",
            LegalConfiguration.supportEmailKey: "support@spotlink.local",
            LegalConfiguration.accountDeletionURLKey: "not a url"
        ]

        let config = LegalConfiguration.resolve { values[$0] }

        #expect(config.privacyPolicyURL == LegalConfiguration.defaultPrivacyPolicyURL)
        #expect(config.termsURL == LegalConfiguration.defaultTermsURL)
        #expect(config.supportURL == LegalConfiguration.defaultSupportURL)
        #expect(config.supportEmail == LegalConfiguration.defaultSupportEmail)
        #expect(config.accountDeletionURL == LegalConfiguration.defaultAccountDeletionURL)
    }
}
