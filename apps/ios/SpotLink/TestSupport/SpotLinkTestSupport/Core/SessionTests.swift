import Testing
@testable import SpotLinkCore

// MARK: - SessionState

@Suite("SessionState")
struct SessionStateTests {

    @Test("Loading state nije autentifikovan i nema sesiju")
    func loadingNotAuthenticated() {
        let state = SessionState.loading
        #expect(!state.isAuthenticated)
        #expect(state.sessionInfo == nil)
        #expect(state.user == nil)
    }

    @Test("Unauthenticated state nije autentifikovan")
    func unauthenticatedNotAuthenticated() {
        let state = SessionState.unauthenticated
        #expect(!state.isAuthenticated)
        #expect(state.sessionInfo == nil)
    }
}

// MARK: - UserProfile

@Suite("UserProfile – Pomocni izracunati atributi")
struct UserProfileComputedTests {

    private func makeProfile(firstName: String, lastName: String, roles: [UserRole]) -> UserProfile {
        UserProfile(
            id: "u1",
            email: "test@example.com",
            firstName: firstName,
            lastName: lastName,
            phone: nil,
            avatarUrl: nil,
            bio: nil,
            roles: roles,
            operatorId: nil,
            registrationStatus: "ACTIVE",
            createdAt: nil
        )
    }

    @Test("fullName spaja ime i prezime")
    func fullName() {
        let profile = makeProfile(firstName: "Petar", lastName: "Petrovic", roles: [.customer])
        #expect(profile.fullName == "Petar Petrovic")
    }

    @Test("initials vraca prva slova")
    func initials() {
        let profile = makeProfile(firstName: "Ana", lastName: "Anic", roles: [.customer])
        #expect(profile.initials == "AA")
    }

    @Test("isCustomer prepoznaje rolu kupca")
    func isCustomer() {
        let profile = makeProfile(firstName: "X", lastName: "Y", roles: [.customer])
        #expect(profile.isCustomer)
        #expect(!profile.isOperator)
        #expect(!profile.isAdmin)
    }

    @Test("isOperator prepoznaje rolu operatora")
    func isOperator() {
        let profile = makeProfile(firstName: "X", lastName: "Y", roles: [.operator_])
        #expect(profile.isOperator)
        #expect(!profile.isCustomer)
    }

    @Test("isAdmin prepoznaje rolu admina")
    func isAdmin() {
        let profile = makeProfile(firstName: "X", lastName: "Y", roles: [.admin])
        #expect(profile.isAdmin)
    }

    @Test("isSupport prepoznaje rolu podrske")
    func isSupport() {
        let profile = makeProfile(firstName: "X", lastName: "Y", roles: [.support])
        #expect(profile.isSupport)
    }
}

// MARK: - UserRole

@Suite("UserRole – Raw vrednosti")
struct UserRoleTests {

    @Test("UserRole.customer ima ocekivanu raw vrednost")
    func customerRaw() {
        #expect(UserRole.customer.rawValue == "CUSTOMER")
    }

    @Test("UserRole.operator_ ima ocekivanu raw vrednost")
    func operatorRaw() {
        #expect(UserRole.operator_.rawValue == "OPERATOR")
    }

    @Test("UserRole.admin ima ocekivanu raw vrednost")
    func adminRaw() {
        #expect(UserRole.admin.rawValue == "ADMIN")
    }

    @Test("UserRole.support ima ocekivanu raw vrednost")
    func supportRaw() {
        #expect(UserRole.support.rawValue == "SUPPORT")
    }

    @Test("UserRole se kreira iz raw vrednosti")
    func fromRaw() {
        #expect(UserRole(rawValue: "CUSTOMER") == .customer)
        #expect(UserRole(rawValue: "OPERATOR") == .operator_)
        #expect(UserRole(rawValue: "SUPPORT") == .support)
        #expect(UserRole(rawValue: "ADMIN") == .admin)
        #expect(UserRole(rawValue: "UNKNOWN") == nil)
    }
}

