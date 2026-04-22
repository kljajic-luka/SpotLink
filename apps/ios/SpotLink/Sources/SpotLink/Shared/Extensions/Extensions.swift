import Foundation

// MARK: - Date Extensions

public extension Date {
    var iso8601String: String {
        ISO8601DateFormatter().string(from: self)
    }

    func formatted(style: DateFormatter.Style = .medium) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = style
        formatter.timeStyle = style == .short ? .short : .none
        return formatter.string(from: self)
    }

    var isToday: Bool {
        Calendar.current.isDateInToday(self)
    }

    var isFuture: Bool {
        self > Date()
    }
}

// MARK: - String Extensions

public extension String {
    var trimmed: String { trimmingCharacters(in: .whitespaces) }
    var isBlank: Bool { trimmed.isEmpty }
    var nilIfBlank: String? { isBlank ? nil : self }
}

// MARK: - Optional Extensions

public extension Optional where Wrapped == String {
    var orEmpty: String { self ?? "" }
}
