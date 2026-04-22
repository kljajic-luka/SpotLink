import SwiftUI

// MARK: - Main App Shell

/// Glavni tab shell za autentifikovane korisnike.
/// Prikazuje razlicite tabove u zavisnosti od korisnicke uloge.
public struct MainAppShell: View {

    let sessionInfo: SessionInfo

    @State private var selectedTab: AppTab = .search
    @EnvironmentObject private var session: SessionManager

    // SearchMapViewModel se inicijalizuje jednom i celi zivotni vek deli sa tabom
    @StateObject private var searchViewModel = SearchMapViewModel(
        locationService: LocationService(
            apiClient: APIClient(
                baseURL: AppEnvironment.current().apiBaseURL,
                tokenProvider: SessionManager.shared
            )
        ),
        locationManager: SpotLinkLocationManager.shared
    )

    public init(sessionInfo: SessionInfo) {
        self.sessionInfo = sessionInfo
    }

    public var body: some View {
        TabView(selection: $selectedTab) {
            // Pretraga/Mapa
            NavigationStack {
                SearchMapView(viewModel: searchViewModel)
                    .navigationTitle("Pronadji parking")
            }
            .tabItem {
                Label("Pretraga", systemImage: "map.fill")
            }
            .tag(AppTab.search)
            .accessibilityLabel("Pretraga parkinga")

            // Rezervacije
            NavigationStack {
                ReservationsPlaceholderView()
            }
            .tabItem {
                Label("Rezervacije", systemImage: "calendar")
            }
            .tag(AppTab.reservations)
            .accessibilityLabel("Moje rezervacije")

            // Vozila
            NavigationStack {
                VehiclesPlaceholderView()
            }
            .tabItem {
                Label("Vozila", systemImage: "car.fill")
            }
            .tag(AppTab.vehicles)
            .accessibilityLabel("Moja vozila")

            // Podrska
            NavigationStack {
                SupportPlaceholderView()
            }
            .tabItem {
                Label("Podrska", systemImage: "questionmark.circle.fill")
            }
            .tag(AppTab.support)
            .accessibilityLabel("Korisnická podrška")

            // Profil
            NavigationStack {
                ProfilePlaceholderView()
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

// MARK: - Placeholder Views (zamenjuju se pravim feature views-ima)

struct ReservationsPlaceholderView: View {
    var body: some View {
        EmptyStateView(
            icon: "calendar",
            title: "Nema rezervacija",
            message: "Rezervacije ce se prikazati ovde nakon prve rezervacije.",
            actionTitle: "Pronadji parking")
        .navigationTitle("Rezervacije")
    }
}

struct VehiclesPlaceholderView: View {
    var body: some View {
        EmptyStateView(
            icon: "car.fill",
            title: "Nema vozila",
            message: "Dodajte vozilo da biste ubrzali rezervaciju.",
            actionTitle: "Dodaj vozilo")
        .navigationTitle("Vozila")
    }
}

struct SupportPlaceholderView: View {
    var body: some View {
        EmptyStateView(
            icon: "questionmark.circle.fill",
            title: "Podrska",
            message: "Ovde mozete kreirati tikete za podrsku.",
            actionTitle: "Novi tiket")
        .navigationTitle("Podrska")
    }
}

struct ProfilePlaceholderView: View {
    @EnvironmentObject private var session: SessionManager

    var body: some View {
        List {
            if let user = session.state.user {
                Section {
                    HStack(spacing: SpotLinkDesign.Spacing.md) {
                        Circle()
                            .fill(SpotLinkDesign.Colors.tint)
                            .frame(width: 56, height: 56)
                            .overlay {
                                Text(user.initials)
                                    .font(.headline)
                                    .foregroundStyle(.white)
                            }
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(user.fullName)
                                .font(SpotLinkDesign.Typography.headline)
                            Text(user.email)
                                .font(SpotLinkDesign.Typography.subheadline)
                                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                        }
                    }
                    .padding(.vertical, SpotLinkDesign.Spacing.sm)
                }
            }

            Section("Nalog") {
                NavigationLink("Podaci o nalogu") {
                    Text("Podaci o nalogu")
                        .navigationTitle("Nalog")
                }
                NavigationLink("Podesavanja") {
                    Text("Podesavanja")
                        .navigationTitle("Podesavanja")
                }
            }

            Section {
                Button(role: .destructive) {
                    Task {
                        session.signOut()
                    }
                } label: {
                    Label("Odjava", systemImage: "rectangle.portrait.and.arrow.right")
                }
            }
        }
        .navigationTitle("Profil")
        .spotlinkListStyle()
    }
}
