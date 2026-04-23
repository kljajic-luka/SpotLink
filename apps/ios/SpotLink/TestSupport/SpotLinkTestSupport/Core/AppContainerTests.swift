import Foundation
import Testing
@testable import SpotLinkCore

@Suite("SpotLinkAppContainer")
@MainActor
struct AppContainerTests {

    @Test("container povezuje session, search i push sloj bez placeholder konstrukcije")
    func containerBuildsCustomerShellDependencies() {
        let defaults = UserDefaults(suiteName: "spotlink.app-container.tests")!
        defaults.removePersistentDomain(forName: "spotlink.app-container.tests")

        let session = SessionManager(
            keychain: KeychainStorage(service: "spotlink.app-container.tests"),
            preferences: PreferenceStorage(prefix: "tests.", defaults: defaults)
        )
        let locationManager = SpotLinkLocationManager()
        let pushManager = PushNotificationManager(notificationService: nil)

        let container = SpotLinkAppContainer(
            environment: .local,
            session: session,
            locationManager: locationManager,
            pushManager: pushManager
        )

        #expect(container.environment == .local)
        #expect(ObjectIdentifier(container.session) == ObjectIdentifier(session))
        #expect(container.searchViewModel.mapCenter.latitude == SearchMapViewModel.defaultCenter.latitude)
        #expect(container.searchViewModel.mapCenter.longitude == SearchMapViewModel.defaultCenter.longitude)
        #expect(ObjectIdentifier(container.pushManager) == ObjectIdentifier(pushManager))
    }
}