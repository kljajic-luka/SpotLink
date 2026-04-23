import Foundation
import Testing
@testable import SpotLinkCore

// MARK: - UserProfile – computed props

@Suite("UserProfile – Identitet i email")
struct UserProfileIdentityTests {

    private func makeProfile() -> UserProfile {
        UserProfile(
            id: "abc-123",
            email: "marko@example.com",
            firstName: "Marko",
            lastName: "Markovic",
            phone: "+381601234567",
            avatarUrl: nil,
            bio: nil,
            roles: [.customer, .operator_],
            operatorId: "op-999",
            registrationStatus: "ACTIVE",
            createdAt: nil
        )
    }

    @Test("id se ispravno cuva")
    func profileId() {
        #expect(makeProfile().id == "abc-123")
    }

    @Test("email se ispravno cuva")
    func profileEmail() {
        #expect(makeProfile().email == "marko@example.com")
    }

    @Test("korisnik sa vise rola ispravno prepoznaje svaku")
    func multipleRoles() {
        let p = makeProfile()
        #expect(p.isCustomer)
        #expect(p.isOperator)
        #expect(!p.isAdmin)
    }

    @Test("operatorId se ispravno cuva")
    func operatorId() {
        #expect(makeProfile().operatorId == "op-999")
    }
}

// MARK: - RegisterCustomerRequest

@Suite("RegisterCustomerRequest – Kreiranje")
struct RegisterCustomerRequestTests {

    @Test("Sva polja se ispravno postavljaju")
    func fieldValues() {
        let request = RegisterCustomerRequest(
            firstName: "Jelena",
            lastName: "Jovanovic",
            email: "jelena@example.com",
            password: "Tajna123!",
            acceptsTerms: true
        )
        #expect(request.firstName == "Jelena")
        #expect(request.lastName == "Jovanovic")
        #expect(request.email == "jelena@example.com")
        #expect(request.acceptsTerms == true)
        #expect(request.phone == nil)
    }
}

@Suite("MobileTokenResponse – Refresh token")
struct MobileTokenResponseTests {

    @Test("backend token response decodes refresh lifecycle fields")
    func refreshFieldsDecode() throws {
        let json = """
        {
          "accessToken": "access",
          "refreshToken": "refresh",
          "tokenType": "Bearer",
          "expiresIn": 900,
          "expiresInSeconds": 900,
          "refreshExpiresInSeconds": 2592000,
          "issuedAt": "2026-04-23T12:00:00Z",
          "expiresAt": "2026-04-23T12:15:00Z",
          "refreshExpiresAt": "2026-05-23T12:00:00Z",
          "user": {
            "id": "u1",
            "email": "marko@example.com",
            "firstName": "Marko",
            "lastName": "Markovic",
            "roles": ["CUSTOMER"]
          },
          "roles": ["CUSTOMER"]
        }
        """

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let response = try decoder.decode(MobileTokenResponse.self, from: Data(json.utf8))

        #expect(response.accessToken == "access")
        #expect(response.refreshToken == "refresh")
        #expect(response.refreshExpiresInSeconds == 2_592_000)
        #expect(response.user.isCustomer)
    }
}
