import Foundation
import Testing
@testable import SpotLinkCore

@Suite("Payment and notification DTO contract decoding")
struct PaymentNotificationDtoTests {

    private let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()

    @Test("payment intent decodes backend response without optional metadata")
    func paymentIntentDecodesBackendShape() throws {
        let json = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "reservationId": "22222222-2222-2222-2222-222222222222",
          "amountCents": 1200,
          "currency": "RSD",
          "status": "AUTHORIZED",
          "redirectUrl": null,
          "clientSecret": "sl_pi_secret_test"
        }
        """

        let intent = try decoder.decode(PaymentIntent.self, from: Data(json.utf8))

        #expect(intent.id == "11111111-1111-1111-1111-111111111111")
        #expect(intent.customerId == nil)
        #expect(intent.status == .authorized)
    }

    @Test("payment confirm decodes provider result")
    func paymentProviderResultDecodes() throws {
        let json = """
        {
          "status": "AUTHORIZED",
          "paymentIntentId": "11111111-1111-1111-1111-111111111111",
          "redirectUrl": null,
          "message": "Already confirmed"
        }
        """

        let result = try decoder.decode(PaymentProviderResult.self, from: Data(json.utf8))

        #expect(result.status == .authorized)
        #expect(result.paymentIntentId == "11111111-1111-1111-1111-111111111111")
    }

    @Test("payment method decodes backend expMonth and default fields")
    func paymentMethodDecodesBackendShape() throws {
        let json = """
        {
          "id": "pm_card_visa",
          "brand": "Visa",
          "last4": "4242",
          "expMonth": 12,
          "expYear": 2032,
          "default": true
        }
        """

        let method = try decoder.decode(PaymentMethod.self, from: Data(json.utf8))

        #expect(method.expiryMonth == 12)
        #expect(method.expiryYear == 2032)
        #expect(method.isDefault)
    }

    @Test("payment capabilities decodes provider authority response")
    func paymentCapabilitiesDecodesBackendShape() throws {
        let json = """
        {
          "onlinePaymentsEnabled": false,
          "activeProvider": "UNCONFIGURED",
          "mockProvider": false,
          "mockPaymentMethodsAllowed": false,
          "operations": {
            "authorize": false,
            "capture": false,
            "cancel": false,
            "refund": false,
            "webhook": false,
            "reconciliation": false
          }
        }
        """

        let capabilities = try decoder.decode(PaymentCapabilities.self, from: Data(json.utf8))

        #expect(capabilities.onlinePaymentsEnabled == false)
        #expect(capabilities.activeProvider == "UNCONFIGURED")
        #expect(capabilities.canAuthorizeOnlinePayment == false)
        #expect(capabilities.operations.refund == false)
    }

    @Test("notification decodes backend read field without userId")
    func notificationDecodesBackendShape() throws {
        let json = """
        {
          "id": "33333333-3333-3333-3333-333333333333",
          "type": "RESERVATION_CONFIRMED",
          "title": "Rezervacija potvrdjena",
          "body": "Vase parking mesto je rezervisano.",
          "relatedEntityId": "22222222-2222-2222-2222-222222222222",
          "read": false,
          "createdAt": "2026-04-23T12:00:00Z"
        }
        """

        let notification = try decoder.decode(SpotLinkNotification.self, from: Data(json.utf8))

        #expect(notification.userId == nil)
        #expect(!notification.read)
    }

    @Test("device token request uses backend IOS enum value")
    func deviceTokenUsesBackendEnumValue() throws {
        let request = RegisterDeviceTokenRequest(deviceToken: "token")
        let data = try JSONEncoder().encode(request)
        let object = try JSONSerialization.jsonObject(with: data) as? [String: String]

        #expect(object?["platform"] == "IOS")
    }

    @Test("unregister device token request uses backend IOS enum value")
    func unregisterDeviceTokenUsesBackendEnumValue() throws {
        let request = UnregisterDeviceTokenRequest(deviceToken: "token")
        let data = try JSONEncoder().encode(request)
        let object = try JSONSerialization.jsonObject(with: data) as? [String: String]

        #expect(object?["deviceToken"] == "token")
        #expect(object?["platform"] == "IOS")
    }
}
