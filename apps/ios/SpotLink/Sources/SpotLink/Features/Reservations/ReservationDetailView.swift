import SwiftUI

#if canImport(MapKit)
import MapKit
#endif

public struct ReservationResolvedContext: Sendable {
    public let location: ParkingLocation
    public let resource: ParkingResource?
    public let vehicle: VehicleProfile?

    public init(location: ParkingLocation, resource: ParkingResource?, vehicle: VehicleProfile?) {
        self.location = location
        self.resource = resource
        self.vehicle = vehicle
    }
}

@MainActor
public final class ReservationDetailViewModel: ObservableObject {
    @Published public private(set) var reservation: Reservation?
    @Published public private(set) var bookingDetail: BookingDetail?
    @Published public private(set) var resolvedContext: ReservationResolvedContext?
    @Published public private(set) var isLoading = false
    @Published public private(set) var isCancelling = false
    @Published public private(set) var errorMessage: String?
    @Published public private(set) var successMessage: String?

    private let reservationId: String
    private let reservationService: ReservationService
    private let locationService: LocationService
    private var didLoad = false

    public init(
        reservationId: String,
        reservationService: ReservationService,
        locationService: LocationService,
        resolvedContext: ReservationResolvedContext? = nil,
        reservation: Reservation? = nil
    ) {
        self.reservationId = reservationId
        self.reservationService = reservationService
        self.locationService = locationService
        self.resolvedContext = resolvedContext
        self.reservation = reservation
    }

    public func loadIfNeeded() async {
        guard !didLoad else { return }
        didLoad = true
        await load()
    }

    public func load() async {
        isLoading = true
        errorMessage = nil
        successMessage = nil
        defer { isLoading = false }

        do {
            let detail = try await reservationService.getReservationDetail(reservationId)
            bookingDetail = detail
            let loadedReservation = detail.reservation
            reservation = loadedReservation

            if resolvedContext == nil {
                let location = try await locationService.getLocation(loadedReservation.locationId)
                let resources = try await locationService.listResources(locationId: loadedReservation.locationId)
                let resource = resources.first(where: { $0.id == loadedReservation.resourceId })
                resolvedContext = ReservationResolvedContext(location: location, resource: resource, vehicle: nil)
            }
        } catch let error as APIError {
            setAPIError(error, operation: "reservation_detail_load")
        } catch {
            errorMessage = "Detalji rezervacije trenutno nisu dostupni."
        }
    }

    public func cancelReservation() async {
        guard let reservation else { return }

        isCancelling = true
        errorMessage = nil
        successMessage = nil
        defer { isCancelling = false }

        do {
            let cancelledReservation = try await reservationService.cancel(
                reservation.id,
                reason: "Korisnik otkazao rezervaciju iz iOS aplikacije."
            )
            self.reservation = cancelledReservation
            if let refreshedDetail = try? await reservationService.getReservationDetail(reservation.id) {
                self.bookingDetail = refreshedDetail
                self.reservation = refreshedDetail.reservation
            }
            successMessage = "Rezervacija je otkazana."
        } catch let error as APIError {
            setAPIError(error, operation: "reservation_cancel")
        } catch {
            errorMessage = "Rezervaciju trenutno nije moguce otkazati."
        }
    }

    private func setAPIError(_ error: APIError, operation: String) {
        SpotLinkLogger.warn("reservation_detail_operation_failed operation=\(operation) code=\(error.code ?? "-") requestId=\(error.requestId ?? "-")")
        errorMessage = error.userFacingMessageWithReference
    }
}

public struct ReservationDetailView: View {
    @StateObject private var viewModel: ReservationDetailViewModel

    private let supportService: SupportService
    @State private var showSupportComposer = false
    @State private var showCancelConfirmation = false

