import SwiftUI
// Eksplicitni import SPM biblioteke.
// Xcode app target se zove SpotLink a PRODUCT_MODULE_NAME=SpotLinkApp,
// pa je ovaj import neophodan da bi kompajler razresio SessionManager,
// AppEnvironment i RootView koji dolaze iz SPM modula SpotLink.
import SpotLink

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
    private let environment = AppEnvironment.current()

    public init() {}

    public var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                .environment(\.appEnvironment, environment)
                .task {
                    await session.restoreSession()
                }
        }
    }
}
