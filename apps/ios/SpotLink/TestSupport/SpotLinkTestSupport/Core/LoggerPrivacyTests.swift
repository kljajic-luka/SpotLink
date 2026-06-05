import Testing
@testable import SpotLinkCore

@Suite("SpotLinkLogger privacy")
struct LoggerPrivacyTests {

    @Test("redakcija uklanja poznate auth i reset tokene")
    func redactsKnownTokenPatterns() {
        let raw = """
        header Bearer eyJhbGciOiJIUzI1NiJ9.secret
        reset=https://app.spotlink.test/reset-password?token=sl_reset_123e4567-e89b-12d3-a456-426614174000
        {"accessToken":"access-token-value","refreshToken":"refresh-token-value"}
        """

        let redacted = SpotLinkLogger.redactedForLog(raw)

        #expect(!redacted.contains("eyJhbGciOiJIUzI1NiJ9.secret"))
        #expect(!redacted.contains("sl_reset_123e4567-e89b-12d3-a456-426614174000"))
        #expect(!redacted.contains("access-token-value"))
        #expect(!redacted.contains("refresh-token-value"))
        #expect(redacted.contains("Bearer [REDACTED]"))
        #expect(redacted.contains("token=[REDACTED]"))
        #expect(redacted.contains("\"accessToken\":\"[REDACTED]\""))
    }
}
