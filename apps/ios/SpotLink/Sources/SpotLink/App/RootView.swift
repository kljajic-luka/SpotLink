import SwiftUI

// MARK: - Root View

/// Koreni pogled koji rutira izmedju loading, auth i main app shell-a.
public struct RootView: View {

    @EnvironmentObject private var session: SessionManager
    @Environment(\.appEnvironment) private var environment

    public init() {}

    public var body: some View {
        Group {
            switch session.state {
            case .loading:
                SplashView()

            case .unauthenticated:
                AuthFlowView()
                    .transition(.opacity)

            case .authenticated(let info):
                MainAppShell(sessionInfo: info)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: session.state.isAuthenticated)
    }
}

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

