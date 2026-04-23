import Foundation

#if canImport(MapboxMaps)
import MapboxMaps
#endif

public enum SpotLinkMapProvider: String, Sendable {
    case mapbox
    case mapKitFallback
}

public enum MapRuntimeConfiguration {
    public static let infoPlistKey = "SPOTLINK_MAPBOX_PUBLIC_TOKEN"

#if DEBUG
    // TODO: Zameniti debug fallback kontrolisanom token distribucijom pre produkcije.
    private static let debugFallbackPublicToken = "pk.eyJ1Ijoia2xqYWphMDEiLCJhIjoiY21pcmV6ZWtkMDgxMDNkcXRkdGg4OGVpbCJ9.Z3UrBxYVEw4tjjLrEaiIgg"
#endif

    public static func configureMapProvider(
        bundle: Bundle = .main,
        processInfo: ProcessInfo = .processInfo
    ) -> SpotLinkMapProvider {
        guard let token = resolvedMapboxToken(bundle: bundle, processInfo: processInfo) else {
            return .mapKitFallback
        }

#if canImport(MapboxMaps)
        MapboxOptions.accessToken = token
        return .mapbox
#else
        return .mapKitFallback
#endif
    }

    public static func resolvedMapboxToken(
        bundle: Bundle = .main,
        processInfo: ProcessInfo = .processInfo
    ) -> String? {
        let candidates = [
            processInfo.environment[infoPlistKey],
            bundle.object(forInfoDictionaryKey: infoPlistKey) as? String
        ]

        for candidate in candidates {
            if let token = normalize(candidate) {
                return token
            }
        }

#if DEBUG
        return debugFallbackPublicToken
#else
        return nil
#endif
    }

    private static func normalize(_ rawValue: String?) -> String? {
        guard let value = rawValue?.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty,
              !value.hasPrefix("$(") else {
            return nil
        }
        return value
    }
}