    public init(
        reservationId: String,
        reservationService: ReservationService,
        locationService: LocationService,
        supportService: SupportService,
        resolvedContext: ReservationResolvedContext? = nil,
        reservation: Reservation? = nil
    ) {
        _viewModel = StateObject(wrappedValue: ReservationDetailViewModel(
            reservationId: reservationId,
            reservationService: reservationService,
            locationService: locationService,
            resolvedContext: resolvedContext,
            reservation: reservation
        ))
        self.supportService = supportService
    }

    public var body: some View {
        Group {
            if let reservation = viewModel.reservation,
               let context = viewModel.resolvedContext {
                ScrollView {
                    VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.lg) {
                        if let error = viewModel.errorMessage {
                            ErrorBanner(error)
                        }

                        if let success = viewModel.successMessage {
                            SuccessBanner(success)
                        }

                        ReservationHeaderCard(
                            title: context.location.name,
                            subtitle: context.location.address.displayAddress,
                            reservation: reservation,
                            resource: context.resource
                        )

                        ReservationPaymentStateCard(
                            reservation: reservation,
                            quoteTotal: reservation.totalAmountFormatted,
                            paymentIntent: nil,
                            paymentResult: nil,
                            paymentMethod: nil
                        )

                        if let detail = viewModel.bookingDetail {
                            ReservationCustomerTimelineCard(
                                events: detail.customerVisibleTimeline,
                                timezone: reservation.timezone
                            )

                            ReservationPaymentAttemptsCard(
                                attempts: detail.paymentAttempts
                            )
                        }

                        ReservationTimelineCard(
                            reservation: reservation,
                            resource: context.resource,
                            location: context.location
                        )

                        ReservationTrustCard(
                            reservation: reservation,
                            location: context.location,
                            resource: context.resource
                        )

                        ReservationActionsCard(
                            reservation: reservation,
                            location: context.location,
                            openSupport: { showSupportComposer = true }
                        )

                        if reservation.status.canCancel {
                            LoadingButton(
                                "Otkazi rezervaciju",
                                isLoading: viewModel.isCancelling,
                                variant: .destructive
                            ) {
                                showCancelConfirmation = true
                            }
                        }
                    }
                    .padding(SpotLinkDesign.Spacing.md)
                }
            } else if viewModel.isLoading {
                LoadingView("Ucitavanje rezervacije...")
            } else if let error = viewModel.errorMessage {
                VStack(spacing: SpotLinkDesign.Spacing.md) {
                    ErrorBanner(error)
                    Button("Pokusaj ponovo") {
                        Task { await viewModel.load() }
                    }
                    .buttonStyle(SpotLinkButtonStyle(.secondary))
                }
                .padding(SpotLinkDesign.Spacing.md)
            } else {
                LoadingView("Priprema detalja rezervacije...")
            }
        }
        .navigationTitle("Detalji rezervacije")
        .spotlinkInlineNavigationTitle()
        .task { await viewModel.loadIfNeeded() }
        .confirmationDialog(
            "Otkazati rezervaciju?",
            isPresented: $showCancelConfirmation,
            titleVisibility: .visible
        ) {
            Button("Otkazi rezervaciju", role: .destructive) {
                Task { await viewModel.cancelReservation() }
            }
            Button("Zadrzi rezervaciju", role: .cancel) {}
        } message: {
            Text("Sistem ce proveriti da li je otkazivanje i dalje dozvoljeno za trenutno stanje rezervacije.")
        }
        .sheet(isPresented: $showSupportComposer) {
            if let reservation = viewModel.reservation,
               let context = viewModel.resolvedContext {
                NavigationStack {
                    SupportTicketComposerView(
                        service: supportService,
                        defaultCategory: .locationAccess,
                        defaultSubject: "Problem na ulazu za rezervaciju \(reservation.bookingCodePlaceholder)",
                        initialBody: "Problem na ulazu za lokaciju \(context.location.name). Booking code: \(reservation.bookingCodePlaceholder).",
                        reservationId: reservation.id,
                        locationId: reservation.locationId
                    )
                }
            }
        }
    }
}

