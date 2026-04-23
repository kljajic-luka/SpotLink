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
    @Published public private(set) var resolvedContext: ReservationResolvedContext?
    @Published public private(set) var isLoading = false
    @Published public private(set) var isCancelling = false
    @Published public private(set) var errorMessage: String?

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
        defer { isLoading = false }

        do {
            let loadedReservation = try await reservationService.getReservation(reservationId)
            reservation = loadedReservation

            if resolvedContext == nil {
                let location = try await locationService.getLocation(loadedReservation.locationId)
                let resources = try await locationService.listResources(locationId: loadedReservation.locationId)
                let resource = resources.first(where: { $0.id == loadedReservation.resourceId })
                resolvedContext = ReservationResolvedContext(location: location, resource: resource, vehicle: nil)
            }
        } catch let error as APIError {
            errorMessage = error.userFacingMessage
        } catch {
            errorMessage = "Detalji rezervacije trenutno nisu dostupni."
        }
    }

    public func cancelReservation() async {
        guard let reservation else { return }

        isCancelling = true
        errorMessage = nil
        defer { isCancelling = false }

        do {
            self.reservation = try await reservationService.cancel(
                reservation.id,
                reason: "Korisnik otkazao rezervaciju iz iOS aplikacije."
            )
        } catch let error as APIError {
            errorMessage = error.userFacingMessage
        } catch {
            errorMessage = "Rezervaciju trenutno nije moguce otkazati."
        }
    }
}

public struct ReservationDetailView: View {
    @StateObject private var viewModel: ReservationDetailViewModel

    private let supportService: SupportService
    @State private var showSupportComposer = false

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

                        ReservationHeaderCard(
                            title: context.location.name,
                            subtitle: context.location.address.displayAddress,
                            reservation: reservation,
                            resource: context.resource
                        )

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
                                await viewModel.cancelReservation()
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
    let formatter = DateFormatter()
    formatter.timeZone = TimeZone(identifier: reservation.timezone) ?? .current
    formatter.dateFormat = "d. MMM HH:mm"
    return (
        formatter.string(from: reservation.startsAt),
        formatter.string(from: reservation.endsAt)
    )
}

func accessInstructionsText(
    reservation: Reservation,
    location: ParkingLocation,
    resource: ParkingResource?
) -> String {
    guard reservation.accessInstructionsVisible else {
        return "Instrukcije za ulaz ce biti vidljive odmah nakon potvrde placanja i partner provere pristupa."
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