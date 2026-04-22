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