struct ReservationHeaderCard: View {
    let title: String
    let subtitle: String
    let reservation: Reservation
    let resource: ParkingResource?

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs) {
                    Text(title)
                        .font(SpotLinkDesign.Typography.title3.weight(.bold))
                    Text(subtitle)
                        .font(SpotLinkDesign.Typography.callout)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }
                Spacer()
                StatusBadge(status: reservation.status)
            }

            Divider()

            ReservationFactGrid(items: [
                ReservationFact(title: "Rezervacija", value: reservation.id, icon: "number"),
                ReservationFact(title: "Booking code", value: reservation.bookingCodePlaceholder, icon: "qrcode"),
                ReservationFact(title: "Ukupno", value: reservation.totalAmountFormatted, icon: "creditcard"),
                ReservationFact(title: "Potvrda", value: resource?.confirmationMode.displayName ?? "Partner potvrda", icon: "checkmark.seal")
            ])
        }
        .padding(SpotLinkDesign.Spacing.md)
        .spotlinkCard()
    }
}

struct SuccessBanner: View {
    let message: String

    init(_ message: String) {
        self.message = message
    }

    var body: some View {
        HStack(alignment: .top, spacing: SpotLinkDesign.Spacing.sm) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(SpotLinkDesign.Colors.success)
                .accessibilityHidden(true)
            Text(message)
                .font(SpotLinkDesign.Typography.footnote)
                .foregroundStyle(SpotLinkDesign.Colors.label)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(SpotLinkDesign.Spacing.md)
        .background(SpotLinkDesign.Colors.success.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm))
        .accessibilityElement(children: .combine)
    }
}

struct ReservationPaymentStateCard: View {
    let reservation: Reservation
    let quoteTotal: String
    let paymentIntent: PaymentIntent?
    let paymentResult: PaymentProviderResult?
    let paymentMethod: PaymentMethod?

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            Text("Placanje i stanje")
                .font(SpotLinkDesign.Typography.headline)

            ReservationFactGrid(items: paymentFacts)

            if let holdMessage {
                ReservationInstructionBlock(
                    title: "Hold za online placanje",
                    message: holdMessage
                )
            }

            ReservationInstructionBlock(
                title: "Otkazivanje",
                message: cancellationText
            )

            ReservationInstructionBlock(
                title: "Podrska",
                message: "Za problem sa pristupom ili placanjem koristite akciju 'Problem na ulazu?' kako bi podrska dobila booking code \(reservation.bookingCodePlaceholder)."
            )
        }
        .padding(SpotLinkDesign.Spacing.md)
        .spotlinkCard()
    }

    private var paymentFacts: [ReservationFact] {
        var facts = [
            ReservationFact(title: "Rezim", value: reservation.paymentMode.displayName, icon: "wallet.pass"),
            ReservationFact(title: "Stanje", value: reservation.status.displayName, icon: "checkmark.seal"),
            ReservationFact(title: "Ukupno", value: quoteTotal, icon: "banknote"),
            ReservationFact(title: "Valuta", value: reservation.currency, icon: "dollarsign.circle")
        ]

        if let paymentResult {
            facts.append(ReservationFact(title: "Payment", value: paymentResult.status.displayName, icon: "creditcard"))
        }
        if let paymentIntent {
            facts.append(ReservationFact(title: "Intent", value: paymentIntent.id, icon: "number"))
        }
        if let paymentMethod {
            facts.append(ReservationFact(title: "Metoda", value: paymentMethod.formattedDescription, icon: "creditcard"))
        }
        if let holdId = reservation.holdId {
            facts.append(ReservationFact(title: "Hold", value: holdId, icon: "timer"))
        }
        if let inventoryPoolId = reservation.inventoryPoolId {
            facts.append(ReservationFact(title: "Pool", value: inventoryPoolId, icon: "square.stack.3d.up"))
        }
        return facts
    }

    private var holdMessage: String? {
        guard reservation.paymentMode == .online,
              reservation.status == .pendingPayment else {
            return nil
        }
        if let expiresAt = reservation.holdExpiresAt {
            return "Mesto je zadrzano do \(formatReservationDateTime(expiresAt, timezone: reservation.timezone)). Ako placanje ne bude potvrdjeno do tada, rezervacija moze isteci."
        }
        return "Online rezervacija nema prikazan istek holda. Kontaktirajte podrsku ako placanje ne prodje."
    }

    private var cancellationText: String {
        reservation.status.canCancel
            ? "Otkazivanje je dostupno za trenutno stanje. Sistem ce ponovo proveriti stanje pre promene."
            : "Otkazivanje nije dostupno za trenutno stanje rezervacije."
    }
}

