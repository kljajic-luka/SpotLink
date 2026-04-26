import Foundation
import Testing
@testable import SpotLinkCore

private final class BookingMockAPIClient: APIClientProtocol, @unchecked Sendable {
    var getHandler: ((String, [String: String]?) throws -> Any)?
    var postHandler: ((String, Any) throws -> Any)?

    func get<T: Decodable>(_ path: String, query: [String: String]? = nil) async throws -> T {
        guard let getHandler,
              let result = try getHandler(path, query) as? T else {
            throw APIError.serverError(500, APIErrorContext(message: "GET mock nije konfigurisan za \(path)"))
        }
        return result
    }

    func post<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        guard let postHandler,
              let result = try postHandler(path, body) as? T else {
            throw APIError.serverError(500, APIErrorContext(message: "POST mock nije konfigurisan za \(path)"))
        }
        return result
    }

    func put<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        throw APIError.serverError(500, APIErrorContext(message: "PUT nije potreban u ovom testu"))
    }

    func patch<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        throw APIError.serverError(500, APIErrorContext(message: "PATCH nije potreban u ovom testu"))
    }

    func delete(_ path: String) async throws {
        throw APIError.serverError(500, APIErrorContext(message: "DELETE nije potreban u ovom testu"))
    }
}

private func makeBookingLocationResult(
    supportedPaymentModes: [PaymentMode] = [.online, .payOnArrival]
) -> LocationSearchResult {
    LocationSearchResult(
        location: ParkingLocation(
            id: "loc-001",
            operatorId: "op-001",
            name: "Partner Garaza Dorcol",
            address: Address(
                line1: "Cara Dusana 10",
                line2: nil,
                city: "Beograd",
                region: nil,
                postalCode: "11000",
                country: "RS",
                formattedAddress: "Cara Dusana 10, Beograd 11000"
            ),
            coordinates: GeoCoordinates(latitude: 44.8230, longitude: 20.4660),
            timezone: "Europe/Belgrade",
            accessType: .gateCode,
            publicNotes: "Ulaz kroz rampu pored portira.",
            active: true
        ),
        resources: [
            ParkingResource(
                id: "res-001",
                locationId: "loc-001",
                type: .garage,
                label: "Nivo -1 / mesto 12",
                floor: "-1",
                bayNumber: "12",
                fitRule: VehicleFitRule(maxHeightMeters: 2.0, maxLengthMeters: 5.0, allowedVehicleTypes: [.car], evOnly: false),
                hourlyRateCents: 240,
                dailyRateCents: 1800,
                currency: "RSD",
                instantReserve: true,
                active: true,
                capacity: 3,
                confirmationMode: .manual,
                payOnArrivalEnabled: supportedPaymentModes.contains(.payOnArrival),
                supportedPaymentModes: supportedPaymentModes
            )
        ],
        distanceKm: 0.9,
        startingPriceCents: 240,
        availableResourceCount: 3
    )
}

private func makeVehicle() -> VehicleProfile {
    VehicleProfile(
        id: "veh-001",
        userId: "user-001",
        type: .car,
        nickname: "Porodicni auto",
        make: "Skoda",
        model: "Octavia",
        color: "Siva",
        licensePlate: "BG-123-AA",
        heightMeters: 1.5,
        lengthMeters: 4.7,
        evCapable: false,
        verificationStatus: "VERIFIED",
        createdAt: "2026-04-23T10:00:00Z"
    )
}

private func makePaymentMethod() -> PaymentMethod {
    PaymentMethod(
        id: "pm_card_visa",
        type: nil,
        displayName: nil,
        last4: "4242",
        expiryMonth: 12,
        expiryYear: 2032,
        brand: "Visa",
        isDefault: true
    )
}

private func makeQuote(start: Date, end: Date) -> ReservationQuote {
    ReservationQuote(
        resourceId: "res-001",
        startsAt: start,
        endsAt: end,
        subtotalCents: 480,
        feesCents: 50,
        discountCents: 0,
        totalAmountCents: 530,
        currency: "RSD",
        expiresAt: start.addingTimeInterval(900)
    )
}

