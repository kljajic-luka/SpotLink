import Testing
@testable import SpotLinkCore


// MARK: - Email validator

@Suite("Validators – Email")
struct EmailValidatorTests {

    @Test("Ispravna email adresa prolazi validaciju")
    func validEmail() {
        #expect(Validators.isValidEmail("korisnik@example.com"))
        #expect(Validators.isValidEmail("ime.prezime+tag@company.co.rs"))
    }

    @Test("Neispravan email ne prolazi validaciju")
    func invalidEmail() {
        #expect(!Validators.isValidEmail(""))
        #expect(!Validators.isValidEmail("nema-at-znaka"))
        #expect(!Validators.isValidEmail("@nodomain.com"))
        #expect(!Validators.isValidEmail("bez.domenske.ekstenzije@host"))
    }
}

// MARK: - Password validator

@Suite("Validators – Lozinka")
struct PasswordValidatorTests {

    @Test("Prazna lozinka vraca gresku")
    func emptyPassword() {
        #expect(Validators.isValidPassword("") != nil)
    }

    @Test("Kratka lozinka vraca gresku")
    func shortPassword() {
        #expect(Validators.isValidPassword("abc123") != nil)
    }

    @Test("Ispravna lozinka prolazi validaciju")
    func validPassword() {
        #expect(Validators.isValidPassword("LozinkaJaka1!") == nil)
    }

    @Test("Preduga lozinka vraca gresku")
    func tooLongPassword() {
        let dugacka = String(repeating: "A", count: 129)
        #expect(Validators.isValidPassword(dugacka) != nil)
    }
}

// MARK: - ValidationResult

@Suite("ValidationResult")
struct ValidationResultTests {

    @Test("Pocetni state je validan")
    func initiallyValid() {
        let result = ValidationResult()
        #expect(result.isValid)
        #expect(result.errors.isEmpty)
    }

    @Test("Dodavanje greske cini state nevalidnim")
    func addError() {
        var result = ValidationResult()
        result.addError("Obavezno polje.", forKey: "email")
        #expect(!result.isValid)
        #expect(result.errors["email"] == "Obavezno polje.")
    }

    @Test("Dodavanje nil greske ne menja state")
    func addNilError() {
        var result = ValidationResult()
        result.addError(nil, forKey: "password")
        #expect(result.isValid)
    }
}