struct ReservationCustomerTimelineCard: View {
    let events: [BookingEvent]
    let timezone: String

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            Text("Tok rezervacije")
                .font(SpotLinkDesign.Typography.headline)

            if events.isEmpty {
                Text("Nema dodatnih dogadjaja vidljivih korisniku.")
                    .font(SpotLinkDesign.Typography.callout)
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            } else {
                VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
                    ForEach(events) { event in
                        HStack(alignment: .top, spacing: SpotLinkDesign.Spacing.sm) {
                            Image(systemName: event.eventType.customerIcon)
                                .foregroundStyle(SpotLinkDesign.Colors.tint)
                                .frame(width: 24)
                            VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs) {
                                Text(event.eventType.customerTitle)
                                    .font(SpotLinkDesign.Typography.callout.weight(.semibold))
                                Text(formatReservationDateTime(event.occurredAt, timezone: timezone))
                                    .font(SpotLinkDesign.Typography.caption)
                                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                                if let notes = event.notes, !notes.isEmpty {
                                    Text(notes)
                                        .font(SpotLinkDesign.Typography.footnote)
                                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                                }
                            }
                        }
                    }
                }
            }
        }
        .padding(SpotLinkDesign.Spacing.md)
        .spotlinkCard()
    }
}

struct ReservationPaymentAttemptsCard: View {
    let attempts: [ReservationPaymentAttempt]

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            Text("Pokusaji placanja")
                .font(SpotLinkDesign.Typography.headline)

            if attempts.isEmpty {
                Text("Nema evidentiranih pokusaja placanja.")
                    .font(SpotLinkDesign.Typography.callout)
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            } else {
                ForEach(attempts) { attempt in
                    ReservationFactGrid(items: [
                        ReservationFact(title: "Provajder", value: attempt.provider, icon: "creditcard"),
                        ReservationFact(title: "Status", value: attempt.status.displayName, icon: attempt.status.icon),
                        ReservationFact(title: "Iznos", value: formatCents(attempt.amountCents, currency: attempt.currency), icon: "banknote"),
                        ReservationFact(title: "Rezim", value: attempt.paymentMode.displayName, icon: "wallet.pass")
                    ])

                    if let failureMessage = attempt.failureMessage, !failureMessage.isEmpty {
                        ReservationInstructionBlock(
                            title: "Razlog neuspeha",
                            message: failureMessage
                        )
                    }
                }
            }
        }
        .padding(SpotLinkDesign.Spacing.md)
        .spotlinkCard()
    }
}

struct ReservationTimelineCard: View {
    let reservation: Reservation
    let resource: ParkingResource?
    let location: ParkingLocation

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            Text("Dolazak i pristup")
                .font(SpotLinkDesign.Typography.headline)

            ReservationFactGrid(items: [
                ReservationFact(title: "Ulazak", value: reservationWindowText(reservation).entry, icon: "arrow.right.circle"),
                ReservationFact(title: "Izlazak", value: reservationWindowText(reservation).exit, icon: "arrow.left.circle"),
                ReservationFact(title: "Pristup", value: location.accessType.displayName, icon: location.accessType.systemIcon),
                ReservationFact(title: "Kapacitet", value: resource?.capacitySummary ?? "Partner kapacitet", icon: "car.2")
            ])

