import XCTest

// MARK: - SpotLink UI testovi
//
// Smoke testovi koji verifikuju da se aplikacija pokrece i prikazuje
// ocekivano pocetno stanje (splash / auth flow).
//
// NAPOMENA: UI testovi zahtevaju pun Xcode i iOS Simulator.
// Na masinama bez Xcode.app, ovi testovi nece biti izvrseni.

final class SpotLinkUITests: XCTestCase {

    var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        let bundleIdentifier = ProcessInfo.processInfo.environment["SPOTLINK_UITEST_APP_BUNDLE_IDENTIFIER"] ?? "com.spotlink.app"
        app = XCUIApplication(bundleIdentifier: bundleIdentifier)
        configureLaunch()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    // MARK: Pokretanje

    func testAppLaunchesSuccessfully() throws {
        app.launch()
        // Aplikacija mora biti u foreground stanju posle pokretanja
        XCTAssertEqual(app.state, .runningForeground,
                       "Aplikacija treba biti pokrenuta u prvom planu")
    }

    func testLaunchToLoginScreen() throws {
        app.launch()

        XCTAssertTrue(element("auth.login.screen").waitForExistence(timeout: 5), "UI test launch treba prikazati prijavu")
        XCTAssertTrue(app.staticTexts["Prijava"].exists, "Login naslov treba biti vidljiv")
        XCTAssertTrue(element("auth.login.emailField").exists, "Email polje treba imati stabilan accessibility identifier")
        XCTAssertTrue(element("auth.login.passwordField").exists, "Lozinka polje treba imati stabilan accessibility identifier")
    }

    func testRegistrationShowsLegalLinksAndDisabledSubmitInitially() throws {
        app.launch()

        let registerLink = element("auth.login.registerButton")
        XCTAssertTrue(registerLink.waitForExistence(timeout: 5), "Login flow treba prikazati registracioni link")
        registerLink.tap()

        XCTAssertTrue(element("auth.register.screen").waitForExistence(timeout: 5), "Registracioni ekran treba biti prikazan")
        XCTAssertTrue(element("auth.register.termsLink").exists, "Registracija treba prikazati link ka uslovima")
        XCTAssertTrue(element("auth.register.privacyLink").exists, "Registracija treba prikazati link ka politici privatnosti")
        XCTAssertTrue(element("auth.register.acceptTermsToggle").exists, "Prihvatanje uslova treba biti jasno dostupno")
        XCTAssertFalse(element("auth.register.submitButton").isEnabled, "Prazna registraciona forma ne sme biti submitovana")
    }

    func testAuthenticatedFixtureRestoresCustomerSessionToSearch() throws {
        configureLaunch(.authenticated)
        app.launch()

        XCTAssertTrue(element("search.screen").waitForExistence(timeout: 6), "Deterministicka sesija treba otvoriti pretragu")
        XCTAssertTrue(element("main.tabView").exists, "Glavni tab shell treba biti prikazan")
        XCTAssertTrue(
            existsAny(["search.openControls.button", "Otvori pretragu parkinga", "Pretraga"]),
            "Pretraga treba imati dostupnu kontrolu ili tab za unos lokacije"
        )
    }

    func testProfileLegalSurfaceAndLogout() throws {
        configureLaunch(.authenticated)
        app.launch()

        let profileTab = app.buttons["Profil"]
        XCTAssertTrue(profileTab.waitForExistence(timeout: 6), "Profil tab treba biti dostupan u autentifikovanom shell-u")
        profileTab.tap()

        XCTAssertTrue(element("profile.screen").waitForExistence(timeout: 5), "Profil ekran treba biti prikazan")
        XCTAssertTrue(element("profile.accountDeletion.requestButton").exists, "Destruktivna akcija brisanja naloga treba biti dostupna")

        XCTAssertTrue(scrollUntilExists("profile.privacyPolicy.link"), "Profil treba izloziti politiku privatnosti")
        XCTAssertTrue(existsAny(["profile.terms.link", "Uslovi koriscenja"]), "Profil treba izloziti uslove koriscenja")
        XCTAssertTrue(existsAny(["profile.support.link", "Centar za podrsku"]), "Profil treba izloziti podrsku")
        XCTAssertTrue(
            scrollUntilExists("profile.accountDeletion.infoLink"),
            "Profil treba izloziti informacije o brisanju naloga"
        )

        XCTAssertTrue(scrollUntilExists("profile.logout.button"), "Odjava treba biti dostupna")
        let logoutButton = element("profile.logout.button")
        logoutButton.tap()

        XCTAssertTrue(element("auth.login.screen").waitForExistence(timeout: 5), "Odjava treba vratiti korisnika na prijavu")
    }

    func testAccountDeletionRequestRequiresConfirmation() throws {
        configureLaunch(.authenticated)
        app.launch()

        let profileTab = app.buttons["Profil"]
        XCTAssertTrue(profileTab.waitForExistence(timeout: 6), "Profil tab treba biti dostupan u autentifikovanom shell-u")
        profileTab.tap()

        XCTAssertTrue(element("profile.accountDeletion.requestButton").waitForExistence(timeout: 5), "Brisanje naloga treba biti dostupno")
        element("profile.accountDeletion.requestButton").tap()

        XCTAssertTrue(app.staticTexts["Zatrazi brisanje naloga?"].waitForExistence(timeout: 3), "Brisanje naloga mora traziti potvrdu")
        XCTAssertTrue(app.buttons["Posalji zahtev"].exists, "Potvrdna destruktivna akcija treba biti jasno imenovana")
    }

    private enum LaunchFixture {
        case signedOut
        case authenticated
    }

    private func configureLaunch(_ fixture: LaunchFixture = .signedOut) {
        app.launchArguments = [
            "--uitesting",
            "--spotlink-uitest-reset-session"
        ]
        if fixture == .authenticated {
            app.launchArguments.append("--spotlink-uitest-authenticated")
        }
        app.launchEnvironment["SPOTLINK_ENV"] = "local"
        app.launchEnvironment["SPOTLINK_UI_TESTING"] = "1"
    }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier]
    }

    private func existsAny(_ identifiersOrLabels: [String]) -> Bool {
        identifiersOrLabels.contains { value in
            element(value).exists
            || app.buttons[value].exists
            || app.links[value].exists
            || app.staticTexts[value].exists
        }
    }

    private func scrollUntilExists(_ identifier: String, maxSwipes: Int = 4) -> Bool {
        let target = element(identifier)
        if target.exists { return true }
        for _ in 0..<maxSwipes {
            app.swipeUp()
            if target.waitForExistence(timeout: 1) {
                return true
            }
        }
        return false
    }
}
