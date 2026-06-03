import Foundation
import Testing
@testable import SpotLinkCore

private final class PaymentServiceMockAPIClient: APIClientProtocol, @unchecked Sendable {
    var getPaths: [String] = []
    var postPaths: [String] = []

    func get<T: Decodable>(_ path: String, query: [String: String]? = nil) async throws -> T {
        getPaths.append(path)
        switch path {
        case "/payments/capabilities":
            return PaymentCapabilities(
                onlinePaymentsEnabled: true,
                activeProvider: "MOCK",
                mockProvider: true,
                mockPaymentMethodsAllowed: true,
                operations: PaymentOperationCapabilities(
                    authorize: true,
                    capture: true,
                    cancel: true,
                    refund: true,
                    webhook: false,
                    reconciliation: false
                )
            ) as! T
        default:
            throw APIError.notFound(APIErrorContext(message: "Nepoznat GET put: \(path)"))
        }
    }

    func post<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        postPaths.append(path)
        switch path {
        case "/payments/intents/pi-123/cancel":
            return PaymentProviderResult(
                status: .cancelled,
                paymentIntentId: "pi-123",
                redirectUrl: nil,
                message: "Cancelled"
            ) as! T
        default:
            throw APIError.notFound(APIErrorContext(message: "Nepoznat POST put: \(path)"))
        }
    }

    func put<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        throw APIError.serverError(500, APIErrorContext(message: "PUT nije potreban u ovom testu"))
    }

    func patch<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        throw APIError.serverError(500, APIErrorContext(message: "PATCH nije potreban u ovom testu"))
    }

    func delete(_ path: String) async throws {
        throw APIError.serverError(500, APIErrorContext(message: "DELETE nije potreban u ovom testu"))
    }
}

@Suite("Payment service endpoints")
struct PaymentServiceTests {

    @Test("loads payment capabilities from backend authority endpoint")
    func capabilitiesUsesBackendEndpoint() async throws {
        let client = PaymentServiceMockAPIClient()
        let service = PaymentService(apiClient: client)

        let capabilities = try await service.capabilities()

        #expect(client.getPaths == ["/payments/capabilities"])
        #expect(capabilities.canAuthorizeOnlinePayment)
        #expect(capabilities.activeProvider == "MOCK")
    }

    @Test("cancels provider intent through backend endpoint")
    func cancelIntentUsesBackendEndpoint() async throws {
        let client = PaymentServiceMockAPIClient()
        let service = PaymentService(apiClient: client)

        let result = try await service.cancelIntent("pi-123")

        #expect(client.postPaths == ["/payments/intents/pi-123/cancel"])
        #expect(result.status == .cancelled)
        #expect(result.paymentIntentId == "pi-123")
    }
}