            ReservationInstructionBlock(
                title: "Instrukcije za ulaz",
                message: accessInstructionsText(reservation: reservation, location: location, resource: resource)
            )

            ReservationInstructionBlock(
                title: "Kasnjenje i grace period",
                message: gracePeriodText(resource: resource)
            )
        }
        .padding(SpotLinkDesign.Spacing.md)
        .spotlinkCard()
    }
}

private extension BookingEventType {
    var customerTitle: String {
        switch self {
        case .created:
            return "Rezervacija kreirana"
        case .holdCreated:
            return "Mesto je privremeno zadrzano"
        case .holdExpired:
            return "Hold je istekao"
        case .paymentAuthorized:
            return "Placanje je autorizovano"
        case .paymentFailed:
            return "Placanje nije uspelo"
        case .confirmed:
            return "Rezervacija je potvrdjena"
        case .cancelled:
            return "Rezervacija je otkazana"
        case .operatorCancelled:
            return "Operator je otkazao rezervaciju"
        case .checkedIn:
            return "Dolazak je evidentiran"
        case .noShow:
            return "Dolazak nije evidentiran"
        case .refundMarked:
            return "Refundacija je oznacena"
        case .legacyImported, .statusChanged, .adminOverride:
            return "Sistemska izmena"
        }
    }

    var customerIcon: String {
        switch self {
        case .paymentAuthorized, .confirmed:
            return "checkmark.circle.fill"
        case .paymentFailed, .holdExpired, .cancelled, .operatorCancelled, .noShow:
            return "exclamationmark.triangle.fill"
        case .holdCreated:
            return "timer"
        case .refundMarked:
            return "arrow.uturn.left.circle.fill"
        default:
            return "circle.fill"
        }
    }
}

private extension ReservationPaymentAttemptStatus {
    var displayName: String {
        switch self {
        case .pending:
            return "U toku"
        case .requiresAction:
            return "Ceka potvrdu"
        case .authorized:
            return "Autorizovano"
        case .failed:
            return "Neuspesno"
        case .cancelled:
            return "Otkazano"
        case .refundMarked:
            return "Refundacija oznacena"
        }
    }

    var icon: String {
        switch self {
        case .authorized:
            return "checkmark.seal"
        case .failed, .cancelled:
            return "xmark.octagon"
        case .refundMarked:
            return "arrow.uturn.left.circle"
        case .pending, .requiresAction:
            return "clock"
        }
    }
}

struct ReservationTrustCard: View {
    let reservation: Reservation
    let location: ParkingLocation
    let resource: ParkingResource?

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            Text("Poverenje i oporavak")
                .font(SpotLinkDesign.Typography.headline)

            ReservationInstructionBlock(
                title: "Partner garantuje mesto",
                message: "SpotLink prikazuje samo partnerski inventar sa garantovanom rezervacijom. Ako na ulazu postoji problem, podrska i operator dobijaju isti booking code: \(reservation.bookingCodePlaceholder)."
            )

            ReservationInstructionBlock(
                title: "QR i booking verifikacija",
                message: "QR kod je placeholder za narednu fazu. Za ovaj MVP koristite booking code i prikaz rezervacije na telefonu."
            )
        }
        .padding(SpotLinkDesign.Spacing.md)
        .spotlinkCard()
    }
}

struct ReservationActionsCard: View {
    let reservation: Reservation
    let location: ParkingLocation
    let openSupport: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            Text("Akcije")
                .font(SpotLinkDesign.Typography.headline)

            Button {
                openInMaps(location: location)
            } label: {
                Label("Otvori navigaciju", systemImage: "map")
            }
            .buttonStyle(SpotLinkButtonStyle(.secondary))

            Button {
                openSupport()
            } label: {
                Label("Problem na ulazu?", systemImage: "questionmark.circle")
            }
            .buttonStyle(SpotLinkButtonStyle(.ghost))
        }
        .padding(SpotLinkDesign.Spacing.md)
        .spotlinkCard()
    }
}

