import SwiftUI

public struct ReservationConfirmationContext {
    public let reservation: Reservation
    public let quote: ReservationQuote
    public let paymentIntent: PaymentIntent
    public let paymentResult: PaymentProviderResult
    public let paymentMethod: PaymentMethod?
    public let resolvedContext: ReservationResolvedContext
}

@MainActor
public final class ReservationBookingViewModel: ObservableObject {
    @Published public private(set) var resources: [ParkingResource]
    @Published public var selectedResourceId: String
    @Published public var startsAt: Date
    @Published public var endsAt: Date
    @Published public var selectedVehicleId: String?
    @Published public var selectedPaymentMethodId: String?
    @Published public var promoCode: String = ""

    @Published public private(set) var vehicles: [VehicleProfile] = []
    @Published public private(set) var paymentMethods: [PaymentMethod] = []
    @Published public private(set) var quote: ReservationQuote?
    @Published public private(set) var confirmationContext: ReservationConfirmationContext?
    @Published public private(set) var isLoading = false
    @Published public private(set) var isQuoting = false
    @Published public private(set) var isSubmitting = false
    @Published public private(set) var errorMessage: String?

    public let result: LocationSearchResult

    private let pendingOperations = PendingOperationStore()
    private var didLoad = false

    public init(result: LocationSearchResult, initialStartsAt: Date, initialEndsAt: Date) {
        self.result = result
        self.resources = result.resources
        self.selectedResourceId = result.resources.first?.id ?? ""
        self.startsAt = initialStartsAt
        self.endsAt = initialEndsAt
    }

    public var selectedResource: ParkingResource? {
        resources.first(where: { $0.id == selectedResourceId })
    }

    public var selectedVehicle: VehicleProfile? {
        vehicles.first(where: { $0.id == selectedVehicleId })
    }

    public var selectedPaymentMethod: PaymentMethod? {
        paymentMethods.first(where: { $0.id == selectedPaymentMethodId })
    }

    public var requiresVehicleSelection: Bool {
        guard let resource = selectedResource else { return false }
        return resource.fitRule != nil
    }

