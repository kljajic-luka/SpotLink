import Foundation
import Testing
@testable import SpotLinkCore

// MARK: - LocationSearchResult – JSON dekodiranje

@Suite("LocationSearchResult – JSON fixture dekodiranje")
struct LocationSearchResultDecodingTests {

    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        d.keyDecodingStrategy = .useDefaultKeys
        return d
    }()

    // MARK: - Fixture: jedan rezultat

    private let singleResultJSON = """
    {
      "location": {
        "id": "loc-001",
        "operatorId": "op-001",
        "name": "Centralna Garaza Beograd",
        "address": {
          "line1": "Knez Mihailova 1",
          "line2": null,
          "city": "Beograd",
          "region": null,
          "postalCode": "11000",
          "country": "RS",
          "formattedAddress": "Knez Mihailova 1, Beograd 11000"
        },
        "coordinates": {
          "latitude": 44.8125,
          "longitude": 20.4612
        },
        "timezone": "Europe/Belgrade",
        "accessType": "SELF_PARK",
        "publicNotes": null,
        "active": true
      },
      "resources": [
        {
          "id": "res-001",
          "locationId": "loc-001",
          "type": "PARKING_SPOT",
          "label": "Mesto A1",
          "floor": "P1",
          "bayNumber": "A1",
          "fitRule": null,
          "hourlyRateCents": 300,
          "dailyRateCents": null,
          "currency": "USD",
          "instantReserve": true,
          "active": true,
          "capacity": 1,
          "confirmationMode": "INSTANT"
        },
        {
          "id": "res-002",
          "locationId": "loc-001",
          "type": "EV_CHARGER",
          "label": "EV Punjac B2",
          "floor": null,
          "bayNumber": "B2",
          "fitRule": null,
          "hourlyRateCents": 500,
          "dailyRateCents": 3500,
          "currency": "USD",
          "instantReserve": false,
          "active": true,
          "capacity": 3,
          "confirmationMode": "MANUAL"
        }
      ],
      "distanceKm": 0.42,
      "startingPriceCents": 300,
      "availableResourceCount": 4
    }
    """

    @Test("lokacija se ispravno dekodira iz JSON-a")
    func dekodiranjeOsnovnihPolja() throws {
        let result = try decoder.decode(LocationSearchResult.self, from: Data(singleResultJSON.utf8))
        #expect(result.location.id == "loc-001")
        #expect(result.location.name == "Centralna Garaza Beograd")
        #expect(result.location.address.city == "Beograd")
        #expect(result.location.coordinates.latitude == 44.8125)
        #expect(result.location.coordinates.longitude == 20.4612)
        #expect(result.location.accessType == .selfPark)
    }

    @Test("resursi se ispravno dekodiraju")
    func dekodiranjeResursa() throws {
        let result = try decoder.decode(LocationSearchResult.self, from: Data(singleResultJSON.utf8))
        #expect(result.resources.count == 2)
        let prvi = result.resources[0]
        #expect(prvi.id == "res-001")
        #expect(prvi.type == .parkingSpot)
        #expect(prvi.hourlyRateCents == 300)
        #expect(prvi.capacity == 1)
        #expect(prvi.confirmationMode == .instant)
    }

    @Test("EV resurs se ispravno dekodira sa confirmationMode MANUAL")
    func dekodiranjeEVResursa() throws {
        let result = try decoder.decode(LocationSearchResult.self, from: Data(singleResultJSON.utf8))
        let ev = result.resources[1]
        #expect(ev.type == .evCharger)
        #expect(ev.capacity == 3)
        #expect(ev.confirmationMode == .manual)
        #expect(ev.dailyRateCents == 3500)
    }

    @Test("distanceKm se ispravno dekodira")
    func dekodiranjeUdaljenosti() throws {
        let result = try decoder.decode(LocationSearchResult.self, from: Data(singleResultJSON.utf8))
        #expect(result.distanceKm == 0.42)
    }

    @Test("startingPriceCents se ispravno dekodira")
    func dekodiranjePocetneCene() throws {
        let result = try decoder.decode(LocationSearchResult.self, from: Data(singleResultJSON.utf8))
        #expect(result.startingPriceCents == 300)
    }

    @Test("availableResourceCount se ispravno dekodira")
    func dekodiranjeKapaciteta() throws {
        let result = try decoder.decode(LocationSearchResult.self, from: Data(singleResultJSON.utf8))
        #expect(result.availableResourceCount == 4)
    }

    @Test("id computed property vraca location.id")
    func idComputedProperty() throws {
        let result = try decoder.decode(LocationSearchResult.self, from: Data(singleResultJSON.utf8))
        #expect(result.id == result.location.id)
        #expect(result.id == "loc-001")
    }

    @Test("formattedDistance ispravno formatira metres za udaljenosti ispod 1km")
    func formattedDistanceMetri() throws {
        let result = try decoder.decode(LocationSearchResult.self, from: Data(singleResultJSON.utf8))
        // 0.42 km = 420m
        #expect(result.formattedDistance == "420m")
    }

    // MARK: - Fixture: udaljenost >= 1km

    private let resultDaleko = """
    {
      "location": {
        "id": "loc-002",
        "operatorId": "op-001",
        "name": "Daleki Parking",
        "address": {
          "line1": "Ulica 1",
          "city": "Beograd",
          "country": "RS"
        },
        "coordinates": { "latitude": 44.80, "longitude": 20.45 },
        "timezone": "Europe/Belgrade",
        "accessType": "VALET",
        "active": true
      },
      "resources": [],
      "distanceKm": 2.5,
      "startingPriceCents": null,
      "availableResourceCount": 0
    }
    """

    @Test("formattedDistance ispravno formatira kilometre za udaljenosti >= 1km")
    func formattedDistanceKm() throws {
        let result = try decoder.decode(LocationSearchResult.self, from: Data(resultDaleko.utf8))
        #expect(result.formattedDistance == "2.5 km")
    }

    @Test("startingPriceCents moze biti null")
    func startingPriceCentsNull() throws {
        let result = try decoder.decode(LocationSearchResult.self, from: Data(resultDaleko.utf8))
        #expect(result.startingPriceCents == nil)
    }

    @Test("formattedStartingPrice vraca nil kada cena ili resursi nisu dostupni")
    func formattedStartingPriceNilBezResursa() throws {
        let result = try decoder.decode(LocationSearchResult.self, from: Data(resultDaleko.utf8))
        #expect(result.formattedStartingPrice == nil)
    }

    // MARK: - Fixture: APIPage sa vise rezultata

    private let pageJSON = """
    {
      "content": [
        {
          "location": {
            "id": "loc-001",
            "operatorId": "op-001",
            "name": "Parking Jedan",
            "address": { "line1": "Ulica 1", "city": "Beograd", "country": "RS" },
            "coordinates": { "latitude": 44.81, "longitude": 20.46 },
            "timezone": "Europe/Belgrade",
            "accessType": "SELF_PARK",
            "active": true
          },
          "resources": [],
          "distanceKm": 0.5,
          "startingPriceCents": 200,
          "availableResourceCount": 2
        },
        {
          "location": {
            "id": "loc-002",
            "operatorId": "op-001",
            "name": "Parking Dva",
            "address": { "line1": "Ulica 2", "city": "Beograd", "country": "RS" },
            "coordinates": { "latitude": 44.82, "longitude": 20.47 },
            "timezone": "Europe/Belgrade",
            "accessType": "GATE_CODE",
            "active": true
          },
          "resources": [],
          "distanceKm": 1.2,
          "startingPriceCents": 400,
          "availableResourceCount": 5
        }
      ],
      "totalElements": 2,
      "totalPages": 1,
      "page": 0,
      "size": 20
    }
    """

    @Test("APIPage se ispravno dekodira sa vise rezultata")
    func dekodiranjeAPIPage() throws {
        let page = try decoder.decode(APIPage<LocationSearchResult>.self, from: Data(pageJSON.utf8))
        #expect(page.content.count == 2)
        #expect(page.totalElements == 2)
        #expect(page.totalPages == 1)
        #expect(page.page == 0)
        #expect(page.size == 20)
    }

    @Test("APIPage sadrzaj je ispravno uredjen")
    func dekodiranjeAPIPageSadrzaj() throws {
        let page = try decoder.decode(APIPage<LocationSearchResult>.self, from: Data(pageJSON.utf8))
        #expect(page.content[0].location.name == "Parking Jedan")
        #expect(page.content[1].location.name == "Parking Dva")
        #expect(page.content[0].distanceKm == 0.5)
        #expect(page.content[1].availableResourceCount == 5)
    }
}

