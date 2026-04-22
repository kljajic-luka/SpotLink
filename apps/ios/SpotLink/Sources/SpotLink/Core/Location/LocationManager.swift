import CoreLocation
import Foundation

// MARK: - Location Permission Status

public enum LocationPermissionStatus: Sendable {
    case notDetermined
    case denied
    case restricted
    case authorizedAlways
    case authorizedWhenInUse

    public var isAuthorized: Bool {
        self == .authorizedAlways || self == .authorizedWhenInUse
    }
}

// MARK: - Location Manager

/// Wrapper oko CoreLocation koji publish-uje promene korisnikove lokacije.
/// Thread-safe putem MainActor.
@MainActor
public final class SpotLinkLocationManager: NSObject, ObservableObject {

    public static let shared = SpotLinkLocationManager()

    @Published public private(set) var permissionStatus: LocationPermissionStatus = .notDetermined
    @Published public private(set) var currentLocation: CLLocation?
    @Published public private(set) var isUpdating: Bool = false

    private let manager: CLLocationManager

    public override init() {
        manager = CLLocationManager()
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    // MARK: - Public API

    public func requestPermission() {
        manager.requestWhenInUseAuthorization()
    }

    public func startUpdatingLocation() {
        guard permissionStatus.isAuthorized else {
            requestPermission()
            return
        }
        isUpdating = true
        manager.startUpdatingLocation()
    }

    public func stopUpdatingLocation() {
        isUpdating = false
        manager.stopUpdatingLocation()
    }

    /// Jedno ocitavanje lokacije (za pretragu).
    public func requestOneTimeLocation() async throws -> CLLocation {
        guard permissionStatus.isAuthorized else {
            throw LocationError.permissionDenied
        }
        return try await withCheckedThrowingContinuation { continuation in
            locationContinuation = continuation
            manager.requestLocation()
        }
    }

    private var locationContinuation: CheckedContinuation<CLLocation, Error>?
}

// MARK: - CLLocationManagerDelegate

extension SpotLinkLocationManager: CLLocationManagerDelegate {
    public nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        Task { @MainActor in
            self.currentLocation = location
            if let continuation = self.locationContinuation {
                self.locationContinuation = nil
                continuation.resume(returning: location)
            }
        }
    }

    public nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            if let continuation = self.locationContinuation {
                self.locationContinuation = nil
                continuation.resume(throwing: error)
            }
        }
    }

    public nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            self.permissionStatus = status.mapped
        }
    }
}

// MARK: - Errors & Extensions

public enum LocationError: Error {
    case permissionDenied
    case locationUnavailable
}

extension CLAuthorizationStatus {
    var mapped: LocationPermissionStatus {
        switch self {
        case .notDetermined:         return .notDetermined
        case .denied:                return .denied
        case .restricted:            return .restricted
        case .authorizedAlways:      return .authorizedAlways
        case .authorizedWhenInUse:   return .authorizedWhenInUse
        @unknown default:            return .notDetermined
        }
    }
}