    public func loadIfNeeded(
        reservationService: ReservationService,
        locationService: LocationService,
        vehicleService: VehicleService,
        paymentService: PaymentService
    ) async {
        guard !didLoad else { return }
        didLoad = true

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            if resources.isEmpty {
                resources = try await locationService.listResources(locationId: result.location.id)
                selectedResourceId = resources.first?.id ?? ""
            }

            vehicles = try await vehicleService.listMyVehicles()
            paymentMethods = try await paymentService.listPaymentMethods()
            selectedPaymentMethodId = paymentMethods.first(where: { $0.isDefault })?.id ?? paymentMethods.first?.id

            if requiresVehicleSelection && selectedVehicleId == nil {
                selectedVehicleId = vehicles.first?.id
            }

            await refreshQuote(service: reservationService)
        } catch let error as APIError {
            errorMessage = error.userFacingMessage
        } catch {
            errorMessage = "Priprema rezervacije trenutno nije uspela."
        }
    }

    public func invalidateQuote() {
        quote = nil
        errorMessage = nil
    }

    public func clearConfirmation() {
        confirmationContext = nil
    }

    public func refreshQuote(service: ReservationService) async {
        guard let selectedResource else {
            errorMessage = "Lokacija trenutno nema aktivno mesto za rezervaciju."
            return
        }

        guard startsAt < endsAt else {
            errorMessage = "Vreme izlaska mora biti nakon vremena ulaska."
            return
        }

        isQuoting = true
        errorMessage = nil
        defer { isQuoting = false }

        do {
            quote = try await service.quote(
                ReservationQuoteRequest(
                    resourceId: selectedResource.id,
                    vehicleId: selectedVehicleId,
                    startsAt: startsAt,
                    endsAt: endsAt,
                    promoCode: promoCode.nilIfBlank
                )
            )
        } catch let error as APIError {
            quote = nil
            errorMessage = error.userFacingMessage
        } catch {
            quote = nil
            errorMessage = "Ponuda trenutno nije dostupna."
        }
    }

    public func submitBooking(
        reservationService: ReservationService,
        paymentService: PaymentService
    ) async {
        guard let selectedResource else {
            errorMessage = "Izaberite mesto pre nastavka."
            return
        }

        if requiresVehicleSelection && selectedVehicleId == nil {
            errorMessage = vehicles.isEmpty
                ? "Za ovo mesto je potrebno vozilo. Dodajte vozilo pre rezervacije."
                : "Izaberite vozilo za ovo mesto."
            return
        }

        guard let paymentMethodId = selectedPaymentMethodId else {
            errorMessage = "Izaberite nacin placanja."
            return
        }

        if quote == nil {
            await refreshQuote(service: reservationService)
        }

        guard let quote else { return }

        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }

        do {
            let reservationKey = pendingOperations.key(for: reservationOperationId, prefix: "res")
            let reservation = try await reservationService.create(
                CreateReservationRequest(
                    resourceId: selectedResource.id,
                    vehicleId: selectedVehicleId,
                    startsAt: startsAt,
                    endsAt: endsAt,
                    promoCode: promoCode.nilIfBlank,
                    quoteId: nil,
                    paymentMethodId: paymentMethodId,
                    idempotencyKey: reservationKey
                )
            )

            let paymentKey = pendingOperations.key(
                for: paymentOperationId(reservationId: reservation.id, paymentMethodId: paymentMethodId),
                prefix: "pay"
            )
            let intent = try await paymentService.createIntent(
                CreatePaymentIntentRequest(
                    reservationId: reservation.id,
                    paymentMethodId: paymentMethodId,
                    idempotencyKey: paymentKey
                )
            )

            if let paymentError = blockingPaymentMessage(for: intent.status, redirectURL: intent.redirectUrl) {
                errorMessage = paymentError
                return
            }

            let paymentResult = try await paymentService.confirmIntent(intent.id)
            if let paymentError = blockingPaymentMessage(for: paymentResult.status, redirectURL: paymentResult.redirectUrl) {
                errorMessage = paymentError
                return
            }

            let confirmedReservation = (try? await reservationService.getReservation(reservation.id)) ?? reservation
            let resolvedContext = ReservationResolvedContext(
                location: result.location,
                resource: selectedResource,
                vehicle: selectedVehicle
            )

            confirmationContext = ReservationConfirmationContext(
                reservation: confirmedReservation,
                quote: quote,
                paymentIntent: intent,
                paymentResult: paymentResult,
                paymentMethod: selectedPaymentMethod,
                resolvedContext: resolvedContext
            )
            pendingOperations.resetAll()
        } catch let error as APIError {
            errorMessage = error.userFacingMessage
        } catch {
            errorMessage = "Rezervacija nije dovrsena. Pokusajte ponovo sa istom ponudom."
        }
    }

    private var reservationOperationId: String {
        [
            selectedResourceId,
            startsAt.iso8601String,
            endsAt.iso8601String,
            selectedVehicleId ?? "bez-vozila",
            promoCode.nilIfBlank ?? "bez-promo"
        ].joined(separator: "|")
    }

    private func paymentOperationId(reservationId: String, paymentMethodId: String) -> String {
        [reservationId, paymentMethodId].joined(separator: "|")
    }

    private func blockingPaymentMessage(for status: PaymentStatus, redirectURL: String?) -> String? {
        switch status {
        case .authorized, .captured:
            return nil
        case .requiresMethod:
            return "Placanje zahteva drugi nacin placanja pre potvrde rezervacije."
        case .requiresAction:
            if redirectURL?.isEmpty == false {
                return "Placanje zahteva dodatnu potvrdu kod provajdera pre zavrsetka rezervacije."
            }
            return "Placanje zahteva dodatnu potvrdu pre zavrsetka rezervacije."
        case .failed, .cancelled:
            return "Placanje nije autorizovano. Pokusajte drugim nacinom placanja."
        case .refunded:
            return "Placanje je refundirano i rezervacija nije potvrdena."
        }
    }
}

