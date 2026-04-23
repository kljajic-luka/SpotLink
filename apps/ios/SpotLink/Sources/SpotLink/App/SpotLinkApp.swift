import SwiftUI
// Eksplicitni import SPM biblioteke.
// Xcode app target se zove SpotLink a PRODUCT_MODULE_NAME=SpotLinkApp,
// pa je ovaj import neophodan da bi kompajler razresio SessionManager,
// AppEnvironment i RootView koji dolaze iz SPM modula SpotLinkCore.
import SpotLinkCore

// MARK: - SpotLink App Entry Point

/// Glavni entry point SpotLink iOS aplikacije.
/// Koristi SessionManager za odluku koji flow prikazati.
///
/// NAPOMENA: AppEnvironmentKey i EnvironmentValues.appEnvironment su
/// definisani u AppEnvironment.swift (biblioteka). Ovaj fajl ih ne
/// redefinise kako bi se izbegao konflikt simbola u Xcode app targetu.
@main
public struct SpotLinkApp: App {

    @StateObject private var session = SessionManager.shared
    @StateObject private var appContainer: SpotLinkAppContainer
    private let environment: AppEnvironment

#if canImport(UIKit)
    @UIApplicationDelegateAdaptor(SpotLinkAppDelegate.self) private var appDelegate
#endif

    public init() {
        let environment = AppEnvironment.current()
        self.environment = environment
        _appContainer = StateObject(wrappedValue: SpotLinkAppContainer(environment: environment))
    }

    public var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                .environmentObject(appContainer)
                .environmentObject(appContainer.pushManager)
                .environment(\.appEnvironment, environment)
                .task {
                    await session.restoreSession()
                    await appContainer.pushManager.checkPermissionStatus()
                }
        }
    }
}
