import Testing
@testable import SpotLinkCore

@Suite("SpotLinkLogger privacy")
struct LoggerPrivacyTests {

    @Test("redakcija uklanja poznate auth i reset tokene")
    func redactsKnownTokenPatterns() {
        let raw = """
        header Bearer sample-bearer-token
        reset=https://app.spotlink.test/reset-password?token=sl_reset_sample-token
        {"accessToken":"access-token-value","refreshToken":"refresh-token-value"}
        """

        let redacted = SpotLinkLogger.redactedForLog(raw)

        #expect(!redacted.contains("sample-bearer-token"))
        #expect(!redacted.contains("sl_reset_sample-token"))
        #expect(!redacted.contains("access-token-value"))
        #expect(!redacted.contains("refresh-token-value"))
        #expect(redacted.contains("Bearer [REDACTED]"))
        #expect(redacted.contains("token=[REDACTED]"))
        #expect(redacted.contains("\"accessToken\":\"[REDACTED]\""))
    }
}
