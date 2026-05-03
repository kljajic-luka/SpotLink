import SwiftUI

// MARK: - Root View

/// Koreni pogled koji rutira izmedju loading, auth i main app shell-a.
public struct RootView: View {

    @EnvironmentObject private var session: SessionManager
    @EnvironmentObject private var appContainer: SpotLinkAppContainer
    @Environment(\.appEnvironment) private var environment
    @State private var debugAutoLoginStarted = false

    public init() {}

    public var body: some View {
        Group {
            switch session.state {
            case .loading:
                SplashView()

            case .unauthenticated:
                Group {
                    if shouldUseDebugAutoLogin {
                        SplashView()
                            .task {
                                await performDebugAutoLoginIfNeeded()
                            }
                    } else {
                        AuthFlowView()
                    }
                }
                .transition(.opacity)

            case .authenticated(let info):
                MainAppShell(sessionInfo: info)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: session.state.isAuthenticated)
    }

    private var shouldUseDebugAutoLogin: Bool {
        #if DEBUG
        return debugAutoLoginCredentials != nil
        #else
        return false
        #endif
    }

    private var debugAutoLoginCredentials: (email: String, password: String)? {
        #if DEBUG
        let process = ProcessInfo.processInfo
        let email = process.environment["SPOTLINK_DEBUG_AUTOLOGIN_EMAIL"]
            ?? process.arguments.value(after: "--spotlink-debug-autologin-email")
        let password = process.environment["SPOTLINK_DEBUG_AUTOLOGIN_PASSWORD"]
            ?? process.arguments.value(after: "--spotlink-debug-autologin-password")

        guard let email = email?.trimmingCharacters(in: .whitespacesAndNewlines),
              let password = password?.trimmingCharacters(in: .whitespacesAndNewlines),
              !email.isEmpty,
              !password.isEmpty else {
            return nil
        }

        return (email, password)
        #else
        return nil
        #endif
    }

    private func performDebugAutoLoginIfNeeded() async {
        #if DEBUG
        guard !debugAutoLoginStarted,
              case .unauthenticated = session.state,
              let credentials = debugAutoLoginCredentials else {
            return
        }

        debugAutoLoginStarted = true

        do {
            try await appContainer.authService.login(email: credentials.email, password: credentials.password)
        } catch {
            debugAutoLoginStarted = false
        }
        #endif
    }
}

#if DEBUG
private extension Array where Element == String {
    func value(after flag: String) -> String? {
        guard let index = firstIndex(of: flag) else { return nil }
        let next = self.index(after: index)
        guard next < endIndex else { return nil }
        return self[next]
    }
}
#endif

// MARK: - Splash View

struct SplashView: View {
    var body: some View {
        VStack(spacing: SpotLinkDesign.Spacing.lg) {
            Image(systemName: "parkingsign.circle.fill")
                .font(.system(size: 72))
                .foregroundStyle(SpotLinkDesign.Colors.tint)
                .accessibilityHidden(true)
            Text("SpotLink")
                .font(SpotLinkDesign.Typography.largeTitle.bold())
                .foregroundStyle(SpotLinkDesign.Colors.label)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(SpotLinkDesign.Colors.background)
        .accessibilityLabel("SpotLink se ucitava")
    }
}
