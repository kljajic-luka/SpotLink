import Foundation

// MARK: - App Container

@MainActor
public final class SpotLinkAppContainer: ObservableObject {
    public let environment: AppEnvironment
    public let session: SessionManager
    public let apiClient: APIClient
    public let diagnosticsReporter: DiagnosticsReporter
    public let diagnosticsContext: AppDiagnosticsContext

    public let authService: AuthService
    public let locationService: LocationService
    public let reservationService: ReservationService
    public let vehicleService: VehicleService
    public let paymentService: PaymentService
    public let supportService: SupportService
    public let profileService: ProfileService
    public let notificationService: NotificationService

    public let locationManager: SpotLinkLocationManager
    public let pushManager: PushNotificationManager
    public let searchViewModel: SearchMapViewModel
    public let mapProvider: SpotLinkMapProvider

    public init(
        environment: AppEnvironment,
        session: SessionManager = .shared,
        locationManager: SpotLinkLocationManager = .shared,
        pushManager: PushNotificationManager = .shared,
        diagnosticsReporter: DiagnosticsReporter? = nil
    ) {
        self.environment = environment
        self.session = session
        self.locationManager = locationManager
        self.pushManager = pushManager
        self.diagnosticsContext = AppDiagnosticsContext.current(environment: environment)
        self.diagnosticsReporter = diagnosticsReporter ?? Self.defaultDiagnosticsReporter()

        self.apiClient = APIClient(
            baseURL: environment.apiBaseURL,
            tokenProvider: session,
            unauthorizedHandler: { [weak session] in
                await session?.handleRemoteUnauthorized()
            },
            diagnosticsReporter: self.diagnosticsReporter,
            diagnosticsContext: self.diagnosticsContext
        )
        self.locationService = LocationService(apiClient: apiClient)
        self.reservationService = ReservationService(apiClient: apiClient)
        self.vehicleService = VehicleService(apiClient: apiClient)
        self.paymentService = PaymentService(apiClient: apiClient)
        self.supportService = SupportService(apiClient: apiClient)
        self.profileService = ProfileService(apiClient: apiClient)
        self.notificationService = NotificationService(apiClient: apiClient)
        self.authService = AuthService(apiClient: apiClient, session: session, pushLifecycle: pushManager)
        self.searchViewModel = SearchMapViewModel(locationService: locationService, locationManager: locationManager)
        self.mapProvider = MapRuntimeConfiguration.configureMapProvider()

        pushManager.configure(notificationService: notificationService)
    }

    private static func defaultDiagnosticsReporter() -> DiagnosticsReporter {
        #if DEBUG
        let raw = ProcessInfo.processInfo.environment["SPOTLINK_DEBUG_DIAGNOSTICS_ENABLED"]
        let enabled = raw == "1" || raw?.lowercased() == "true"
        return InMemoryDiagnosticsReporter(enabled: enabled)
        #else
        return NoopDiagnosticsReporter()
        #endif
    }
}
