import SwiftUI

public struct ReservationConfirmationContext {
    public let reservation: Reservation
    public let quote: ReservationQuote
    public let paymentIntent: PaymentIntent?
    public let paymentResult: PaymentProviderResult?
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
    @Published public var selectedPaymentMode: PaymentMode = .payOnArrival
    @Published public var promoCode: String = ""

    @Published public private(set) var vehicles: [VehicleProfile] = []
    @Published public private(set) var paymentMethods: [PaymentMethod] = []
    @Published public private(set) var quote: ReservationQuote?
    @Published public private(set) var confirmationContext: ReservationConfirmationContext?
    @Published public private(set) var pendingOnlineReservation: Reservation?
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
        self.selectedPaymentMode = Self.preferredPaymentMode(for: result.resources.first)
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

    public var availablePaymentModes: [PaymentMode] {
        selectedResource?.availablePaymentModes ?? [.online]
    }

    public var paymentCapabilityText: String {
        if availablePaymentModes.count == 1, let mode = availablePaymentModes.first {
            return "Ovaj resurs podrzava samo: \(mode.displayName)."
        }
        return "Ovaj resurs podrzava: \(availablePaymentModes.map(\.displayName).joined(separator: ", "))."
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
                reconcileSelectedPaymentMode()
            }

            vehicles = try await vehicleService.listMyVehicles()
            await loadPaymentMethods(paymentService: paymentService)

            if requiresVehicleSelection && selectedVehicleId == nil {
                selectedVehicleId = vehicles.first?.id
            }

