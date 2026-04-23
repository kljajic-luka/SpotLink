import Foundation

// MARK: - Vehicle Models

public struct VehicleProfile: Decodable, Identifiable, Sendable {
    public let id: String
    public let userId: String
    public let type: VehicleType
    public let nickname: String?
    public let make: String?
    public let model: String?
    public let color: String?
    public let licensePlate: String?
    public let heightMeters: Double?
    public let lengthMeters: Double?
    public let evCapable: Bool
    public let verificationStatus: String
    public let createdAt: String

    public var displayName: String {
        if let nickname, !nickname.isEmpty { return nickname }
        let parts = [make, model].compactMap { $0 }.joined(separator: " ")
        return parts.isEmpty ? type.displayName : parts
    }
}

public enum VehicleType: String, Codable, CaseIterable, Sendable {
    case car         = "CAR"
    case motorcycle  = "MOTORCYCLE"
    case van         = "VAN"
    case truck       = "TRUCK"
    case bicycle     = "BICYCLE"
    case other       = "OTHER"

    public var displayName: String {
        switch self {
        case .car:        return "Automobil"
        case .motorcycle: return "Motocikl"
        case .van:        return "Kombi"
        case .truck:      return "Kamion"
        case .bicycle:    return "Bicikl"
        case .other:      return "Ostalo"
        }
    }

    public var systemIcon: String {
        switch self {
        case .car:        return "car.fill"
        case .motorcycle: return "bicycle"
        case .van:        return "bus.fill"
        case .truck:      return "truck.box.fill"
        case .bicycle:    return "bicycle.circle.fill"
        case .other:      return "car.circle"
        }
    }
}

public struct VehicleUpsertRequest: Encodable, Sendable {
    public let type: VehicleType
    public let nickname: String?
    public let make: String?
    public let model: String?
    public let color: String?
    public let licensePlate: String?
    public let heightMeters: Double?
    public let lengthMeters: Double?
    public let evCapable: Bool

    public init(
        type: VehicleType,
        nickname: String? = nil,
        make: String? = nil,
        model: String? = nil,
        color: String? = nil,
        licensePlate: String? = nil,
        heightMeters: Double? = nil,
        lengthMeters: Double? = nil,
        evCapable: Bool = false
    ) {
        self.type = type
        self.nickname = nickname
        self.make = make
        self.model = model
        self.color = color
        self.licensePlate = licensePlate
        self.heightMeters = heightMeters
        self.lengthMeters = lengthMeters
        self.evCapable = evCapable
    }
}

public struct VehicleFitRule: Decodable, Sendable {
    public let maxHeightMeters: Double?
    public let maxLengthMeters: Double?
    public let allowedVehicleTypes: [VehicleType]?
    public let evOnly: Bool?
}
