import Foundation

// MARK: - Keychain Storage

/// Thread-safe Keychain wrapper za cuvanje tokena i osetljivih podataka.
/// Na macOS i iOS koristi `Security` framework.
public final class KeychainStorage: Sendable {

    public static let shared = KeychainStorage()
    private let service: String

    public init(service: String = "app.spotlink.ios") {
        self.service = service
    }

    // MARK: - Public API

    public func save(_ value: String, forKey key: String) throws {
        let data = Data(value.utf8)
        try save(data, forKey: key)
    }

    public func read(forKey key: String) -> String? {
        guard let data = readData(forKey: key) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    public func delete(forKey key: String) {
        let query = baseQuery(forKey: key)
        SecItemDelete(query as CFDictionary)
    }

    public func deleteAll() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - Private

    private func save(_ data: Data, forKey key: String) throws {
        delete(forKey: key)
        var query = baseQuery(forKey: key)
        query[kSecValueData as String] = data
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError.saveFailed(status)
        }
    }

    private func readData(forKey key: String) -> Data? {
        var query = baseQuery(forKey: key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return data
    }

    private func baseQuery(forKey key: String) -> [String: Any] {
        return [
            kSecClass as String:       kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]
    }
}

// MARK: - Keychain Error

public enum KeychainError: Error {
    case saveFailed(OSStatus)
}

// MARK: - UserDefaults Storage

/// Imenovan UserDefaults namespace za nesenzitivne korisnicke postavke.
public final class PreferenceStorage: Sendable {

    public static let shared = PreferenceStorage()
    private let prefix: String
    private nonisolated(unsafe) let defaults: UserDefaults

    public init(prefix: String = "spotlink.", defaults: UserDefaults = .standard) {
        self.prefix = prefix
        self.defaults = defaults
    }

    private func key(_ k: String) -> String { "\(prefix)\(k)" }

    public func set<T>(_ value: T, forKey k: String) where T: Any {
        defaults.set(value, forKey: key(k))
    }

    public func string(forKey k: String) -> String? {
        defaults.string(forKey: key(k))
    }

    public func bool(forKey k: String, default defaultValue: Bool = false) -> Bool {
        guard defaults.object(forKey: key(k)) != nil else { return defaultValue }
        return defaults.bool(forKey: key(k))
    }

    public func remove(forKey k: String) {
        defaults.removeObject(forKey: key(k))
    }
}
