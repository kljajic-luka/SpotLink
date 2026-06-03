import XCTest
@testable import SpotLinkCore

// MARK: - Testovi pokretanja i bootstrap-a aplikacije
//
// Verifikuje da se osnovni objekti sesije i konfiguracije mogu
// inicijalizovati bez rusenja. Ovi testovi su namenski minimalni –
// detaljni testovi logike nalaze se u SPM paketu (Tests/SpotLinkTests/).

final class LaunchTests: XCTestCase {

    // MARK: SessionManager

    func testSessionManagerSharedInstanceExists() async {
        // SessionManager.shared mora biti dostupan bez rusenja
        await MainActor.run {
            XCTAssertNotNil(SessionManager.shared, "SessionManager.shared ne sme biti nil")
        }
    }

    func testSessionManagerInitialStateIsLoading() async {
        // Pre poziva restoreSession(), stanje je .loading
        // Novi, nezavisni SessionManager za izolovani test
        let manager = await MainActor.run { SessionManager() }
        let state = await MainActor.run { manager.state }
        if case .loading = state {
            // Ocekivano pocetno stanje
        } else {
            XCTFail("Pocetno stanje SessionManager-a treba biti .loading, dobijeno: \(state)")
        }
    }

    // MARK: AppEnvironment

    func testAppEnvironmentDefaultsToLocalInDebug() {
        // U Debug build-u bez SPOTLINK_ENV env var, treba vratiti .local
        let env = AppEnvironment.current()
        // U CI/debug okruzenju bez postavljene SPOTLINK_ENV, ocekujemo .local
        XCTAssertEqual(env, .local,
                       "AppEnvironment.current() treba biti .local u debug testu bez SPOTLINK_ENV")
    }

    func testAllEnvironmentURLsAreNonEmpty() {
        for env in AppEnvironment.allCases {
            let url = env.apiBaseURL
            XCTAssertFalse(url.absoluteString.isEmpty,
                           "API URL za okruzenje '\(env.rawValue)' ne sme biti prazan")
            XCTAssertTrue(url.scheme == "https" || url.scheme == "http",
                          "API URL za '\(env.rawValue)' mora koristiti http(s) shemu")
        }
    }

    func testLocalEnvironmentUsesLocalhost() {
        let url = AppEnvironment.local.apiBaseURL
        XCTAssertTrue(url.host == "localhost" || url.host == "127.0.0.1",
                      "Local okruzenje treba koristiti localhost")
    }

    func testLocalDeviceEnvironmentUsesMacBackendAddress() {
        let url = AppEnvironment.localDevice.apiBaseURL
        XCTAssertEqual(url.scheme, "http")
        XCTAssertEqual(url.host, "192.168.1.151")
        XCTAssertEqual(url.port, 8080)
    }

    func testProductionEnvironmentIsMarkedAsProduction() {
        XCTAssertTrue(AppEnvironment.production.isProduction)
        XCTAssertFalse(AppEnvironment.local.isProduction)
        XCTAssertFalse(AppEnvironment.localDevice.isProduction)
        XCTAssertFalse(AppEnvironment.development.isProduction)
        XCTAssertFalse(AppEnvironment.staging.isProduction)
    }

    // MARK: SessionState

    func testSessionStateIsAuthenticatedLogic() {
        let loadingState = SessionState.loading
        let unauthenticatedState = SessionState.unauthenticated
        XCTAssertFalse(loadingState.isAuthenticated)
        XCTAssertFalse(unauthenticatedState.isAuthenticated)
        XCTAssertNil(loadingState.sessionInfo)
        XCTAssertNil(unauthenticatedState.sessionInfo)
    }
}