public struct ReservationBookingFlowView: View {
    @EnvironmentObject private var appContainer: SpotLinkAppContainer
    @StateObject private var viewModel: ReservationBookingViewModel

    public init(result: LocationSearchResult, initialStartsAt: Date, initialEndsAt: Date) {
        _viewModel = StateObject(wrappedValue: ReservationBookingViewModel(
            result: result,
            initialStartsAt: initialStartsAt,
            initialEndsAt: initialEndsAt
        ))
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.lg) {
                if let error = viewModel.errorMessage {
                    ErrorBanner(error)
                }

                ReservationPreviewHero(result: viewModel.result, resource: viewModel.selectedResource)

                bookingWindowSection
                resourceSection
                vehicleSection
                paymentSection
                quoteSection
                trustSection
                actionSection
            }
            .padding(SpotLinkDesign.Spacing.md)
        }
        .navigationTitle("Rezervacija")
        .spotlinkInlineNavigationTitle()
        .task {
            await viewModel.loadIfNeeded(
                reservationService: appContainer.reservationService,
                locationService: appContainer.locationService,
                vehicleService: appContainer.vehicleService,
                paymentService: appContainer.paymentService
            )
        }
        .navigationDestination(
            isPresented: Binding(
                get: { viewModel.confirmationContext != nil },
                set: { isPresented in
                    if !isPresented {
                        viewModel.clearConfirmation()
                    }
                }
            )
        ) {
            if let context = viewModel.confirmationContext {
                ReservationConfirmationView(context: context)
            }
        }
    }

    private var bookingWindowSection: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            Text("Termin")
                .font(SpotLinkDesign.Typography.headline)

            DatePicker("Ulazak", selection: $viewModel.startsAt, displayedComponents: [.date, .hourAndMinute])
                .onChange(of: viewModel.startsAt) { _, _ in
                    viewModel.invalidateQuote()
                }
            DatePicker("Izlazak", selection: $viewModel.endsAt, displayedComponents: [.date, .hourAndMinute])
                .onChange(of: viewModel.endsAt) { _, _ in
                    viewModel.invalidateQuote()
                }
        }
        .padding(SpotLinkDesign.Spacing.md)
        .spotlinkCard()
    }

    private var resourceSection: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            Text("Partner mesto")
                .font(SpotLinkDesign.Typography.headline)

            if viewModel.resources.isEmpty && viewModel.isLoading {
                ProgressView("Ucitavanje mesta")
            } else {
                Picker("Mesto", selection: $viewModel.selectedResourceId) {
                    ForEach(viewModel.resources) { resource in
                        Text("\(resource.label) • \(resource.hourlyRateFormatted)")
                            .tag(resource.id)
                    }
                }
                .pickerStyle(.menu)
                .onChange(of: viewModel.selectedResourceId) { _, _ in
                    viewModel.invalidateQuote()
                }

                if let resource = viewModel.selectedResource {
                    ReservationFactGrid(items: [
                        ReservationFact(title: "Tip", value: resource.type.displayName, icon: resource.type.systemIcon),
                        ReservationFact(title: "Potvrda", value: resource.confirmationMode.displayName, icon: "checkmark.seal"),
                        ReservationFact(title: "Kapacitet", value: resource.capacitySummary, icon: "car.2"),
                        ReservationFact(title: "Pristup", value: viewModel.result.location.accessType.displayName, icon: viewModel.result.location.accessType.systemIcon)
                    ])
                }
            }
        }
        .padding(SpotLinkDesign.Spacing.md)
        .spotlinkCard()
    }

    private var vehicleSection: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            Text("Vozilo")
                .font(SpotLinkDesign.Typography.headline)

            if viewModel.vehicles.isEmpty {
                Text(viewModel.requiresVehicleSelection
                     ? "Za ovo mesto je potrebno da izaberete vozilo. Trenutno nema sacuvanih vozila na nalogu."
                     : "Vozilo nije obavezno za ovu rezervaciju, ali pomaze partneru pri ulazu.")
                    .font(SpotLinkDesign.Typography.callout)
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            } else {
                Picker("Izaberite vozilo", selection: $viewModel.selectedVehicleId) {
                    if !viewModel.requiresVehicleSelection {
                        Text("Bez vozila")
                            .tag(Optional<String>.none)
                    }
                    ForEach(viewModel.vehicles) { vehicle in
                        Text(vehicle.displayName)
                            .tag(Optional(vehicle.id))
                    }
                }
                .pickerStyle(.menu)
                .onChange(of: viewModel.selectedVehicleId) { _, _ in
                    viewModel.invalidateQuote()
                }

                if let vehicle = viewModel.selectedVehicle {
                    Text(vehicle.licensePlate ?? vehicle.displayName)
                        .font(SpotLinkDesign.Typography.footnote)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }
            }
        }
        .padding(SpotLinkDesign.Spacing.md)
        .spotlinkCard()
    }

    private var paymentSection: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            Text("Placanje")
                .font(SpotLinkDesign.Typography.headline)

            if viewModel.paymentMethods.isEmpty {
                Text("Nema dostupnih nacina placanja.")
                    .font(SpotLinkDesign.Typography.callout)
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            } else {
                Picker("Kartica", selection: $viewModel.selectedPaymentMethodId) {
                    ForEach(viewModel.paymentMethods) { method in
                        Text(method.formattedDescription)
                            .tag(Optional(method.id))
                    }
                }
                .pickerStyle(.menu)

                if let method = viewModel.selectedPaymentMethod {
                    Text("Placanje ide kroz interni mock payment flow. Izabrana metoda: \(method.formattedDescription).")
                        .font(SpotLinkDesign.Typography.footnote)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }
            }
        }
        .padding(SpotLinkDesign.Spacing.md)
        .spotlinkCard()
    }

    private var quoteSection: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            Text("Ponuda")
                .font(SpotLinkDesign.Typography.headline)

            if let quote = viewModel.quote {
                ReservationFactGrid(items: [
                    ReservationFact(title: "Osnovica", value: quote.subtotalFormatted, icon: "dollarsign.circle"),
                    ReservationFact(title: "Naknade", value: quote.feesFormatted, icon: "percent"),
                    ReservationFact(title: "Popust", value: quote.discountFormatted, icon: "tag"),
                    ReservationFact(title: "Ukupno", value: quote.totalAmountFormatted, icon: "creditcard")
                ])

                if let expiresAt = quote.expiresAt {
                    Text("Ponuda vazi do \(expiresAt.formatted(style: .short)).")
                        .font(SpotLinkDesign.Typography.footnote)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }
            } else if viewModel.isQuoting {
                ProgressView("Ucitavanje ponude...")
            } else {
                Text("Potvrdite termin i osvezite ponudu pre rezervacije.")
                    .font(SpotLinkDesign.Typography.callout)
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            }
        }
        .padding(SpotLinkDesign.Spacing.md)
        .spotlinkCard()
    }

    private var trustSection: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            Text("Sta dobijate")
                .font(SpotLinkDesign.Typography.headline)

            ReservationInstructionBlock(
                title: "Garantovana rezervacija",
                message: "SpotLink prikazuje samo partnerski, off-street inventar. Rezervacija dobija booking code i podrsku za oporavak ako dodje do problema na ulazu."
            )
            ReservationInstructionBlock(
                title: "Pristup i potvrda",
                message: "Ulaz je vezan za \(viewModel.result.location.accessType.displayName.lowercased()) i \((viewModel.selectedResource?.confirmationMode.displayName ?? "partner potvrdu").lowercased())."
            )
        }
        .padding(SpotLinkDesign.Spacing.md)
        .spotlinkCard()
    }

    private var actionSection: some View {
        VStack(spacing: SpotLinkDesign.Spacing.sm) {
            LoadingButton("Osvezi ponudu", isLoading: viewModel.isQuoting, variant: .secondary) {
                await viewModel.refreshQuote(service: appContainer.reservationService)
            }

            LoadingButton("Rezervisi i potvrdi placanje", isLoading: viewModel.isSubmitting) {
                await viewModel.submitBooking(
                    reservationService: appContainer.reservationService,
                    paymentService: appContainer.paymentService
                )
            }
        }
    }
}

