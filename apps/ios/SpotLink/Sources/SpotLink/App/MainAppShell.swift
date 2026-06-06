import SwiftUI

// MARK: - Main App Shell

/// Glavni tab shell za autentifikovane korisnike.
/// Prikazuje razlicite tabove u zavisnosti od korisnicke uloge.
public struct MainAppShell: View {

    let sessionInfo: SessionInfo

    @State private var selectedTab: AppTab
    @EnvironmentObject private var session: SessionManager
    @EnvironmentObject private var appContainer: SpotLinkAppContainer

    public init(sessionInfo: SessionInfo) {
        self.sessionInfo = sessionInfo
        #if DEBUG
        _selectedTab = State(initialValue: UITestFixtureConfiguration.shouldOpenProfileTab ? .profile : .search)
        #else
        _selectedTab = State(initialValue: .search)
        #endif
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
        .accessibilityIdentifier("main.tabView")
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
    @State private var isRequestingAccountDeletion = false
    @State private var showAccountDeletionConfirmation = false
    @State private var notificationPreferences = NotificationPreferenceForm()
    @State private var preferencesLoaded = false
    @State private var isLoadingPreferences = false
    @State private var isSavingPreferences = false
    @State private var preferenceStatus: String?
    @State private var profileAlert: ProfileAlert?
    private let legalConfiguration = LegalConfiguration.current()

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
                .accessibilityIdentifier("profile.screen")
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

            Section("Podesavanja obavestenja") {
                if isLoadingPreferences && !preferencesLoaded {
                    ProgressView("Ucitavanje podesavanja...")
                        .accessibilityIdentifier("profile.notifications.loading")
                }

                Toggle("Rezervacije", isOn: $notificationPreferences.reservationAlerts)
                    .accessibilityIdentifier("profile.notifications.reservationToggle")
                    .accessibilityLabel("Obavestenja za rezervacije")
                Toggle("Placanja", isOn: $notificationPreferences.paymentAlerts)
                    .accessibilityIdentifier("profile.notifications.paymentToggle")
                    .accessibilityLabel("Obavestenja za placanja")
                Toggle("Odgovori podrske", isOn: $notificationPreferences.supportAlerts)
                    .accessibilityIdentifier("profile.notifications.supportToggle")
                    .accessibilityLabel("Obavestenja za odgovore podrske")
                Toggle("Marketing saglasnost", isOn: $notificationPreferences.marketingOptIn)
                    .accessibilityIdentifier("profile.notifications.marketingToggle")
                    .accessibilityLabel("Marketing saglasnost")

                Button {
                    Task {
                        await saveNotificationPreferences()
                    }
                } label: {
                    Label(
                        isSavingPreferences ? "Cuvanje..." : "Sacuvaj podesavanja",
                        systemImage: "checkmark.circle"
                    )
                }
                .disabled(isLoadingPreferences || isSavingPreferences)
                .accessibilityIdentifier("profile.notifications.saveButton")

                if let preferenceStatus {
                    Text(preferenceStatus)
                        .font(SpotLinkDesign.Typography.footnote)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                        .accessibilityIdentifier("profile.notifications.statusText")
                }
            }

            Section("Nalog") {
                infoRow(title: "Tip korisnika", value: sessionInfo.user.isOperator ? "Partner / operator" : "Kupac")
                infoRow(title: "Podrska", value: sessionInfo.user.isSupport ? "Ukljucena" : "Standardna korisnicka podrska")

                Button(role: .destructive) {
                    showAccountDeletionConfirmation = true
                } label: {
                    Label(
                        isRequestingAccountDeletion ? "Slanje zahteva..." : "Zatrazi brisanje naloga",
                        systemImage: "trash"
                    )
                }
                .disabled(isRequestingAccountDeletion)
                .accessibilityIdentifier("profile.accountDeletion.requestButton")
                .accessibilityLabel("Zatrazi brisanje naloga")
                .accessibilityHint("Salje zahtev podrsci. Nalog se ne brise automatski.")
            }

            Section("Privatnost i podrska") {
                Link(destination: legalConfiguration.privacyPolicyURL) {
                    Label("Politika privatnosti", systemImage: "hand.raised")
                }
                .accessibilityIdentifier("profile.privacyPolicy.link")
                Link(destination: legalConfiguration.termsURL) {
                    Label("Uslovi koriscenja", systemImage: "doc.text")
                }
                .accessibilityIdentifier("profile.terms.link")
                Link(destination: legalConfiguration.supportURL) {
                    Label("Centar za podrsku", systemImage: "questionmark.circle")
                }
                .accessibilityIdentifier("profile.support.link")
                Link(destination: legalConfiguration.supportMailURL) {
                    Label(legalConfiguration.supportEmail, systemImage: "envelope")
                }
                .accessibilityIdentifier("profile.supportEmail.link")
                Link(destination: legalConfiguration.accountDeletionURL) {
                    Label("Informacije o brisanju naloga", systemImage: "person.crop.circle.badge.xmark")
                }
                .accessibilityIdentifier("profile.accountDeletion.infoLink")
            }

            Section {
                Button(role: .destructive) {
                    Task {
                        await appContainer.authService.logout()
                    }
                } label: {
                    Label("Odjava", systemImage: "rectangle.portrait.and.arrow.right")
                }
                .accessibilityIdentifier("profile.logout.button")
                .accessibilityHint("Odjavljuje trenutnu sesiju sa ovog uredjaja.")
            }
        }
        .navigationTitle("Profil")
        .spotlinkListStyle()
        .accessibilityIdentifier("profile.screen")
        .task {
            await loadNotificationPreferencesIfNeeded()
        }
        .confirmationDialog(
            "Zatrazi brisanje naloga?",
            isPresented: $showAccountDeletionConfirmation,
            titleVisibility: .visible
        ) {
            Button("Posalji zahtev", role: .destructive) {
                Task {
                    await requestAccountDeletion()
                }
            }
            Button("Odustani", role: .cancel) {}
        } message: {
            Text("SpotLink podrska ce pregledati zahtev pre bilo kakvog brisanja ili anonimizacije naloga.")
        }
        .alert(item: $profileAlert) { alert in
            Alert(
                title: Text(alert.title),
                message: Text(alert.message),
                dismissButton: .default(Text("U redu"))
            )
        }
    }

    @MainActor
    private func loadNotificationPreferencesIfNeeded() async {
        guard !preferencesLoaded, !isLoadingPreferences else { return }

        #if DEBUG
        if UITestFixtureConfiguration.isUITesting {
            notificationPreferences.apply(UITestFixtureConfiguration.authenticatedUserPreferences())
            preferencesLoaded = true
            preferenceStatus = nil
            return
        }
        #endif

        isLoadingPreferences = true
        defer { isLoadingPreferences = false }

        do {
            let profile = try await appContainer.profileService.getCurrentProfile()
            notificationPreferences.apply(profile.preferences)
            preferencesLoaded = true
            preferenceStatus = nil
        } catch let error as APIError {
            SpotLinkLogger.warn("notification_preferences_load_failed code=\(error.code ?? "-") requestId=\(error.requestId ?? "-")")
            preferencesLoaded = true
            preferenceStatus = "Podesavanja trenutno nisu dostupna."
        } catch {
            SpotLinkLogger.warn("notification_preferences_load_failed error=\(error.localizedDescription)")
            preferencesLoaded = true
            preferenceStatus = "Podesavanja trenutno nisu dostupna."
        }
    }

    @MainActor
    private func saveNotificationPreferences() async {
        guard !isSavingPreferences else { return }
        isSavingPreferences = true
        preferenceStatus = nil
        defer { isSavingPreferences = false }

        do {
            let profile = try await appContainer.profileService.updateNotificationPreferences(notificationPreferences.updateRequest)
            notificationPreferences.apply(profile.preferences)
            preferencesLoaded = true
            preferenceStatus = "Podesavanja sacuvana."
        } catch let error as APIError {
            SpotLinkLogger.warn("notification_preferences_save_failed code=\(error.code ?? "-") requestId=\(error.requestId ?? "-")")
            preferenceStatus = error.userFacingMessageWithReference
        } catch {
            SpotLinkLogger.warn("notification_preferences_save_failed error=\(error.localizedDescription)")
            preferenceStatus = "Podesavanja trenutno nije moguce sacuvati."
        }
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

    @MainActor
    private func requestAccountDeletion() async {
        guard !isRequestingAccountDeletion else { return }
        isRequestingAccountDeletion = true
        defer { isRequestingAccountDeletion = false }

        do {
            let ticket = try await appContainer.profileService.requestAccountDeletion()
            profileAlert = ProfileAlert(
                title: "Zahtev poslat",
                message: "Zahtev za brisanje naloga je evidentiran (tiket \(String(ticket.id.prefix(8)))). Podrska ce obraditi zahtev. Odredjeni zapisi mogu biti zadrzani zbog zakonskih, platnih ili zastite od zloupotrebe."
            )
        } catch let error as APIError {
            SpotLinkLogger.warn("account_deletion_request_failed code=\(error.code ?? "-") requestId=\(error.requestId ?? "-")")
            profileAlert = ProfileAlert(title: "Zahtev nije poslat", message: error.userFacingMessageWithReference)
        } catch {
            SpotLinkLogger.warn("account_deletion_request_failed error=\(error.localizedDescription)")
            profileAlert = ProfileAlert(
                title: "Zahtev nije poslat",
                message: "Zahtev trenutno nije moguce poslati. Pokusajte ponovo."
            )
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

    private struct ProfileAlert: Identifiable {
        let id = UUID()
        let title: String
        let message: String
    }

    private struct NotificationPreferenceForm {
        var reservationAlerts = true
        var paymentAlerts = true
        var supportAlerts = true
        var marketingOptIn = false

        var updateRequest: UpdateUserPreferencesRequest {
            UpdateUserPreferencesRequest(
                marketingOptIn: marketingOptIn,
                reservationAlerts: reservationAlerts,
                paymentAlerts: paymentAlerts,
                supportAlerts: supportAlerts
            )
        }

        mutating func apply(_ preferences: UserPreferences) {
            reservationAlerts = preferences.reservationAlerts
            paymentAlerts = preferences.paymentAlerts
            supportAlerts = preferences.supportAlerts
            marketingOptIn = preferences.marketingOptIn
        }
    }
}
