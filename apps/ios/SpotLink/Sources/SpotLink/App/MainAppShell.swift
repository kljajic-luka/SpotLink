import SwiftUI

// MARK: - Main App Shell

/// Glavni tab shell za autentifikovane korisnike.
/// Prikazuje razlicite tabove u zavisnosti od korisnicke uloge.
public struct MainAppShell: View {

    let sessionInfo: SessionInfo

    @State private var selectedTab: AppTab = .search
    @EnvironmentObject private var session: SessionManager
    @EnvironmentObject private var appContainer: SpotLinkAppContainer

    public init(sessionInfo: SessionInfo) {
        self.sessionInfo = sessionInfo
    }

    public var body: some View {
        TabView(selection: $selectedTab) {
            // Pretraga/Mapa
            NavigationStack {
                SearchMapView(viewModel: appContainer.searchViewModel)
                    .navigationTitle("Pronadji parking")
            }
            .tabItem {
                Label("Pretraga", systemImage: "map.fill")
            }
            .tag(AppTab.search)
            .accessibilityLabel("Pretraga parkinga")

            // Rezervacije
            NavigationStack {
                ReservationsView(
                    service: appContainer.reservationService,
                    locationService: appContainer.locationService,
                    supportService: appContainer.supportService
                )
            }
            .tabItem {
                Label("Rezervacije", systemImage: "calendar")
            }
            .tag(AppTab.reservations)
            .accessibilityLabel("Moje rezervacije")

            // Vozila
            NavigationStack {
                VehiclesView(service: appContainer.vehicleService)
            }
            .tabItem {
                Label("Vozila", systemImage: "car.fill")
            }
            .tag(AppTab.vehicles)
            .accessibilityLabel("Moja vozila")

            // Podrska
            NavigationStack {
                SupportTicketsView(service: appContainer.supportService)
            }
            .tabItem {
                Label("Podrska", systemImage: "questionmark.circle.fill")
            }
            .tag(AppTab.support)
            .accessibilityLabel("Korisnická podrška")

            // Profil
            NavigationStack {
                ProfileOverviewView(sessionInfo: sessionInfo)
            }
            .tabItem {
                Label("Profil", systemImage: "person.circle.fill")
            }
            .tag(AppTab.profile)
            .accessibilityLabel("Moj profil")
        }
        .tint(SpotLinkDesign.Colors.tint)
    }
}

// MARK: - App Tabs

enum AppTab: String, CaseIterable {
    case search      = "search"
    case reservations = "reservations"
    case vehicles    = "vehicles"
    case support     = "support"
    case profile     = "profile"
}

struct ProfileOverviewView: View {
    let sessionInfo: SessionInfo

    @EnvironmentObject private var session: SessionManager
    @EnvironmentObject private var appContainer: SpotLinkAppContainer
    @EnvironmentObject private var pushManager: PushNotificationManager

    var body: some View {
        List {
            Section {
                HStack(spacing: SpotLinkDesign.Spacing.md) {
                    Circle()
                        .fill(SpotLinkDesign.Colors.tint)
                        .frame(width: 56, height: 56)
                        .overlay {
                            Text(sessionInfo.user.initials)
                                .font(.headline)
                                .foregroundStyle(.white)
                        }
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(sessionInfo.user.fullName)
                            .font(SpotLinkDesign.Typography.headline)
                        Text(sessionInfo.user.email)
                            .font(SpotLinkDesign.Typography.subheadline)
                            .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    }
                }
                .padding(.vertical, SpotLinkDesign.Spacing.sm)
            }

            Section("Status naloga") {
                infoRow(title: "Uloge", value: sessionInfo.user.roles.map(\.rawValue).joined(separator: ", "))
                infoRow(title: "Okruzenje", value: appContainer.environment.displayName)
                infoRow(title: "Sesija", value: sessionInfo.isExpired ? "Zahteva osvezavanje" : "Aktivna")
            }

            Section("Obavestenja") {
                infoRow(title: "Dozvola", value: notificationStatusTitle)
                if let token = pushManager.deviceToken {
                    infoRow(title: "Token", value: String(token.prefix(12)) + "...")
                }

                Button {
                    Task {
                        await pushManager.requestPermission()
                    }
                } label: {
                    Label("Omoguci push obavestenja", systemImage: "bell.badge")
                }
            }

            Section("Nalog") {
                infoRow(title: "Tip korisnika", value: sessionInfo.user.isOperator ? "Partner / operator" : "Kupac")
                infoRow(title: "Podrska", value: sessionInfo.user.isSupport ? "Ukljucena" : "Standardna korisnicka podrska")
            }

            Section {
                Button(role: .destructive) {
                    Task {
                        await appContainer.authService.logout()
                    }
                } label: {
                    Label("Odjava", systemImage: "rectangle.portrait.and.arrow.right")
                }
            }
        }
        .navigationTitle("Profil")
        .spotlinkListStyle()
    }

    private var notificationStatusTitle: String {
        switch pushManager.permissionStatus {
        case .authorized, .provisional, .ephemeral:
            return "Dozvoljeno"
        case .denied:
            return "Odbijeno"
        case .notDetermined:
            return "Nije odluceno"
        @unknown default:
            return "Nepoznato"
        }
    }

    @ViewBuilder
    private func infoRow(title: String, value: String) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(value)
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                .multilineTextAlignment(.trailing)
        }
    }
}
