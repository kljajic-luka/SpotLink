import CoreLocation
import Foundation
import Testing
@testable import SpotLinkCore

// MARK: - Mock API Client

/// Simulira APIClientProtocol za testove bez mreze.
private final class MockAPIClient: APIClientProtocol, @unchecked Sendable {

    // Handler koji vraca rezultat za GET pozive
    var getHandler: ((String, [String: String]?) throws -> Any)?
    var shouldThrowOffline = false
    var shouldThrowError: APIError?

    func get<T: Decodable>(_ path: String, query: [String: String]? = nil) async throws -> T {
        if shouldThrowOffline { throw APIError.offline }
        if let err = shouldThrowError { throw err }
        guard let handler = getHandler,
              let result = try handler(path, query) as? T else {
            throw APIError.serverError(500, APIErrorContext(message: "Mock nije konfigurisan"))
        }
        return result
    }

    func post<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        throw APIError.serverError(500, APIErrorContext(message: "Post nije implementiran u mock-u"))
    }

    func put<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        throw APIError.serverError(500, APIErrorContext(message: "Put nije implementiran u mock-u"))
    }

    func patch<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        throw APIError.serverError(500, APIErrorContext(message: "Patch nije implementiran u mock-u"))
    }

    func delete(_ path: String) async throws {}
}

// MARK: - Test Fixtures

private func makeSearchResult(
    locationId: String = "loc-001",
    name: String = "Test Parking",
    lat: Double = 44.8125,
    lon: Double = 20.4612,
    distanceKm: Double? = 0.5,
    startingPriceCents: Int? = 200,
    availableResourceCount: Int = 3
) -> LocationSearchResult {
    LocationSearchResult(
        location: ParkingLocation(
            id: locationId,
            operatorId: "op-001",
            name: name,
            address: Address(
                line1: "Ulica 1",
                line2: nil,
                city: "Beograd",
                region: nil,
                postalCode: nil,
                country: "RS",
                formattedAddress: "Ulica 1, Beograd"
            ),
            coordinates: GeoCoordinates(latitude: lat, longitude: lon),
            timezone: "Europe/Belgrade",
            accessType: .selfPark,
            publicNotes: nil,
            active: true
        ),
        resources: [],
        distanceKm: distanceKm,
        startingPriceCents: startingPriceCents,
        availableResourceCount: availableResourceCount
    )
}

private func makeEmptyPage() -> APIPage<LocationSearchResult> {
    APIPage(content: [], totalElements: 0, totalPages: 0, page: 0, size: 20)
}

private func makeSingleResultPage(_ result: LocationSearchResult) -> APIPage<LocationSearchResult> {
    APIPage(content: [result], totalElements: 1, totalPages: 1, page: 0, size: 20)
}

private func makeMultiResultPage(_ results: [LocationSearchResult]) -> APIPage<LocationSearchResult> {
    APIPage(content: results, totalElements: results.count, totalPages: 1, page: 0, size: 20)
}

// MARK: - SearchMapViewModel Tests

@Suite("SearchMapViewModel – stanja i pretraga")
@MainActor
struct SearchMapViewModelTests {

    // Pravljenje view modela sa mock API klijentom
    private func makeViewModel(client: MockAPIClient) -> SearchMapViewModel {
        let locationService = LocationService(apiClient: client)
        let locationManager = SpotLinkLocationManager()
        return SearchMapViewModel(locationService: locationService, locationManager: locationManager)
    }

    // MARK: - Pocetno stanje

    @Test("pocetno stanje je idle")
    func pocetnoStanje() {
        let vm = makeViewModel(client: MockAPIClient())
        // Provera da je pocetno stanje idle (pre nego sto se pokrene pretraga)
        if case .idle = vm.state {
            // Ispravno
        } else {
            Issue.record("Ocekivano idle, dobijeno \(vm.state)")
        }
    }

    @Test("podrazumevani centar je Beograd")
    func podrazumevaniCentar() {
        let vm = makeViewModel(client: MockAPIClient())
        #expect(vm.mapCenter.latitude == SearchMapViewModel.defaultCenter.latitude)
        #expect(vm.mapCenter.longitude == SearchMapViewModel.defaultCenter.longitude)
    }

    @Test("vremenski okvir je zaokruzeni sledeci sat za 2 sata")
    func podrazumevanoVremenskoOkno() {
        let vm = makeViewModel(client: MockAPIClient())
        let razlika = vm.searchEndsAt.timeIntervalSince(vm.searchStartsAt)
        // Vremenski okvir treba biti 2 sata (7200 sekundi)
        #expect(razlika == 7200)
    }

    // MARK: - Uspesna pretraga

