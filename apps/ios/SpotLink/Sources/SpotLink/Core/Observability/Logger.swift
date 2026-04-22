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
    public static var minimumLevel: Level = .error
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

    private static func log(_ level: Level, _ message: String, file: String, line: Int) {
        guard level >= minimumLevel else { return }
        let filename = URL(fileURLWithPath: file).lastPathComponent
        let prefix: String
        switch level {
        case .debug: prefix = "🔍 DEBUG"
        case .info:  prefix = "ℹ️ INFO "
        case .warn:  prefix = "⚠️ WARN "
        case .error: prefix = "❌ ERROR"
        }
        print("[\(prefix)] \(filename):\(line) → \(message)")
    }
}