private func makeReservation(
    status: ReservationStatus,
    paymentMode: PaymentMode = .online
) -> Reservation {
    Reservation(
        id: "resv-001",
        customerId: "user-001",
        operatorId: "op-001",
        locationId: "loc-001",
        resourceId: "res-001",
        inventoryPoolId: "pool-001",
        holdId: "hold-001",
        vehicleId: "veh-001",
        startsAt: Date(timeIntervalSince1970: 1_713_868_800),
        endsAt: Date(timeIntervalSince1970: 1_713_876_000),
        timezone: "Europe/Belgrade",
        status: status,
        paymentMode: paymentMode,
        totalAmountCents: 530,
        currency: "RSD",
        accessInstructionsVisible: status == .confirmed,
        paymentExpiresAt: paymentMode == .online ? Date(timeIntervalSince1970: 1_713_869_700) : nil,
        createdAt: Date(timeIntervalSince1970: 1_713_868_000),
        updatedAt: Date(timeIntervalSince1970: 1_713_868_100)
    )
}

@Suite("Reservation flow – DTO and idempotency")
@MainActor
struct ReservationFlowTests {

    @Test("reservation quote dekodira backend polja bez quoteId i durationHours")
    func reservationQuoteDecodesBackendShape() throws {
        let json = """
        {
          "resourceId": "res-001",
          "startsAt": "2026-04-23T12:00:00Z",
          "endsAt": "2026-04-23T14:00:00Z",
          "subtotalCents": 480,
          "feesCents": 50,
          "discountCents": 0,
          "totalAmountCents": 530,
          "currency": "RSD",
          "expiresAt": "2026-04-23T12:15:00Z"
        }
        """

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let quote = try decoder.decode(ReservationQuote.self, from: Data(json.utf8))

        #expect(quote.resourceId == "res-001")
        #expect(quote.subtotalCents == 480)
        #expect(quote.feesCents == 50)
        #expect(quote.discountCents == 0)
        #expect(quote.totalAmountCents == 530)
    }

    @Test("reservation dekodira hardened booking i payment polja")
    func reservationDecodesHardenedBackendShape() throws {
        let json = """
        {
          "id": "resv-001",
          "customerId": "user-001",
          "operatorId": "op-001",
          "locationId": "loc-001",
          "resourceId": "res-001",
          "inventoryPoolId": "pool-001",
          "holdId": "hold-001",
          "vehicleId": "veh-001",
          "startsAt": "2026-04-23T12:00:00Z",
          "endsAt": "2026-04-23T14:00:00Z",
          "timezone": "Europe/Belgrade",
          "status": "NO_SHOW",
          "paymentMode": "PAY_ON_ARRIVAL",
          "totalAmountCents": 530,
          "currency": "RSD",
          "accessInstructionsVisible": false,
          "paymentExpiresAt": null,
          "createdAt": "2026-04-23T10:00:00Z",
          "updatedAt": "2026-04-23T10:05:00Z"
        }
        """

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let reservation = try decoder.decode(Reservation.self, from: Data(json.utf8))

        #expect(reservation.status == .noShow)
        #expect(reservation.paymentMode == .payOnArrival)
        #expect(reservation.inventoryPoolId == "pool-001")
        #expect(reservation.holdId == "hold-001")
        #expect(reservation.holdExpiresAt == nil)
        #expect(reservation.currency == "RSD")
    }

    @Test("create reservation request cuva eksplicitni idempotency key")
    func createReservationRequestUsesProvidedIdempotencyKey() throws {
        let start = Date(timeIntervalSince1970: 1_713_868_800)
        let end = Date(timeIntervalSince1970: 1_713_876_000)
        let request = CreateReservationRequest(
            resourceId: "res-001",
            vehicleId: "veh-001",
            startsAt: start,
            endsAt: end,
            promoCode: nil,
            quoteId: nil,
            paymentMethodId: "pm_card_visa",
            paymentMode: .online,
            idempotencyKey: "res:test-key"
        )

        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        let data = try encoder.encode(request)
        let object = try JSONSerialization.jsonObject(with: data) as? [String: Any]

        #expect(object?["idempotencyKey"] as? String == "res:test-key")
        #expect(object?["paymentMode"] as? String == "ONLINE")
        #expect(object?["startsAt"] as? String == start.iso8601String)
        #expect(object?["endsAt"] as? String == end.iso8601String)
    }

