import Foundation
import Testing
@testable import SpotLinkCore

@Suite("Mobile API contract fixture decoding")
struct MobileApiFixtureDecodingTests {

    private let decoder = JSONDecoder.spotLinkBackend()

    @Test("auth and profile response fixtures decode through app models")
    func authAndProfileFixturesDecode() throws {
        _ = try decode("auth-login-response", as: AuthResponseEnvelope.self)

        let token = try decode("auth-token-response", as: MobileTokenResponse.self)
        #expect(token.tokenType == "Bearer")
        #expect(token.user.isCustomer)

        let currentUser = try decode("auth-me-response", as: UserProfile.self)
        #expect(currentUser.registrationStatus == "ACTIVE")

        let profile = try decode("profile-response", as: UserProfileDetails.self)
        #expect(profile.stats.activeVehicles == 1)
        #expect(profile.preferences.reservationAlerts)
    }

    @Test("location and vehicle response fixtures decode through app models")
    func locationAndVehicleFixturesDecode() throws {
        let vehicle = try decode("vehicle-response", as: VehicleProfile.self)
        #expect(vehicle.type == .car)
        #expect(vehicle.evCapable)

        let location = try decode("parking-location-response", as: ParkingLocation.self)
        #expect(location.accessType == .gateCode)

        let resource = try decode("parking-resource-response", as: ParkingResource.self)
        #expect(resource.capacity == 1)
        #expect(resource.confirmationMode == .instant)
        #expect(resource.availablePaymentModes.contains(.payOnArrival))

        let searchPage = try decode("location-search-response", as: APIPage<LocationSearchResult>.self)
        #expect(searchPage.content.count == 1)
        #expect(searchPage.content[0].resources[0].capacity == 1)
    }

    @Test("reservation and payment response fixtures decode through app models")
    func reservationAndPaymentFixturesDecode() throws {
        let quote = try decode("reservation-quote-response", as: ReservationQuote.self)
        #expect(quote.totalAmountCents == 1_944)

        let confirmed = try decode("reservation-response-confirmed", as: Reservation.self)
        #expect(confirmed.status == .confirmed)
        #expect(confirmed.paymentMode == .online)

        let cancelled = try decode("reservation-response-cancelled", as: Reservation.self)
        #expect(cancelled.status == .cancelled)
        #expect(cancelled.paymentMode == .online)

        let capabilities = try decode("payment-capabilities-response", as: PaymentCapabilities.self)
        #expect(!capabilities.canAuthorizeOnlinePayment)

        let intent = try decode("payment-intent-response", as: PaymentIntent.self)
        #expect(intent.status == .authorized)
    }

    @Test("support notification dashboard and error fixtures decode through app models")
    func supportNotificationDashboardAndErrorFixturesDecode() throws {
        let ticket = try decode("support-ticket-response", as: SupportTicket.self)
        #expect(ticket.status == .open)

        let message = try decode("support-message-response", as: SupportMessage.self)
        #expect(message.ticketId == ticket.id)

        let notification = try decode("notification-response", as: SpotLinkNotification.self)
        #expect(!notification.read)

        let unreadCount = try decode("notification-unread-count-response", as: NotificationUnreadCount.self)
        #expect(unreadCount.count == 3)

        let notificationPage = try decode("paginated-response-example", as: APIPage<SpotLinkNotification>.self)
        #expect(notificationPage.totalElements == 1)

        let operatorSummary = try decode("operator-dashboard-response", as: OperatorDashboardSummary.self)
        #expect(operatorSummary.activeResources == 42)

        let adminSummary = try decode("admin-dashboard-response", as: AdminDashboardSummary.self)
        #expect(adminSummary.openSupportTickets == 12)

        let standardError = try decode("standard-error-response", as: APIErrorEnvelope.self)
        #expect(standardError.code == "RESOURCE_UNAVAILABLE")
        #expect(standardError.requestId != nil)

        let validationError = try decode("validation-error-response", as: APIErrorEnvelope.self)
        #expect(validationError.code == "VALIDATION_ERROR")
        #expect(validationError.details?["email"] != nil)
    }

    private func decode<T: Decodable>(_ fixtureName: String, as type: T.Type) throws -> T {
        let data = try Data(contentsOf: fixturesDirectory().appendingPathComponent("\(fixtureName).json"))
        return try decoder.decode(type, from: data)
    }

    private func fixturesDirectory() throws -> URL {
        var directory = URL(fileURLWithPath: FileManager.default.currentDirectoryPath, isDirectory: true)
        for _ in 0..<8 {
            let candidate = directory
                .appendingPathComponent("docs/mobile-api-contract/json-fixtures", isDirectory: true)
            if FileManager.default.fileExists(atPath: candidate.path) {
                return candidate
            }
            directory.deleteLastPathComponent()
        }
        throw FixtureError.notFound(FileManager.default.currentDirectoryPath)
    }

    enum FixtureError: Error, CustomStringConvertible {
        case notFound(String)

        var description: String {
            switch self {
            case .notFound(let start):
                return "Could not find docs/mobile-api-contract/json-fixtures from \(start)"
            }
        }
    }
}
