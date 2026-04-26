import Foundation

// MARK: - Address

public struct Address: Decodable, Sendable {
    public let line1: String
    public let line2: String?
    public let city: String
    public let region: String?
    public let postalCode: String?
    public let country: String
    public let formattedAddress: String?

    public var displayAddress: String {
        formattedAddress ?? [line1, city, region, country]
            .compactMap { $0 }
            .joined(separator: ", ")
    }
}

// MARK: - Coordinates

public struct GeoCoordinates: Decodable, Equatable, Sendable {
    public let latitude: Double
    public let longitude: Double
}

// MARK: - Parking Location

public struct ParkingLocation: Decodable, Identifiable, Sendable {
    public let id: String
    public let operatorId: String
    public let name: String
    public let address: Address
    public let coordinates: GeoCoordinates
    public let timezone: String
    public let accessType: ParkingAccessType
    public let publicNotes: String?
    public let active: Bool
}

public enum ParkingAccessType: String, Decodable, CaseIterable, Sendable {
    case selfPark   = "SELF_PARK"
    case valet      = "VALET"
    case gateCode   = "GATE_CODE"
    case appUnlock  = "APP_UNLOCK"
    case attendant  = "ATTENDANT"

    public var displayName: String {
        switch self {
        case .selfPark:  return "Samo-parkiranje"
        case .valet:     return "Valet"
        case .gateCode:  return "Kod kapije"
        case .appUnlock: return "Otkljucavanje aplikacijom"
        case .attendant: return "Praceno"
        }
    }

    public var systemIcon: String {
        switch self {
        case .selfPark:  return "parkingsign"
        case .valet:     return "person.fill"
        case .gateCode:  return "lock.open.fill"
        case .appUnlock: return "iphone"
        case .attendant: return "person.badge.key.fill"
        }
    }
}

public enum ConfirmationMode: String, Decodable, CaseIterable, Sendable {
    case instant = "INSTANT"
    case manual  = "MANUAL"

    public var displayName: String {
        switch self {
        case .instant: return "Instant potvrda"
        case .manual:  return "Rucna potvrda"
        }
    }
}

// MARK: - Parking Resource

public struct ParkingResource: Decodable, Identifiable, Sendable {
    public let id: String
    public let locationId: String
    public let type: ParkingResourceType
    public let label: String
    public let floor: String?
    public let bayNumber: String?
    public let fitRule: VehicleFitRule?
    public let hourlyRateCents: Int
    public let dailyRateCents: Int?
    public let currency: String
    public let instantReserve: Bool
    public let active: Bool
    public let capacity: Int
    public let confirmationMode: ConfirmationMode

    public var hourlyRateFormatted: String {
        formatCents(hourlyRateCents, currency: currency) + "/h"
    }

    public var dailyRateFormatted: String? {
        guard let daily = dailyRateCents else { return nil }
        return formatCents(daily, currency: currency) + "/dan"
    }

    public var capacitySummary: String {
        if capacity == 1 {
            return "1 garantovano mesto"
        }
        return "\(capacity) mesta na raspolaganju"
    }
}

func formatCents(_ cents: Int, currency: String) -> String {
    let amount = Double(cents) / 100.0
    let formatter = NumberFormatter()
    formatter.numberStyle = .currency
    formatter.currencyCode = currency
    formatter.locale = Locale(identifier: "sr_RS")
    return formatter.string(from: NSNumber(value: amount)) ?? "\(currency) \(amount)"
}

public enum ParkingResourceType: String, Decodable, CaseIterable, Sendable {
    case parkingSpot = "PARKING_SPOT"
    case garage      = "GARAGE"
    case driveway    = "DRIVEWAY"
    case evCharger   = "EV_CHARGER"
    case lot         = "LOT"

    public var displayName: String {
        switch self {
        case .parkingSpot: return "Parking mesto"
        case .garage:      return "Garaza"
        case .driveway:    return "Prilaz"
        case .evCharger:   return "EV punjac"
        case .lot:         return "Parking"
        }
    }

    public var systemIcon: String {
        switch self {
        case .parkingSpot: return "parkingsign.circle"
        case .garage:      return "building.fill"
        case .driveway:    return "road.lanes"
        case .evCharger:   return "bolt.car.fill"
        case .lot:         return "square.grid.3x3.fill"
        }
    }
}

// MARK: - Search

public struct LocationSearchFilters: Sendable {
    public var query: String?
    public var latitude: Double?
    public var longitude: Double?
    public var radiusKm: Double?
    public var resourceTypes: [ParkingResourceType]?
    public var evChargingRequired: Bool?
    public var startsAt: String?
    public var endsAt: String?
    public var page: Int?
    public var size: Int?

    public init() {}

    public var queryParameters: [String: String] {
        var params: [String: String] = [:]
        if let q = query, !q.isEmpty { params["query"] = q }
        if let lat = latitude { params["latitude"] = String(lat) }
        if let lon = longitude { params["longitude"] = String(lon) }
        if let r = radiusKm { params["radiusKm"] = String(r) }
        if let types = resourceTypes, !types.isEmpty {
            params["resourceTypes"] = types.map(\.rawValue).joined(separator: ",")
        }
        if let ev = evChargingRequired { params["evChargingRequired"] = String(ev) }
        if let starts = startsAt { params["startsAt"] = starts }
        if let ends = endsAt { params["endsAt"] = ends }
        if let p = page { params["page"] = String(p) }
        if let s = size { params["size"] = String(s) }
        return params
    }
}

// MARK: - Search Result (ugnjezdena struktura ista kao backend LocationSearchResult)

public struct LocationSearchResult: Decodable, Identifiable, Sendable {
    public var id: String { location.id }
    public let location: ParkingLocation
    public let resources: [ParkingResource]
    public let distanceKm: Double?
    public let startingPriceCents: Int?
    public let availableResourceCount: Int

    public var formattedDistance: String? {
        guard let d = distanceKm else { return nil }
        if d < 1 { return "\(Int(d * 1000))m" }
        return String(format: "%.1f km", d)
    }

    public var formattedStartingPrice: String? {
        guard let price = startingPriceCents,
              let currency = resources.first?.currency else { return nil }
        return formatCents(price, currency: currency) + "/h"
    }
}

public struct GeocodeSuggestion: Decodable, Identifiable, Sendable {
    public let id: String
    public let address: Address
    public let coordinates: GeoCoordinates
    public let accuracyMeters: Int?

    public var displayName: String { address.displayAddress }
    public var latitude: Double { coordinates.latitude }
    public var longitude: Double { coordinates.longitude }
    public var placeId: String? { id }
}