            await refreshQuote(service: reservationService)
        } catch let error as APIError {
            setAPIError(error, operation: "reservation_booking_load")
        } catch {
            errorMessage = "Priprema rezervacije trenutno nije uspela."
        }
    }

    public func invalidateQuote() {
        quote = nil
        errorMessage = nil
        pendingOnlineReservation = nil
    }

    public func paymentModeChanged() {
        errorMessage = nil
        pendingOnlineReservation = nil
        reconcileSelectedPaymentMode()
        if selectedPaymentMode.requiresOnlinePayment && selectedPaymentMethodId == nil {
            selectedPaymentMethodId = paymentMethods.first(where: { $0.isDefault })?.id ?? paymentMethods.first?.id
        }
    }

    public func resourceSelectionChanged() {
        reconcileSelectedPaymentMode()
        invalidateQuote()
    }

    public func clearConfirmation() {
        confirmationContext = nil
    }

    public func didCreateVehicle(_ vehicle: VehicleProfile) {
        vehicles.removeAll { $0.id == vehicle.id }
        vehicles.insert(vehicle, at: 0)
        selectedVehicleId = vehicle.id
        invalidateQuote()
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
            setAPIError(error, operation: "reservation_quote")
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

        if selectedPaymentMode.requiresOnlinePayment {
            guard selectedPaymentMethodId != nil else {
                errorMessage = "Izaberite nacin placanja."
                return
            }
        }

        guard availablePaymentModes.contains(selectedPaymentMode) else {
            errorMessage = "Izabrani nacin placanja nije dostupan za ovo mesto."
            return
        }

        if quote == nil {
            await refreshQuote(service: reservationService)
        }

        guard let quote else { return }

        isSubmitting = true
        errorMessage = nil
        pendingOnlineReservation = nil
        defer { isSubmitting = false }

        do {
            let paymentMethodId = selectedPaymentMode.requiresOnlinePayment ? selectedPaymentMethodId : nil
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
                    paymentMode: selectedPaymentMode,
                    idempotencyKey: reservationKey
                )
            )

            if reservation.status != .pendingPayment {
                let latestReservation = (try? await reservationService.getReservation(reservation.id)) ?? reservation
                completeReservation(
                    latestReservation,
                    quote: quote,
                    paymentIntent: nil,
                    paymentResult: nil,
                    paymentMethod: nil,
                    selectedResource: selectedResource
                )
                return
            }

            guard let paymentMethodId else {
                errorMessage = "Izaberite nacin placanja."
                return
            }

            pendingOnlineReservation = reservation

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
            completeReservation(
                confirmedReservation,
                quote: quote,
                paymentIntent: intent,
                paymentResult: paymentResult,
                paymentMethod: selectedPaymentMethod,
                selectedResource: selectedResource
            )
        } catch let error as APIError {
            setAPIError(error, operation: "reservation_submit")
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
            selectedPaymentMode.rawValue,
            promoCode.nilIfBlank ?? "bez-promo"
        ].joined(separator: "|")
    }

    public var primaryActionTitle: String {
        if selectedResource?.confirmationMode == .manual {
            return "Posalji zahtev za rezervaciju"
        }
        return selectedPaymentMode == .payOnArrival
            ? "Rezervisi uz placanje na dolasku"
            : "Rezervisi i potvrdi online placanje"
    }

    private func loadPaymentMethods(paymentService: PaymentService) async {
        do {
            paymentMethods = try await paymentService.listPaymentMethods()
            selectedPaymentMethodId = paymentMethods.first(where: { $0.isDefault })?.id ?? paymentMethods.first?.id
        } catch let error as APIError {
            paymentMethods = []
            selectedPaymentMethodId = nil
            SpotLinkLogger.warn("reservation_payment_methods_load_failed code=\(error.code ?? "-") requestId=\(error.requestId ?? "-")")
            if selectedPaymentMode.requiresOnlinePayment {
                errorMessage = error.userFacingMessageWithReference
            }
        } catch {
            paymentMethods = []
            selectedPaymentMethodId = nil
            if selectedPaymentMode.requiresOnlinePayment {
                errorMessage = "Nacini placanja trenutno nisu dostupni."
            }
        }
    }

    private func completeReservation(
        _ reservation: Reservation,
        quote: ReservationQuote,
        paymentIntent: PaymentIntent?,
        paymentResult: PaymentProviderResult?,
        paymentMethod: PaymentMethod?,
        selectedResource: ParkingResource
    ) {
        let resolvedContext = ReservationResolvedContext(
            location: result.location,
            resource: selectedResource,
            vehicle: selectedVehicle
        )

        confirmationContext = ReservationConfirmationContext(
            reservation: reservation,
            quote: quote,
            paymentIntent: paymentIntent,
            paymentResult: paymentResult,
            paymentMethod: paymentMethod,
            resolvedContext: resolvedContext
        )
        pendingOnlineReservation = nil
        pendingOperations.resetAll()
    }

    private func setAPIError(_ error: APIError, operation: String) {
        SpotLinkLogger.warn("reservation_operation_failed operation=\(operation) code=\(error.code ?? "-") requestId=\(error.requestId ?? "-")")
        errorMessage = error.userFacingMessageWithReference
    }

    private func reconcileSelectedPaymentMode() {
        guard !availablePaymentModes.contains(selectedPaymentMode) else {
            return
        }
        selectedPaymentMode = Self.preferredPaymentMode(for: selectedResource)
    }

    private static func preferredPaymentMode(for resource: ParkingResource?) -> PaymentMode {
        let modes = resource?.availablePaymentModes ?? [.online]
        if modes.contains(.payOnArrival) {
            return .payOnArrival
        }
        return modes.first ?? .online
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

                if let reservation = viewModel.pendingOnlineReservation {
                    OnlinePaymentHoldNotice(reservation: reservation)
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
                    viewModel.resourceSelectionChanged()
                }

                if let resource = viewModel.selectedResource {
                    ReservationFactGrid(items: [
                        ReservationFact(title: "Tip", value: resource.type.displayName, icon: resource.type.systemIcon),
                        ReservationFact(title: "Potvrda", value: resource.confirmationMode.displayName, icon: "checkmark.seal"),
                        ReservationFact(title: "Kapacitet", value: resource.capacitySummary, icon: "car.2"),
                        ReservationFact(title: "Pristup", value: viewModel.result.location.accessType.displayName, icon: viewModel.result.location.accessType.systemIcon),
                        ReservationFact(title: "Placanje", value: resource.availablePaymentModes.map(\.displayName).joined(separator: ", "), icon: "wallet.pass")
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

            Picker("Rezim placanja", selection: $viewModel.selectedPaymentMode) {
                ForEach(viewModel.availablePaymentModes, id: \.self) { mode in
                    Text(mode.displayName)
                        .tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .accessibilityLabel("Rezim placanja")
            .onChange(of: viewModel.selectedPaymentMode) { _, _ in
                viewModel.paymentModeChanged()
            }

            ReservationInstructionBlock(
                title: viewModel.selectedPaymentMode.displayName,
                message: viewModel.selectedPaymentMode.detailText
            )

            ReservationInstructionBlock(
                title: "Dostupnost placanja",
                message: viewModel.paymentCapabilityText
            )

            if viewModel.selectedPaymentMode.requiresOnlinePayment {
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
                        Text("Izabrana metoda: \(method.formattedDescription).")
                            .font(SpotLinkDesign.Typography.footnote)
                            .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    }
                }
            } else {
                ReservationFactGrid(items: [
                    ReservationFact(title: "Valuta", value: viewModel.quote?.currency ?? "RSD", icon: "banknote"),
                    ReservationFact(title: "Potvrda", value: viewModel.selectedResource?.confirmationMode.displayName ?? "Partner potvrda", icon: "checkmark.seal")
                ])
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
                    Text("Ponuda vazi do \(formatReservationDateTime(expiresAt, timezone: viewModel.result.location.timezone)).")
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

            LoadingButton(viewModel.primaryActionTitle, isLoading: viewModel.isSubmitting) {
                await viewModel.submitBooking(
                    reservationService: appContainer.reservationService,
                    paymentService: appContainer.paymentService
                )
            }
        }
    }
}

struct OnlinePaymentHoldNotice: View {
    let reservation: Reservation

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs) {
            Label("Online placanje je zapoceto", systemImage: "timer")
                .font(SpotLinkDesign.Typography.headline)
            Text(holdText)
                .font(SpotLinkDesign.Typography.callout)
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                .fixedSize(horizontal: false, vertical: true)
            if let holdId = reservation.holdId {
                Text("Hold: \(holdId)")
                    .font(SpotLinkDesign.Typography.caption)
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    .textSelection(.enabled)
            }
        }
        .padding(SpotLinkDesign.Spacing.md)
        .background(SpotLinkDesign.Colors.warning.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm))
        .accessibilityElement(children: .combine)
    }

    private var holdText: String {
        if let expiresAt = reservation.holdExpiresAt {
            return "Mesto je zadrzano do \(formatReservationDateTime(expiresAt, timezone: reservation.timezone)). Dovrsite online placanje pre isteka ili napravite novu rezervaciju."
        }
        return "Mesto je zadrzano dok se ne zavrsi provera placanja."
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

    private var isPendingOperatorConfirmation: Bool {
        context.reservation.status == .pendingOperatorConfirmation
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.lg) {
                VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
                    Label(confirmationTitle, systemImage: confirmationIcon)
                        .font(SpotLinkDesign.Typography.title2.weight(.bold))
                        .foregroundStyle(confirmationColor)
                    Text(confirmationMessage)
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

                ReservationPaymentStateCard(
                    reservation: context.reservation,
                    quoteTotal: context.quote.totalAmountFormatted,
                    paymentIntent: context.paymentIntent,
                    paymentResult: context.paymentResult,
                    paymentMethod: context.paymentMethod
                )

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
        .navigationTitle(isPendingOperatorConfirmation ? "Cekanje potvrde" : "Potvrda")
        .spotlinkInlineNavigationTitle()
        .sheet(isPresented: $showSupportComposer) {
            NavigationStack {
                SupportTicketComposerView(
                    service: appContainer.supportService,
                    defaultCategory: .locationAccess,
                    defaultSubject: "Problem na ulazu za rezervaciju \(context.reservation.displayBookingCode)",
                    initialBody: "Na ulazu u lokaciju \(context.resolvedContext.location.name) imam problem sa pristupom. Booking code: \(context.reservation.supportBookingCodeText).",
                    reservationId: context.reservation.id,
                    locationId: context.reservation.locationId
                )
            }
        }
    }

    private var confirmationTitle: String {
        isPendingOperatorConfirmation ? "Zahtev je poslat" : "Rezervacija je spremna"
    }

    private var confirmationIcon: String {
        isPendingOperatorConfirmation ? "hourglass.circle.fill" : "checkmark.circle.fill"
    }

    private var confirmationColor: Color {
        isPendingOperatorConfirmation ? SpotLinkDesign.Colors.warning : SpotLinkDesign.Colors.success
    }

    private var confirmationMessage: String {
        if isPendingOperatorConfirmation {
            return "Partner lokacija pregleda zahtev. Booking code je vec dodeljen i podrska moze da prati status dok cekate potvrdu ili odbijanje."
        }
        return "Partner lokacija i booking code su sacuvani za dolazak. Ako na ulazu nesto krene po zlu, podrska vidi isti booking code i rezervaciju."
    }
}
