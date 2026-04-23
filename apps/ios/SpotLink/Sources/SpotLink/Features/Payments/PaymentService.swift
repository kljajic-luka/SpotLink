import Foundation

// MARK: - Payment Service

public final class PaymentService: Sendable {
    private let apiClient: APIClientProtocol

    public init(apiClient: APIClientProtocol) {
        self.apiClient = apiClient
    }

    public func listPaymentMethods() async throws -> [PaymentMethod] {
        try await apiClient.get("/payments/methods", query: nil)
    }

    public func createIntent(_ request: CreatePaymentIntentRequest) async throws -> PaymentIntent {
        try await apiClient.post("/payments/intents", body: request)
    }

    public func confirmIntent(_ intentId: String) async throws -> PaymentProviderResult {
        try await apiClient.post("/payments/intents/\(intentId)/confirm", body: EmptyPayload())
    }
}

private struct EmptyPayload: Encodable {}