struct ReservationPreviewHero: View {
    let result: LocationSearchResult
    let resource: ParkingResource?

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            Text(result.location.name)
                .font(SpotLinkDesign.Typography.title3.weight(.bold))
            Text(result.location.address.displayAddress)
                .font(SpotLinkDesign.Typography.callout)
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)

            if let resource {
                Text("\(resource.type.displayName) • \(resource.label)")
                    .font(SpotLinkDesign.Typography.subheadline.weight(.semibold))
            }
        }
        .padding(SpotLinkDesign.Spacing.md)
        .spotlinkCard()
    }
}

struct ReservationConfirmationView: View {
    @EnvironmentObject private var appContainer: SpotLinkAppContainer
    @State private var showSupportComposer = false

    let context: ReservationConfirmationContext

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.lg) {
                VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
                    Label("Rezervacija je spremna", systemImage: "checkmark.circle.fill")
                        .font(SpotLinkDesign.Typography.title2.weight(.bold))
                        .foregroundStyle(SpotLinkDesign.Colors.success)
                    Text("Partner lokacija i booking code su sacuvani za dolazak. Ako na ulazu nesto krene po zlu, podrska vidi isti booking code i rezervaciju.")
                        .font(SpotLinkDesign.Typography.callout)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }

                ReservationHeaderCard(
                    title: context.resolvedContext.location.name,
                    subtitle: context.resolvedContext.location.address.displayAddress,
                    reservation: context.reservation,
                    resource: context.resolvedContext.resource
                )

                ReservationTimelineCard(
                    reservation: context.reservation,
                    resource: context.resolvedContext.resource,
                    location: context.resolvedContext.location
                )

                VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
                    Text("Placanje")
                        .font(SpotLinkDesign.Typography.headline)
                    ReservationFactGrid(items: [
                        ReservationFact(title: "Intent", value: context.paymentIntent.id, icon: "number"),
                        ReservationFact(title: "Status", value: context.paymentResult.status.displayName, icon: "creditcard"),
                        ReservationFact(title: "Metoda", value: context.paymentMethod?.formattedDescription ?? "Partner default", icon: "wallet.pass"),
                        ReservationFact(title: "Ukupno", value: context.quote.totalAmountFormatted, icon: "banknote")
                    ])
                }
                .padding(SpotLinkDesign.Spacing.md)
                .spotlinkCard()

                ReservationTrustCard(
                    reservation: context.reservation,
                    location: context.resolvedContext.location,
                    resource: context.resolvedContext.resource
                )

                NavigationLink {
                    ReservationDetailView(
                        reservationId: context.reservation.id,
                        reservationService: appContainer.reservationService,
                        locationService: appContainer.locationService,
                        supportService: appContainer.supportService,
                        resolvedContext: context.resolvedContext,
                        reservation: context.reservation
                    )
                } label: {
                    Text("Prikazi detalje rezervacije")
                }
                .buttonStyle(SpotLinkButtonStyle(.primary))

                Button {
                    showSupportComposer = true
                } label: {
                    Label("Problem na ulazu?", systemImage: "questionmark.circle")
                }
                .buttonStyle(SpotLinkButtonStyle(.ghost))
            }
            .padding(SpotLinkDesign.Spacing.md)
        }
        .navigationTitle("Potvrda")
        .spotlinkInlineNavigationTitle()
        .sheet(isPresented: $showSupportComposer) {
            NavigationStack {
                SupportTicketComposerView(
                    service: appContainer.supportService,
                    defaultCategory: .locationAccess,
                    defaultSubject: "Problem na ulazu za rezervaciju \(context.reservation.bookingCodePlaceholder)",
                    initialBody: "Na ulazu u lokaciju \(context.resolvedContext.location.name) imam problem sa pristupom. Booking code: \(context.reservation.bookingCodePlaceholder).",
                    reservationId: context.reservation.id,
                    locationId: context.reservation.locationId
                )
            }
        }
    }
}