import Foundation
import Testing
@testable import SpotLinkCore

private final class VehicleFlowMockAPIClient: APIClientProtocol, @unchecked Sendable {
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

private func makeCreatedVehicle(id: String = "veh-001", licensePlate: String = "BG-123-AA") -> VehicleProfile {
    VehicleProfile(
        id: id,
        userId: "user-001",
        type: .car,
        nickname: "Porodicni auto",
        make: "Skoda",
        model: "Octavia",
        color: "Siva",
        licensePlate: licensePlate,
        heightMeters: 1.48,
        lengthMeters: 4.69,
        evCapable: false,
        verificationStatus: "PENDING",
        createdAt: "2026-04-26T09:00:00Z"
    )
}

private func makeVehicleRequiredResult() -> LocationSearchResult {
    LocationSearchResult(
        location: ParkingLocation(
            id: "loc-001",
            operatorId: "op-001",
            name: "Partner Garaza Centar",
            address: Address(
                line1: "Kralja Petra 10",
                line2: nil,
                city: "Beograd",
                region: nil,
                postalCode: "11000",
                country: "RS",
                formattedAddress: "Kralja Petra 10, Beograd 11000"
            ),
            coordinates: GeoCoordinates(latitude: 44.8170, longitude: 20.4570),
            timezone: "Europe/Belgrade",
            accessType: .gateCode,
            publicNotes: nil,
            active: true
        ),
        resources: [
            ParkingResource(
                id: "res-001",
                locationId: "loc-001",
                type: .garage,
                label: "Nivo -1 / mesto 08",
                floor: "-1",
                bayNumber: "08",
                fitRule: VehicleFitRule(maxHeightMeters: 2.0, maxLengthMeters: 5.0, allowedVehicleTypes: [.car], evOnly: false),
                hourlyRateCents: 250,
                dailyRateCents: 1800,
                currency: "RSD",
                instantReserve: true,
                active: true,
                capacity: 2,
                confirmationMode: .manual,
                payOnArrivalEnabled: true,
                supportedPaymentModes: [.payOnArrival]
            )
        ],
        distanceKm: 0.7,
        startingPriceCents: 250,
        availableResourceCount: 2
    )
}

private func makeVehicleQuote(start: Date, end: Date) -> ReservationQuote {
    ReservationQuote(
        resourceId: "res-001",
        startsAt: start,
        endsAt: end,
        subtotalCents: 500,
        feesCents: 50,
        discountCents: 0,
        totalAmountCents: 550,
        currency: "RSD",
        expiresAt: start.addingTimeInterval(900)
    )
}

private func makePayOnArrivalReservation(vehicleId: String) -> Reservation {
    Reservation(
        id: "resv-veh-001",
        customerId: "user-001",
        operatorId: "op-001",
        locationId: "loc-001",
        resourceId: "res-001",
        inventoryPoolId: "pool-001",
        holdId: nil,
        vehicleId: vehicleId,
        startsAt: Date(timeIntervalSince1970: 1_713_868_800),
        endsAt: Date(timeIntervalSince1970: 1_713_876_000),
        timezone: "Europe/Belgrade",
        bookingCode: "SL-VEH001",
        status: .confirmed,
        paymentMode: .payOnArrival,
        totalAmountCents: 550,
        currency: "RSD",
        accessInstructionsVisible: true,
        paymentExpiresAt: nil,
        createdAt: Date(timeIntervalSince1970: 1_713_868_000),
        updatedAt: Date(timeIntervalSince1970: 1_713_868_100)
    )
}

@Suite("Vehicle create flow")
@MainActor
struct VehicleCreationFlowTests {

    @Test("add vehicle submit normalizuje payload i vraca kreirano vozilo")
    func addVehicleSubmitNormalizesPayload() async throws {
        let client = VehicleFlowMockAPIClient()
        let service = VehicleService(apiClient: client)
        let viewModel = AddVehicleViewModel(service: service)

        viewModel.type = .van
        viewModel.licensePlate = "  bg 123 aa  "
        viewModel.nickname = "  Porodicni kombi  "
        viewModel.make = "  Ford  "
        viewModel.model = "  Transit Custom  "
        viewModel.color = "  Tamno siva  "
        viewModel.heightMeters = "2,15"
        viewModel.lengthMeters = "4.97"
        viewModel.evCapable = true

        var capturedRequest: VehicleUpsertRequest?

        client.postHandler = { path, body in
            #expect(path == "/vehicles")

            guard let request = body as? VehicleUpsertRequest else {
                throw APIError.decodingFailed("Ocekivan VehicleUpsertRequest")
            }

            capturedRequest = request
            return makeCreatedVehicle()
        }

        let createdVehicle = try await viewModel.submit()

        #expect(createdVehicle.id == "veh-001")
        #expect(capturedRequest?.type == .van)
        #expect(capturedRequest?.licensePlate == "BG 123 AA")
        #expect(capturedRequest?.nickname == "Porodicni kombi")
        #expect(capturedRequest?.make == "Ford")
        #expect(capturedRequest?.model == "Transit Custom")
        #expect(capturedRequest?.color == "Tamno siva")
        #expect(capturedRequest?.heightMeters == 2.15)
        #expect(capturedRequest?.lengthMeters == 4.97)
        #expect(capturedRequest?.evCapable == true)
        #expect(viewModel.errorMessage == nil)
        #expect(viewModel.isSubmitting == false)
    }

