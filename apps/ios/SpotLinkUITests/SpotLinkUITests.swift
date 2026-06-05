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
        app.launchArguments = ["--uitesting"]
        // Postavljamo local okruzenje za UI testove
        app.launchEnvironment["SPOTLINK_ENV"] = "local"
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

    func testSplashOrAuthViewAppearsOnLaunch() throws {
        app.launch()
        // Pocetni prikaz je ili splash (ucitavanje) ili auth flow
        // Ne testiramo konkretan sadrzaj – samo da app nije prazna
        let appExists = app.otherElements.firstMatch.waitForExistence(timeout: 5)
        XCTAssertTrue(appExists, "Aplikacija treba prikazati sadrzaj posle pokretanja")
    }

    func testRegistrationShowsLegalLinksAndDisabledSubmitInitially() throws {
        app.launch()

        let registerLink = app.buttons["Registrujte se"]
        XCTAssertTrue(registerLink.waitForExistence(timeout: 5), "Login flow treba prikazati registracioni link")
        registerLink.tap()

        XCTAssertTrue(app.staticTexts["Registracija"].waitForExistence(timeout: 5), "Registracioni ekran treba biti prikazan")
        XCTAssertTrue(existsOnScreen("Uslovi koriscenja"), "Registracija treba prikazati link ka uslovima")
        XCTAssertTrue(existsOnScreen("Politika privatnosti"), "Registracija treba prikazati link ka politici privatnosti")
        XCTAssertFalse(app.buttons["Registruj se"].isEnabled, "Prazna registraciona forma ne sme biti submitovana")
    }

    private func existsOnScreen(_ label: String) -> Bool {
        app.links[label].exists
        || app.buttons[label].exists
        || app.staticTexts[label].exists
    }
}