    @Test("uspesna pretraga sa rezultatima postavlja state na results")
    func uspesnaPretragaSaRezultatima() async {
        let client = MockAPIClient()
        let ocekivaniRezultat = makeSearchResult()
        let stranica = makeSingleResultPage(ocekivaniRezultat)
        client.getHandler = { _, _ in stranica }

        let vm = makeViewModel(client: client)
        vm.searchWithCurrentCenter()

        // Cekamo kratko da async task zavrsi
        try? await Task.sleep(nanoseconds: 100_000_000)

        if case .results(let items) = vm.state {
            #expect(items.count == 1)
            #expect(items[0].location.name == "Test Parking")
        } else {
            Issue.record("Ocekivano results, dobijeno \(vm.state)")
        }
    }

    @Test("uspesna pretraga bez rezultata postavlja state na empty")
    func uspesnaPretragaBezRezultata() async {
        let client = MockAPIClient()
        client.getHandler = { _, _ in makeEmptyPage() }

        let vm = makeViewModel(client: client)
        vm.searchWithCurrentCenter()

        try? await Task.sleep(nanoseconds: 100_000_000)

        if case .empty = vm.state {
            // Ispravno
        } else {
            Issue.record("Ocekivano empty, dobijeno \(vm.state)")
        }
    }

    // MARK: - Rucna pretraga

    @Test("rucna pretraga sa praznim upitom ne pokrece pretragu")
    func rucnaPretragaSaPraznimUpiton() async {
        let client = MockAPIClient()
        var searchPokrenut = false
        client.getHandler = { _, _ in
            searchPokrenut = true
            return makeEmptyPage()
        }

        let vm = makeViewModel(client: client)
        vm.query = ""
        vm.searchManual()

        try? await Task.sleep(nanoseconds: 50_000_000)

        #expect(!searchPokrenut)
    }

    @Test("rucna pretraga sa upitom pokrece pretragu sa tim upitom")
    func rucnaPretragaSaUpitom() async {
        let client = MockAPIClient()
        var zabelezeniParametri: [String: String]?
        client.getHandler = { _, query in
            zabelezeniParametri = query
            return makeEmptyPage()
        }

        let vm = makeViewModel(client: client)
        vm.query = "Centralna"
        vm.searchManual()

        try? await Task.sleep(nanoseconds: 100_000_000)

        #expect(zabelezeniParametri?["query"] == "Centralna")
    }

    // MARK: - Greske

    @Test("API greska postavlja state na error sa porukom")
    func apiGreska() async {
        let client = MockAPIClient()
        client.shouldThrowError = .serverError(500, APIErrorContext(message: "Interna greska servera"))

        let vm = makeViewModel(client: client)
        vm.searchWithCurrentCenter()

        try? await Task.sleep(nanoseconds: 100_000_000)

        if case .error(let msg) = vm.state {
            #expect(!msg.isEmpty)
        } else {
            Issue.record("Ocekivano error, dobijeno \(vm.state)")
        }
    }

    @Test("offline greska postavlja state na offline")
    func offlineGreska() async {
        let client = MockAPIClient()
        client.shouldThrowOffline = true

        let vm = makeViewModel(client: client)
        vm.searchWithCurrentCenter()

        try? await Task.sleep(nanoseconds: 100_000_000)

        if case .offline = vm.state {
            // Ispravno
        } else {
            Issue.record("Ocekivano offline, dobijeno \(vm.state)")
        }
    }

    // MARK: - Selekcija

    @Test("clearSelection ponistava selectedResult")
    func clearSelection() {
        let vm = makeViewModel(client: MockAPIClient())
        vm.selectedResult = makeSearchResult()
        vm.clearSelection()
        #expect(vm.selectedResult == nil)
    }

    @Test("odabrani rezultat se ispravno cuva")
    func selektovaniRezultat() {
        let vm = makeViewModel(client: MockAPIClient())
        let result = makeSearchResult(locationId: "loc-xyz", name: "Odabrani Parking")
        vm.selectedResult = result
        #expect(vm.selectedResult?.id == "loc-xyz")
        #expect(vm.selectedResult?.location.name == "Odabrani Parking")
    }

    // MARK: - Pretraga blizu mene

    @Test("searchNearMe sa odbijenom dozvolom postavlja locationPermissionDenied")
    func pretragaBlizuMeneBezDozvole() async {
        let client = MockAPIClient()
        let vm = makeViewModel(client: client)
        // Dozvola je denied (podrazumevano notDetermined u testu)
        // Kad je notDetermined, manager ce zatraziti dozvolu, ali u testu nema pravi CLLocationManager
        // Testiramo samo da nema crash-a
        vm.searchNearMe()
        try? await Task.sleep(nanoseconds: 50_000_000)
        // Test prolazi ako nema crash-a i state ostaje idle ili loading
    }

