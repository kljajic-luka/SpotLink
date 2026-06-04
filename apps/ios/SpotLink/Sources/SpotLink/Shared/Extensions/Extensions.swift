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

// MARK: - Backend JSON

public extension JSONDecoder {
    static func spotLinkBackend() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .useDefaultKeys
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let value = try container.decode(String.self)

            let fractionalFormatter = ISO8601DateFormatter()
            fractionalFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            if let date = fractionalFormatter.date(from: value) {
                return date
            }

            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime]
            if let date = formatter.date(from: value) {
                return date
            }

            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Invalid ISO8601 date: \(value)"
            )
        }
        return decoder
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
