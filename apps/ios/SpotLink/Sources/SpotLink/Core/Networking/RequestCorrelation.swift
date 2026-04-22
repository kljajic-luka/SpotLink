import Foundation

// MARK: - Request Correlation

/// Dodaje X-Request-Id header na svaki zahtev.
/// Na iOS, generisemo UUID na klijentu i saljemo ga kao korelacioni ID.
enum RequestCorrelation {
    static func generateRequestId() -> String {
        "mob-\(UUID().uuidString.lowercased())"
    }
}

// MARK: - Idempotency Key

/// Generiše idempotency key koji je kompatibilan sa backend validacijom
/// (pattern: ^[A-Za-z0-9._:-]{8,160}$).
public enum IdempotencyKey {
    public static func generate(prefix: String = "sl") -> String {
        let uuid = UUID().uuidString.lowercased().replacingOccurrences(of: "-", with: "")
        return "\(prefix):\(uuid)"
    }
}