// MARK: - ConfirmationMode – dekodiranje

@Suite("ConfirmationMode – raw value mapiranje")
struct ConfirmationModeTests {

    @Test("INSTANT se dekodira ispravno")
    func instantDekodiranje() throws {
        let data = Data("\"INSTANT\"".utf8)
        let mode = try JSONDecoder().decode(ConfirmationMode.self, from: data)
        #expect(mode == .instant)
    }

    @Test("MANUAL se dekodira ispravno")
    func manualDekodiranje() throws {
        let data = Data("\"MANUAL\"".utf8)
        let mode = try JSONDecoder().decode(ConfirmationMode.self, from: data)
        #expect(mode == .manual)
    }
}

@Suite("GeocodeSuggestion – backend shape")
struct GeocodeSuggestionTests {

    @Test("geocode suggestion dekodira ugnjezdenu adresu i koordinate")
    func geocodeSuggestionDecode() throws {
        let json = """
        {
          "id": "mock-belgrade-1",
          "address": {
            "line1": "Knez Mihailova 1",
            "line2": null,
            "city": "Beograd",
            "region": null,
            "postalCode": "11000",
            "country": "RS",
            "formattedAddress": "Knez Mihailova 1, Beograd 11000"
          },
          "coordinates": {
            "latitude": 44.8125,
            "longitude": 20.4612
          },
          "accuracyMeters": 150
        }
        """

        let suggestion = try JSONDecoder().decode(GeocodeSuggestion.self, from: Data(json.utf8))

        #expect(suggestion.id == "mock-belgrade-1")
        #expect(suggestion.displayName == "Knez Mihailova 1, Beograd 11000")
        #expect(suggestion.latitude == 44.8125)
        #expect(suggestion.longitude == 20.4612)
        #expect(suggestion.accuracyMeters == 150)
    }
}