    @Test("add vehicle validation blokira prazan plate")
    func addVehicleValidationRejectsEmptyPlate() async {
        let client = VehicleFlowMockAPIClient()
        let service = VehicleService(apiClient: client)
        let viewModel = AddVehicleViewModel(service: service)

        viewModel.licensePlate = "   "
        var postCalls = 0

        client.postHandler = { _, _ in
            postCalls += 1
            return makeCreatedVehicle()
        }

        do {
            _ = try await viewModel.submit()
            Issue.record("Ocekivana je validation greska za praznu registraciju")
        } catch let error as APIError {
            if case .validation = error {
                // Ocekivano.
            } else {
                Issue.record("Ocekivan je APIError.validation, dobijeno: \(error)")
            }
        } catch {
            Issue.record("Ocekivan je APIError.validation, dobijeno: \(error)")
        }

        #expect(postCalls == 0)
        #expect(viewModel.errorMessage == "Unesite vazecu registraciju.")
        #expect(viewModel.isSubmitting == false)
    }

    @Test("add vehicle backend greska ostaje vidljiva korisniku")
    func addVehicleSubmitShowsBackendError() async {
        let client = VehicleFlowMockAPIClient()
        let service = VehicleService(apiClient: client)
        let viewModel = AddVehicleViewModel(service: service)

        viewModel.licensePlate = "BG-555-ZZ"

        client.postHandler = { _, _ in
            throw APIError.conflict(APIErrorContext(
                message: "Vozilo sa ovom registracijom vec postoji.",
                requestId: "req-vehicle-409"
            ))
        }

        do {
            _ = try await viewModel.submit()
            Issue.record("Ocekivana je backend conflict greska")
        } catch let error as APIError {
            if case .conflict = error {
                // Ocekivano.
            } else {
                Issue.record("Ocekivan je APIError.conflict, dobijeno: \(error)")
            }
        } catch {
            Issue.record("Ocekivan je APIError.conflict, dobijeno: \(error)")
        }

        #expect(viewModel.errorMessage == "Vozilo sa ovom registracijom vec postoji.\nRef: req-vehicle-409")
        #expect(viewModel.isSubmitting == false)
    }

    @Test("reservation flow se oporavlja kada je vozilo obavezno a nalog je prazan")
    func reservationFlowRecoversAfterAddingVehicleToEmptyAccount() async {
        let client = VehicleFlowMockAPIClient()
        let result = makeVehicleRequiredResult()
        let reservationService = ReservationService(apiClient: client)
        let locationService = LocationService(apiClient: client)
        let vehicleService = VehicleService(apiClient: client)
        let paymentService = PaymentService(apiClient: client)
        let createdVehicle = makeCreatedVehicle(id: "veh-new-001", licensePlate: "NS-777-BB")
        let reservation = makePayOnArrivalReservation(vehicleId: createdVehicle.id)

        var capturedReservedVehicleId: String?

        client.getHandler = { path, _ in
            switch path {
            case "/vehicles/me":
                return [VehicleProfile]()
            case "/payments/capabilities":
                return PaymentCapabilities(
                    onlinePaymentsEnabled: true,
                    activeProvider: "MOCK",
                    mockProvider: true,
                    mockPaymentMethodsAllowed: true,
                    operations: PaymentOperationCapabilities(
                        authorize: true,
                        capture: true,
                        cancel: true,
                        refund: true,
                        webhook: false,
                        reconciliation: false
                    )
                )
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
                return makeVehicleQuote(
                    start: Date(timeIntervalSince1970: 1_713_868_800),
                    end: Date(timeIntervalSince1970: 1_713_876_000)
                )
            case "/reservations":
                guard let request = body as? CreateReservationRequest else {
                    throw APIError.decodingFailed("Ocekivan CreateReservationRequest")
                }
                capturedReservedVehicleId = request.vehicleId
                return reservation
            default:
                throw APIError.notFound(APIErrorContext(message: "Nepoznat POST put: \(path)"))
            }
        }

        let viewModel = ReservationBookingViewModel(
            result: result,
            initialStartsAt: Date(timeIntervalSince1970: 1_713_868_800),
            initialEndsAt: Date(timeIntervalSince1970: 1_713_876_000)
        )

        await viewModel.loadIfNeeded(
            reservationService: reservationService,
            locationService: locationService,
            vehicleService: vehicleService,
            paymentService: paymentService
        )

        #expect(viewModel.requiresVehicleSelection == true)
        #expect(viewModel.vehicles.isEmpty)
        #expect(viewModel.selectedVehicleId == nil)

        viewModel.didCreateVehicle(createdVehicle)
        await viewModel.submitBooking(
            reservationService: reservationService,
            paymentService: paymentService
        )

        #expect(viewModel.selectedVehicleId == createdVehicle.id)
        #expect(capturedReservedVehicleId == createdVehicle.id)
        #expect(viewModel.confirmationContext?.reservation.vehicleId == createdVehicle.id)
        #expect(viewModel.errorMessage == nil)
    }
}