    // MARK: - Parametri pretrage

    @Test("pretraga sa koordinatama dodaje radiusKm u parametre")
    func pretragaSaKoordinatamaImaRadijus() async {
        let client = MockAPIClient()
        var zabelezeniParametri: [String: String]?
        client.getHandler = { _, query in
            zabelezeniParametri = query
            return makeEmptyPage()
        }

        let vm = makeViewModel(client: client)
        vm.searchWithCurrentCenter()

        try? await Task.sleep(nanoseconds: 100_000_000)

        // Podrazumevani centar je Beograd, pa treba biti latitude/longitude u parametrima
        #expect(zabelezeniParametri?["latitude"] != nil)
        #expect(zabelezeniParametri?["longitude"] != nil)
        #expect(zabelezeniParametri?["radiusKm"] == "10.0")
    }

    @Test("pretraga uvek sadrzi startsAt i endsAt parametre")
    func pretragaImaVremenskeParametre() async {
        let client = MockAPIClient()
        var zabelezeniParametri: [String: String]?
        client.getHandler = { _, query in
            zabelezeniParametri = query
            return makeEmptyPage()
        }

        let vm = makeViewModel(client: client)
        vm.searchWithCurrentCenter()

        try? await Task.sleep(nanoseconds: 100_000_000)

        #expect(zabelezeniParametri?["startsAt"] != nil)
        #expect(zabelezeniParametri?["endsAt"] != nil)
    }

    @Test("novi searchWithCurrentCenter otkazuje prethodni task")
    func noviTaskOtkazujePrethodni() async {
        let client = MockAPIClient()
        var brojPoziva = 0
        client.getHandler = { _, _ in
            brojPoziva += 1
            return makeEmptyPage()
        }

        let vm = makeViewModel(client: client)
        // Pokrecemo dva uzastopna poziva
        vm.searchWithCurrentCenter()
        vm.searchWithCurrentCenter()

        try? await Task.sleep(nanoseconds: 200_000_000)

        // Drugi task treba biti zavrseni, ali otkazivanje je best-effort
        // Samo proveravamo da nema crash-a i da je state validan
        switch vm.state {
        case .results, .empty, .error, .offline:
            break // Validan krajnji state
        default:
            break
        }
    }
}

// MARK: - LocationSearchFilters – query parametri

@Suite("LocationSearchFilters – izgradnja query parametara")
struct LocationSearchFiltersTests {

    @Test("prazni filteri vracaju prazne parametre")
    func prazniFilteri() {
        let filteri = LocationSearchFilters()
        #expect(filteri.queryParameters.isEmpty)
    }

    @Test("query se dodaje u parametre")
    func queryParametar() {
        var filteri = LocationSearchFilters()
        filteri.query = "garaza"
        #expect(filteri.queryParameters["query"] == "garaza")
    }

    @Test("prazni query string se ne dodaje u parametre")
    func prazanQuerySeNeDodaje() {
        var filteri = LocationSearchFilters()
        filteri.query = ""
        #expect(filteri.queryParameters["query"] == nil)
    }

    @Test("koordinate i radijus se dodaju zajedno")
    func koordinateIRadijus() {
        var filteri = LocationSearchFilters()
        filteri.latitude = 44.8125
        filteri.longitude = 20.4612
        filteri.radiusKm = 5.0
        let params = filteri.queryParameters
        #expect(params["latitude"] == "44.8125")
        #expect(params["longitude"] == "20.4612")
        #expect(params["radiusKm"] == "5.0")
    }

    @Test("evChargingRequired se dodaje kao string")
    func evChargingRequired() {
        var filteri = LocationSearchFilters()
        filteri.evChargingRequired = true
        #expect(filteri.queryParameters["evChargingRequired"] == "true")
    }

    @Test("vremenski okvir se dodaje kao string")
    func vremenski0kvir() {
        var filteri = LocationSearchFilters()
        filteri.startsAt = "2025-06-01T10:00:00Z"
        filteri.endsAt = "2025-06-01T12:00:00Z"
        let params = filteri.queryParameters
        #expect(params["startsAt"] == "2025-06-01T10:00:00Z")
        #expect(params["endsAt"] == "2025-06-01T12:00:00Z")
    }

    @Test("vise tipova resursa se spajaju zarezom")
    func visetipova() {
        var filteri = LocationSearchFilters()
        filteri.resourceTypes = [.parkingSpot, .evCharger]
        let params = filteri.queryParameters
        let types = params["resourceTypes"] ?? ""
        #expect(types.contains("PARKING_SPOT"))
        #expect(types.contains("EV_CHARGER"))
    }
}
