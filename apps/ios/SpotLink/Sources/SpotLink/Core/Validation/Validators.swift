import Foundation

// MARK: - Form Validators

public enum Validators {

    public static func isValidEmail(_ value: String) -> Bool {
        let pattern = #"^[A-Z0-9a-z._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$"#
        return value.range(of: pattern, options: .regularExpression) != nil
    }

    public static func isValidPassword(_ value: String) -> String? {
        if value.isEmpty { return "Lozinka je obavezna." }
        if value.count < 8 { return "Lozinka mora imati najmanje 8 karaktera." }
        if value.count > 128 { return "Lozinka moze imati najvise 128 karaktera." }
        return nil
    }

    public static func isValidPhone(_ value: String) -> Bool {
        let stripped = value.filter { $0.isNumber || $0 == "+" }
        return stripped.count >= 7 && stripped.count <= 20
    }

    public static func isNonEmpty(_ value: String, fieldName: String) -> String? {
        value.trimmingCharacters(in: .whitespaces).isEmpty ? "\(fieldName) je obavezno." : nil
    }

    public static func maxLength(_ value: String, max: Int, fieldName: String) -> String? {
        value.count > max ? "\(fieldName) moze imati najvise \(max) karaktera." : nil
    }
}

// MARK: - Validation Result

public struct ValidationResult {
    public var errors: [String: String] = [:]

    public var isValid: Bool { errors.isEmpty }

    public mutating func addError(_ error: String?, forKey key: String) {
        if let error { errors[key] = error }
    }
}