struct ReservationFact: Identifiable {
    let title: String
    let value: String
    let icon: String

    var id: String { title }
}

struct ReservationFactGrid: View {
    let items: [ReservationFact]

    private let columns = [
        GridItem(.flexible(), spacing: SpotLinkDesign.Spacing.sm),
        GridItem(.flexible(), spacing: SpotLinkDesign.Spacing.sm)
    ]

    var body: some View {
        LazyVGrid(columns: columns, alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            ForEach(items) { item in
                VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs) {
                    Label(item.title, systemImage: item.icon)
                        .font(SpotLinkDesign.Typography.caption)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    Text(item.value)
                        .font(SpotLinkDesign.Typography.callout.weight(.semibold))
                        .textSelection(.enabled)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(SpotLinkDesign.Spacing.sm)
                .background(SpotLinkDesign.Colors.secondaryBG)
                .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm))
            }
        }
    }
}

struct ReservationInstructionBlock: View {
    let title: String
    let message: String

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs) {
            Text(title)
                .font(SpotLinkDesign.Typography.footnote.weight(.semibold))
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            Text(message)
                .font(SpotLinkDesign.Typography.callout)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

func reservationWindowText(_ reservation: Reservation) -> (entry: String, exit: String) {
    (
        formatReservationDateTime(reservation.startsAt, timezone: reservation.timezone),
        formatReservationDateTime(reservation.endsAt, timezone: reservation.timezone)
    )
}

func formatReservationDateTime(_ date: Date, timezone: String) -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "sr_RS")
    formatter.timeZone = TimeZone(identifier: timezone) ?? TimeZone(identifier: "Europe/Belgrade") ?? .current
    formatter.dateFormat = "d. MMM HH:mm"
    return formatter.string(from: date)
}

func accessInstructionsText(
    reservation: Reservation,
    location: ParkingLocation,
    resource: ParkingResource?
) -> String {
    guard reservation.accessInstructionsVisible else {
        if reservation.paymentMode == .online && reservation.status == .pendingPayment {
            return "Instrukcije za ulaz ce biti vidljive kada placanje bude potvrdjeno pre isteka holda."
        }
        return "Instrukcije za ulaz trenutno nisu dostupne za stanje \(reservation.status.displayName.lowercased())."
    }

    switch location.accessType {
    case .gateCode:
        return "Na ulazu unesite booking code \(reservation.bookingCodePlaceholder). Ako kapija ne reaguje u roku od 60 sekundi, koristite akciju 'Problem na ulazu?'."
    case .attendant:
        return "Pokazite booking code \(reservation.bookingCodePlaceholder) dezurnom osoblju i pratite oznaku partner lokacije."
    case .valet:
        return "Zaustavite se na drop-off zoni, pokazite booking code i ostanite dostupni na telefonu za preuzimanje vozila."
    case .appUnlock:
        return "Digitalno otkljucavanje dolazi u sledecoj fazi. Za sada koristite booking code i partner instrukcije sa lokacije."
    case .selfPark:
        return "Pratite oznake partner garaze i parkirajte na dodeljenom mestu. Booking code \(reservation.bookingCodePlaceholder) cuvajte pri izlasku."
    }
}

func gracePeriodText(resource: ParkingResource?) -> String {
    let modeText = resource?.confirmationMode == .manual
        ? "Partner tim prati manuelne potvrde i tolerise do 15 minuta kasnjenja kada je dolazak najavljen podrskom."
        : "Instant potvrda ukljucuje privremeni grace period od 15 minuta za dolazak i ulaznu verifikaciju."
    return modeText
}

func openInMaps(location: ParkingLocation) {
#if canImport(MapKit)
    let coordinate = CLLocationCoordinate2D(
        latitude: location.coordinates.latitude,
        longitude: location.coordinates.longitude
    )
    let placemark = MKPlacemark(coordinate: coordinate)
    let item = MKMapItem(placemark: placemark)
    item.name = location.name
    item.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDriving])
#endif
}
