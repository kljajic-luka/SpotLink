import Foundation
import Testing
@testable import SpotLinkCore

@Suite("DiagnosticsReporter privacy")
struct DiagnosticsReporterTests {

    @Test("API error event captures only support-safe identifiers")
    func apiErrorEventCapturesCodeRequestIdAndStatus() {
        let context = AppDiagnosticsContext(environment: "staging", appVersion: "1.4.0", appBuild: "42")
        let apiContext = APIErrorContext(
            message: "Server rejected request",
            code: "AUTH_TEMPORARILY_LOCKED",
            requestId: "req-20260606-abc"
        )

        let event = DiagnosticsEvent.apiFailure(from: .locked(apiContext), context: context)

        #expect(event.category == .apiFailure)
        #expect(event.backendCode == "AUTH_TEMPORARILY_LOCKED")
        #expect(event.backendRequestId == "req-20260606-abc")
        #expect(event.httpStatus == 423)
        #expect(event.appEnvironment == "staging")
        #expect(event.appVersion == "1.4.0")
        #expect(event.appBuild == "42")
    }

    @Test("unsafe diagnostic field values are redacted")
    func unsafeValuesAreRedacted() {
        let context = AppDiagnosticsContext(environment: "local", appVersion: "1.0", appBuild: "1")
        let apiContext = APIErrorContext(
            message: "Unsafe details are intentionally ignored",
            code: "token=sl_reset_secret",
            requestId: "Bearer sensitive-token"
        )

        let event = DiagnosticsEvent.apiFailure(from: .serverError(500, apiContext), context: context)

        #expect(event.backendCode == "redacted")
        #expect(event.backendRequestId == "redacted")
        #expect(event.httpStatus == 500)
    }

    @Test("diagnostic event does not carry API error details or payload fields")
    func eventDoesNotCarryDetailsOrPayloads() {
        let context = AppDiagnosticsContext(environment: "local", appVersion: "1.0", appBuild: "1")
        let apiContext = APIErrorContext(
            message: "validation failed",
            code: "VALIDATION_ERROR",
            requestId: "req-safe",
            details: [
                "email": "person@example.test",
                "licensePlate": "BG-123-AA",
                "address": "Sensitive Street 12",
                "accessToken": "secret-token"
            ]
        )

        let event = DiagnosticsEvent.apiFailure(from: .validation(apiContext), context: context)
        let reflected = String(describing: event)

        #expect(event.backendCode == "VALIDATION_ERROR")
        #expect(event.backendRequestId == "req-safe")
        #expect(!reflected.contains("person@example.test"))
        #expect(!reflected.contains("BG-123-AA"))
        #expect(!reflected.contains("Sensitive Street"))
        #expect(!reflected.contains("secret-token"))
    }

    @Test("no-op reporter stays disabled and records nothing")
    func noopReporterDoesNotRecord() async {
        let reporter = NoopDiagnosticsReporter()
        let event = DiagnosticsEvent(
            category: .apiFailure,
            backendCode: "SERVER_ERROR",
            backendRequestId: "req-safe",
            httpStatus: 500,
            appEnvironment: "production",
            appVersion: "1",
            appBuild: "1"
        )

        await reporter.record(event)

        #expect(await reporter.isEnabled() == false)
        #expect(await reporter.recentEvents(limit: 10).isEmpty)
    }

    @Test("disabled in-memory reporter drops events")
    func disabledInMemoryReporterDropsEvents() async {
        let reporter = InMemoryDiagnosticsReporter(enabled: false)
        let event = DiagnosticsEvent(
            category: .apiFailure,
            backendCode: "SERVER_ERROR",
            backendRequestId: "req-safe",
            httpStatus: 500,
            appEnvironment: "staging",
            appVersion: "1",
            appBuild: "1"
        )

        await reporter.record(event)

        #expect(await reporter.isEnabled() == false)
        #expect(await reporter.recentEvents(limit: 10).isEmpty)
    }
}
