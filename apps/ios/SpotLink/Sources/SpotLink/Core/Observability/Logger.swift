import Foundation

// MARK: - SpotLink Logger

/// Produkciono bezbedni logger – na DEBUG stampa detalje, na Release samo greske.
public enum SpotLinkLogger {
    public enum Level: Int, Comparable {
        case debug = 0
        case info  = 1
        case warn  = 2
        case error = 3

        public static func < (lhs: Level, rhs: Level) -> Bool { lhs.rawValue < rhs.rawValue }
    }

    #if DEBUG
    nonisolated(unsafe) public static var minimumLevel: Level = .debug
    #else
    nonisolated(unsafe) public static var minimumLevel: Level = .error
    #endif

    public static func debug(_ message: @autoclosure () -> String, file: String = #file, line: Int = #line) {
        log(.debug, message(), file: file, line: line)
    }

    public static func info(_ message: @autoclosure () -> String, file: String = #file, line: Int = #line) {
        log(.info, message(), file: file, line: line)
    }

    public static func warn(_ message: @autoclosure () -> String, file: String = #file, line: Int = #line) {
        log(.warn, message(), file: file, line: line)
    }

    public static func error(_ message: @autoclosure () -> String, file: String = #file, line: Int = #line) {
        log(.error, message(), file: file, line: line)
    }

    public static func redactedForLog(_ message: String) -> String {
        var redacted = message
        let replacements: [(String, String)] = [
            (#"(Bearer\s+)[A-Za-z0-9._~+/=-]+"#, "$1[REDACTED]"),
            (#"sl_reset_[A-Za-z0-9-]+"#, "sl_reset_[REDACTED]"),
            (#"(?i)((?:accessToken|refreshToken|token|authorization)=)[^&\s]+"#, "$1[REDACTED]"),
            ("(?i)(\"(?:accessToken|refreshToken|token|authorization)\"\\s*:\\s*\")[^\"]+(\")", "$1[REDACTED]$2")
        ]

        for (pattern, replacement) in replacements {
            redacted = redacted.replacingOccurrences(
                of: pattern,
                with: replacement,
                options: .regularExpression
            )
        }
        return redacted
    }

    private static func log(_ level: Level, _ message: String, file: String, line: Int) {
        guard level >= minimumLevel else { return }
        let filename = URL(fileURLWithPath: file).lastPathComponent
        let safeMessage = redactedForLog(message)
        let prefix: String
        switch level {
        case .debug: prefix = "🔍 DEBUG"
        case .info:  prefix = "ℹ️ INFO "
        case .warn:  prefix = "⚠️ WARN "
        case .error: prefix = "❌ ERROR"
        }
        print("[\(prefix)] \(filename):\(line) → \(safeMessage)")
    }
}
