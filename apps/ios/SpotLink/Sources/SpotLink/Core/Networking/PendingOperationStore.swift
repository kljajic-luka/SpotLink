import Foundation

// MARK: - Pending Operation Store

/// Cuva stabilne idempotency kljuceve tokom jednog logickog toka.
/// View model resetuje kljuc tek kada tok bude zavrsen ili namerno odbacen.
public final class PendingOperationStore {
    private var keysByOperation: [String: String] = [:]

    public init() {}

    public func key(for operation: String, prefix: String) -> String {
        if let existing = keysByOperation[operation] {
            return existing
        }

        let generated = IdempotencyKey.generate(prefix: prefix)
        keysByOperation[operation] = generated
        return generated
    }

    public func reset(operation: String) {
        keysByOperation.removeValue(forKey: operation)
    }

    public func resetAll() {
        keysByOperation.removeAll()
    }
}