    @Test("pending operation store vraca isti kljuc za isti logicki zahtev")
    func pendingOperationStoreReusesKey() {
        let store = PendingOperationStore()
        let first = store.key(for: "reservation|res-001|slot-a", prefix: "res")
        let second = store.key(for: "reservation|res-001|slot-a", prefix: "res")
        let third = store.key(for: "reservation|res-001|slot-b", prefix: "res")

        #expect(first == second)
        #expect(first != third)

        store.reset(operation: "reservation|res-001|slot-a")
        let regenerated = store.key(for: "reservation|res-001|slot-a", prefix: "res")
        #expect(regenerated != first)
    }

    @Test("pay on arrival flow salje eksplicitni mode i ne poziva online payment")
    func payOnArrivalFlowSkipsOnlinePayment() async {
        let client = BookingMockAPIClient()
        let result = makeBookingLocationResult()
        let vehicle = makeVehicle()
        let reservation = makeReservation(status: .confirmed, paymentMode: .payOnArrival)

        let reservationService = ReservationService(apiClient: client)
        let locationService = LocationService(apiClient: client)
        let vehicleService = VehicleService(apiClient: client)
        let paymentService = PaymentService(apiClient: client)

        var paymentCalls = 0
        var capturedPaymentMode: PaymentMode?
        var capturedPaymentMethodId: String?

        client.getHandler = { path, _ in
            switch path {
            case "/vehicles/me":
                return [vehicle]
            case "/payments/methods":
                return [PaymentMethod]()
            case "/reservations/\(reservation.id)":
                return reservation
            default:
                throw APIError.notFound(APIErrorContext(message: "Nepoznat GET put: \(path)"))
            }
        }

        client.postHandler = { path, body in
            switch path {
            case "/reservations/quote":
                return makeQuote(start: reservation.startsAt, end: reservation.endsAt)
            case "/reservations":
                guard let request = body as? CreateReservationRequest else {
                    throw APIError.decodingFailed("Ocekivan CreateReservationRequest")
                }
                capturedPaymentMode = request.paymentMode
                capturedPaymentMethodId = request.paymentMethodId
                return reservation
            case "/payments/intents", "/payments/intents/pi-001/confirm":
                paymentCalls += 1
                throw APIError.serverError(500, APIErrorContext(message: "Online payment ne sme biti pozvan"))
            default:
                throw APIError.notFound(APIErrorContext(message: "Nepoznat POST put: \(path)"))
            }
        }

        let viewModel = ReservationBookingViewModel(
            result: result,
            initialStartsAt: reservation.startsAt,
            initialEndsAt: reservation.endsAt
        )

        await viewModel.loadIfNeeded(
            reservationService: reservationService,
            locationService: locationService,
            vehicleService: vehicleService,
            paymentService: paymentService
        )
        await viewModel.submitBooking(reservationService: reservationService, paymentService: paymentService)

        #expect(capturedPaymentMode == .payOnArrival)
        #expect(capturedPaymentMethodId == nil)
        #expect(paymentCalls == 0)
        #expect(viewModel.confirmationContext?.reservation.paymentMode == .payOnArrival)
        #expect(viewModel.confirmationContext?.reservation.status == .confirmed)
        #expect(viewModel.confirmationContext?.paymentIntent == nil)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("booking flow ogranicava placanje na mode koji resurs podrzava")
    @MainActor
    func bookingFlowRestrictsPaymentModesToSelectedResourceCapabilities() {
        let result = makeBookingLocationResult(supportedPaymentModes: [.online])
        let reservation = makeReservation(status: .pendingPayment, paymentMode: .online)
        let viewModel = ReservationBookingViewModel(
            result: result,
            initialStartsAt: reservation.startsAt,
            initialEndsAt: reservation.endsAt
        )

        #expect(viewModel.availablePaymentModes == [.online])
        #expect(viewModel.selectedPaymentMode == .online)

        viewModel.selectedPaymentMode = .payOnArrival
        viewModel.paymentModeChanged()

        #expect(viewModel.selectedPaymentMode == .online)
    }

    @Test("booking flow zadrzava isti reservation idempotency key kroz retry")
    func bookingFlowKeepsReservationIdempotencyKeyAcrossRetry() async {
        let client = BookingMockAPIClient()
        let result = makeBookingLocationResult()
        let vehicle = makeVehicle()
        let paymentMethod = makePaymentMethod()
        let reservation = makeReservation(status: .pendingPayment)
        let confirmedReservation = makeReservation(status: .confirmed)
        let paymentIntent = PaymentIntent(
            id: "pi-001",
            reservationId: reservation.id,
            customerId: nil,
            amountCents: reservation.totalAmountCents,
            currency: reservation.currency,
            status: .authorized,
            redirectUrl: nil,
            clientSecret: "sl_pi_secret",
            providerReference: nil,
            createdAt: nil,
            updatedAt: nil
        )
        let paymentResult = PaymentProviderResult(
            status: .authorized,
            paymentIntentId: paymentIntent.id,
            redirectUrl: nil,
            message: "Authorized"
        )

        let reservationService = ReservationService(apiClient: client)
        let locationService = LocationService(apiClient: client)
        let vehicleService = VehicleService(apiClient: client)
        let paymentService = PaymentService(apiClient: client)

        var reservationKeys: [String] = []
        var reservationAttempts = 0

        client.getHandler = { path, _ in
            switch path {
            case "/vehicles/me":
                return [vehicle]
            case "/payments/methods":
                return [paymentMethod]
            case "/reservations/\(reservation.id)":
                return confirmedReservation
            default:
                throw APIError.notFound(APIErrorContext(message: "Nepoznat GET put: \(path)"))
            }
        }

        client.postHandler = { path, body in
            switch path {
            case "/reservations/quote":
                return makeQuote(start: reservation.startsAt, end: reservation.endsAt)
            case "/reservations":
                guard let request = body as? CreateReservationRequest else {
                    throw APIError.decodingFailed("Ocekivan CreateReservationRequest")
                }
                reservationKeys.append(request.idempotencyKey)
                reservationAttempts += 1
                if reservationAttempts == 1 {
                    throw APIError.offline
                }
                return reservation
            case "/payments/intents":
                return paymentIntent
            case "/payments/intents/\(paymentIntent.id)/confirm":
                return paymentResult
            default:
                throw APIError.notFound(APIErrorContext(message: "Nepoznat POST put: \(path)"))
            }
        }

        let viewModel = ReservationBookingViewModel(
            result: result,
            initialStartsAt: reservation.startsAt,
            initialEndsAt: reservation.endsAt
        )

        await viewModel.loadIfNeeded(
            reservationService: reservationService,
            locationService: locationService,
            vehicleService: vehicleService,
            paymentService: paymentService
        )
        viewModel.selectedPaymentMode = .online
        viewModel.paymentModeChanged()

        #expect(viewModel.quote?.totalAmountCents == 530)
        #expect(viewModel.selectedPaymentMethodId == paymentMethod.id)
        #expect(viewModel.selectedVehicleId == vehicle.id)

        await viewModel.submitBooking(reservationService: reservationService, paymentService: paymentService)
        #expect(reservationKeys.count == 1)
        #expect(viewModel.confirmationContext == nil)

        await viewModel.submitBooking(reservationService: reservationService, paymentService: paymentService)

        #expect(reservationKeys.count == 2)
        #expect(reservationKeys[0] == reservationKeys[1])
        #expect(viewModel.confirmationContext?.reservation.status == .confirmed)
        #expect(viewModel.confirmationContext?.paymentIntent?.id == paymentIntent.id)
    }

    @Test("booking flow ne potvrdi rezervaciju kada intent zahteva dodatnu akciju")
    func bookingFlowStopsWhenIntentRequiresAction() async {
        let client = BookingMockAPIClient()
        let result = makeBookingLocationResult()
        let vehicle = makeVehicle()
        let paymentMethod = makePaymentMethod()
        let reservation = makeReservation(status: .pendingPayment)
        let paymentIntent = PaymentIntent(
            id: "pi-action",
            reservationId: reservation.id,
            customerId: nil,
            amountCents: reservation.totalAmountCents,
            currency: reservation.currency,
            status: .requiresAction,
            redirectUrl: "https://payments.spotlink.test/3ds",
            clientSecret: "sl_pi_secret_action",
            providerReference: nil,
            createdAt: nil,
            updatedAt: nil
        )

        let reservationService = ReservationService(apiClient: client)
        let locationService = LocationService(apiClient: client)
        let vehicleService = VehicleService(apiClient: client)
        let paymentService = PaymentService(apiClient: client)

        var confirmCalls = 0

        client.getHandler = { path, _ in
            switch path {
            case "/vehicles/me":
                return [vehicle]
            case "/payments/methods":
                return [paymentMethod]
            default:
                throw APIError.notFound(APIErrorContext(message: "Nepoznat GET put: \(path)"))
            }
        }

        client.postHandler = { path, _ in
            switch path {
            case "/reservations/quote":
                return makeQuote(start: reservation.startsAt, end: reservation.endsAt)
            case "/reservations":
                return reservation
            case "/payments/intents":
                return paymentIntent
            case "/payments/intents/\(paymentIntent.id)/confirm":
                confirmCalls += 1
                return PaymentProviderResult(
                    status: .authorized,
                    paymentIntentId: paymentIntent.id,
                    redirectUrl: nil,
                    message: nil
                )
            default:
                throw APIError.notFound(APIErrorContext(message: "Nepoznat POST put: \(path)"))
            }
        }

        let viewModel = ReservationBookingViewModel(
            result: result,
            initialStartsAt: reservation.startsAt,
            initialEndsAt: reservation.endsAt
        )

        await viewModel.loadIfNeeded(
            reservationService: reservationService,
            locationService: locationService,
            vehicleService: vehicleService,
            paymentService: paymentService
        )
        viewModel.selectedPaymentMode = .online
        viewModel.paymentModeChanged()
        await viewModel.submitBooking(reservationService: reservationService, paymentService: paymentService)

        #expect(confirmCalls == 0)
        #expect(viewModel.confirmationContext == nil)
        #expect(viewModel.pendingOnlineReservation?.holdExpiresAt == reservation.holdExpiresAt)
        #expect(viewModel.errorMessage == "Placanje zahteva dodatnu potvrdu kod provajdera pre zavrsetka rezervacije.")
    }

    @Test("booking flow ne prikazuje potvrdu kada provider vrati requires action")
    func bookingFlowWaitsForAuthorizedOrCapturedProviderResult() async {
        let client = BookingMockAPIClient()
        let result = makeBookingLocationResult()
        let vehicle = makeVehicle()
        let paymentMethod = makePaymentMethod()
        let reservation = makeReservation(status: .pendingPayment)
        let paymentIntent = PaymentIntent(
            id: "pi-provider-action",
            reservationId: reservation.id,
            customerId: nil,
            amountCents: reservation.totalAmountCents,
            currency: reservation.currency,
            status: .authorized,
            redirectUrl: nil,
            clientSecret: "sl_pi_secret_authorized",
            providerReference: nil,
            createdAt: nil,
            updatedAt: nil
        )

        let reservationService = ReservationService(apiClient: client)
        let locationService = LocationService(apiClient: client)
        let vehicleService = VehicleService(apiClient: client)
        let paymentService = PaymentService(apiClient: client)

        var confirmCalls = 0

        client.getHandler = { path, _ in
            switch path {
            case "/vehicles/me":
                return [vehicle]
            case "/payments/methods":
                return [paymentMethod]
            default:
                throw APIError.notFound(APIErrorContext(message: "Nepoznat GET put: \(path)"))
            }
        }

        client.postHandler = { path, _ in
            switch path {
            case "/reservations/quote":
                return makeQuote(start: reservation.startsAt, end: reservation.endsAt)
            case "/reservations":
                return reservation
            case "/payments/intents":
                return paymentIntent
            case "/payments/intents/\(paymentIntent.id)/confirm":
                confirmCalls += 1
                return PaymentProviderResult(
                    status: .requiresAction,
                    paymentIntentId: paymentIntent.id,
                    redirectUrl: "https://payments.spotlink.test/challenge",
                    message: "3DS challenge required"
                )
            default:
                throw APIError.notFound(APIErrorContext(message: "Nepoznat POST put: \(path)"))
            }
        }

        let viewModel = ReservationBookingViewModel(
            result: result,
            initialStartsAt: reservation.startsAt,
            initialEndsAt: reservation.endsAt
        )

        await viewModel.loadIfNeeded(
            reservationService: reservationService,
            locationService: locationService,
            vehicleService: vehicleService,
            paymentService: paymentService
        )
        viewModel.selectedPaymentMode = .online
        viewModel.paymentModeChanged()
        await viewModel.submitBooking(reservationService: reservationService, paymentService: paymentService)

        #expect(confirmCalls == 1)
        #expect(viewModel.confirmationContext == nil)
        #expect(viewModel.pendingOnlineReservation?.holdId == reservation.holdId)
        #expect(viewModel.errorMessage == "Placanje zahteva dodatnu potvrdu kod provajdera pre zavrsetka rezervacije.")
    }
}
