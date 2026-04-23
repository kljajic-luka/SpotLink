# SpotLink iOS Foundation – Migracioni dokument

## Pregled

Ovaj dokument opisuje postavljanje iOS Swift Package fondacije i Xcode app target-a
za SpotLink aplikaciju, ukljucujuci arhitekturalne odluke, module, tok autentifikacije
i instrukcije za pokretanje.

**Poslednja azuriranja (2026-04-22):**
- Dodat `apps/ios/SpotLink.xcodeproj` sa app targetom, unit test i UI test targetima
- Dodat asset catalog sa AppIcon, AccentColor i Background color set-ovima
- Dodat `Info.plist`, `PrivacyInfo.xcprivacy` i `SpotLink.entitlements`
- Uklonjena duplikacija `AppEnvironmentKey` iz `SpotLinkApp.swift`
- `swift build`, `swift test`, `npm run test:ios` i `xcodebuild build` prolaze

---

## 1. Audit nalaza

### Stanje pre migracije

- Nije postojao iOS Swift projekat; backend je imao `/auth/token` endpoint ali bez podrske za `Bearer` JWT tokene.
- Web frontend koristi cookie-bazirani sesijski token (`session_token`); iOS zahteva `Bearer` JWT.
- OpenAPI specifikacija (`spotlink-api-draft.openapi.yaml`) opisuje `/auth/token` endpoint ali bez iOS-specificnog toka.

### Promene na backendu

Radi podrske za iOS, backend je prosiren:

| Fajl | Promena |
|------|---------|
| `pom.xml` | Dodat `jjwt-api`, `jjwt-impl`, `jjwt-jackson` za JWT |
| `application.properties` | Dodat `spotlink.jwt.secret`, `spotlink.jwt.expiration-seconds` |
| `AuthController.java` | Novi endpoint `POST /api/auth/token` vraca `MobileTokenResponse` (accessToken, expiresIn, tokenType, user) |
| `JwtTokenService.java` | Nova klasa za generisanje i validaciju JWT tokena |
| Flyway migracije | Azurirane za podrsku korisnickim rolama |

---

## 2. Arhitektura iOS aplikacije

### Platforma i alati

- **Jezik**: Swift 6 (swift-tools-version: 6.0)
- **UI framework**: SwiftUI
- **Arhitektura**: MVVM sa async/await
- **Platforme**: `.iOS(.v17)` + `.macOS(.v14)` (dual target za macOS build bez Xcode iOS SDK)
- **Build alat**: Swift Package Manager (SPM)
- **Lokacija**: `apps/ios/SpotLink/`

### Struktura modula

```
apps/ios/SpotLink/
├── Package.swift
├── Sources/
│   └── SpotLink/
│       ├── App/
│       │   ├── SpotLinkApp.swift          # @main entry point
│       │   ├── RootView.swift             # Koren pogleda – routing po session state
│       │   └── MainAppShell.swift         # TabView sa 5 tabova
│       ├── Core/
│       │   ├── Auth/
│       │   │   ├── AuthModels.swift        # UserProfile, UserRole, zahtevi/odgovori
│       │   │   ├── AuthService.swift       # Login, registracija, password reset
│       │   │   └── Validators.swift        # Email, lozinka, telefon validacija
│       │   ├── Config/
│       │   │   └── AppEnvironment.swift    # .local/.development/.staging/.production
│       │   ├── DesignSystem/
│       │   │   └── DesignTokens.swift      # Boje, tipografija, razmak, senke
│       │   ├── Location/
│       │   │   └── LocationManager.swift   # CLLocationManager wrapper
│       │   ├── Networking/
│       │   │   ├── APIClient.swift         # Generic async HTTP klijent
│       │   │   ├── APIError.swift          # Greske (unauthorized, serverError, itd.)
│       │   │   └── RequestCorrelation.swift # Idempotency key per zahtev
│       │   ├── Observability/
│       │   │   └── Logger.swift            # Miniaturni logger sa nivoima
│       │   ├── Session/
│       │   │   ├── SessionManager.swift    # @MainActor ObservableObject; JWT u Keychain
│       │   │   └── StorageAdapters.swift   # KeychainStorage + PreferenceStorage
│       │   └── Validation/
│       │       └── (u Validators.swift)
│       ├── Features/
│       │   ├── Auth/
│       │   │   └── AuthViews.swift         # AuthFlowView, LoginView, RegisterView
│       │   ├── Reservations/
│       │   │   └── ReservationsView.swift  # Lista i otkazivanje rezervacija
│       │   ├── Support/
│       │   │   └── SupportView.swift       # Tiketi za podrsku
│       │   └── Vehicles/
│       │       └── VehiclesView.swift      # Upravljanje vozilima
│       └── Shared/
│           ├── Extensions/
│           │   ├── Extensions.swift        # Date, String, Optional
│           │   └── View+PlatformModifiers.swift  # Cross-platform SwiftUI helperi
│           ├── Models/                     # Deljeni modeli (vozila, lokacije, itd.)
│           └── Services/                   # Feature servisi (VehicleService, itd.)
└── Tests/
    └── SpotLinkTests/
        ├── Core/
        │   ├── ValidatorsTests.swift       # Email, lozinka, ValidationResult
        │   └── SessionTests.swift          # SessionState, UserProfile, UserRole
        └── Features/
            └── AuthModelsTests.swift       # UserProfile atributi, RegisterCustomerRequest
```

---

## 3. Autentifikacioni tok

### iOS autentifikacija (JWT Bearer)

```
iOS app → POST /api/auth/token
          Body: { email, password, deviceId, platform: "MOBILE" }
          
          ← 200 OK:
          {
            "accessToken": "eyJhbGci...",
            "expiresIn": 86400,
            "tokenType": "Bearer",
            "user": { id, email, firstName, lastName, roles, ... }
          }
```

### Web autentifikacija (Cookie sesija, nepromenjena)

```
Web app → POST /api/auth/login
          ← Set-Cookie: session_token=...
```

### Cuvanje tokena na iOS

| Podatak | Lokacija | Razlog |
|---------|----------|--------|
| `accessToken` | iOS Keychain | Bezbedno cuvanje osetljivih podataka |
| `tokenExpiresAt` | UserDefaults | Timestamp isteka (non-sensitive) |
| `userProfile` (JSON) | UserDefaults | Brz pristup profilu |

### Session lifecycle

1. **Pokretanje**: `SessionManager.restoreSession()` cita token iz Keychain; ako je istekao → `.unauthenticated`
2. **Prijava**: `AuthService.login()` → `POST /auth/token` → `SessionManager.establish()`
3. **Odjava**: `SessionManager.signOut()` → brise Keychain i UserDefaults
4. **Obnova tokena**: Nije implementiran refresh token; po isteku token, korisnik se odjavlja

---

## 4. Cross-platform (iOS + macOS) re-resen

Paket cilja i iOS (.v17) i macOS (.v14) istovremeno. Neke SwiftUI API-je nisu dostupne na oba
sistema pa su dodate pomocne metode u `View+PlatformModifiers.swift`:

| SwiftUI API | iOS | macOS |
|-------------|-----|-------|
| `.navigationBarHidden(true)` | `.toolbar(.hidden, for: .navigationBar)` | no-op |
| `.listStyle(.insetGrouped)` | `.insetGrouped` | `.inset` |
| `ToolbarItemPlacement.topBarTrailing` | `.topBarTrailing` | `.automatic` |
| `.textContentType(.emailAddress)` | aktivan | ignorisan |
| `.keyboardType(.emailAddress)` | aktivan | ignorisan |

---

## 5. Komande za pokretanje

### Preduslovi

```bash
# Proveriti Swift verziju (potreban 5.10+)
swift --version

# Proveriti SPM
swift package --version
```

### Build

```bash
cd apps/ios/SpotLink

# Cistiti build keš i rekompajlirati
rm -rf .build && swift build
# Ocekivani izlaz: Build complete!
```

### Testovi

```bash
cd apps/ios/SpotLink
swift test
# Ocekivani izlaz: Swift Testing summary sa stvarno izvrsenim testovima
```

> **Napomena**: `swift test` i `npm run test:ios` sada daju isti Swift Testing
> izlaz. Prvi XCTest summary moze prikazati `Executed 0 tests`, a odmah zatim
> sledi stvarni Swift Testing run summary koji je relevantan za verifikaciju.

### Generisanje Xcode projekta (opciono)

```bash
brew install xcodegen
cd apps/ios/SpotLink
xcodegen generate  # zahteva project.yml koji treba kreirati
```

---

## 6. Okruzenja (Environments)

iOS app podrзava 4 okruzenja konfigurabilna putem `SPOTLINK_ENV` env varijable ili `Info.plist`:

| Okruzenje | API URL |
|-----------|---------|
| `local` | `http://localhost:8080/api` |
| `development` | `https://api-dev.spotlink.app/api` |
| `staging` | `https://api-staging.spotlink.app/api` |
| `production` | `https://api.spotlink.app/api` |

---

## 7. Poznate ogranicenja i sledeci koraci

### Ogranicenja

| Oblast | Status |
|--------|--------|
| Refresh token | Nije implementiran; po isteku JWT korisnik se odjavlja |
| Push notifikacije | `PushNotificationManager` implementiran ali nije testiran na uredjaju |
| Offline mod | Nema lokalnog cache-a; zahteva internet konekciju |
| Xctest izvrsavanje | Zahteva puni Xcode; CLT-only ne moze izvrsiti test bundle |
| `SpotLinkApp.swift` | Iskljucen iz library targeta zbog `@main` konflikta; samo za app target |

### Sledeci koraci

1. **XcodeGen projekat** – Kreirati `project.yml` za generisanje `.xcodeproj` fajla
2. **TestFlight distribucija** – Definisati `Bundle ID`, provisioning profile i CI/CD pipeline
3. **Refresh token tok** – Implementirati auto-obnavljanje JWT pre isteka (5 min pre)
4. **Mapa lokacija** – Dodat Mapbox prikaz sa `MapKit` fallback-om; sledeci korak je produkciono upravljanje tokenom i dublji UX polish
5. **Placanja** – Integrisati `PaymentService` sa Stripe iOS SDK
6. **Podrska za iPad** – Prilagoditi layout za vece ekrane
7. **Lokalizacija** – Dodati srpske i engleske `Localizable.strings` fajlove
8. **Linux CI** – Postaviti GitHub Actions sa ubuntu-latest za puno izvrsavanje testova

---

## 8. Zavisnosti

### iOS (SPM)

- `mapbox-maps-ios` je dodat za iOS mapu.
- `MapKit` ostaje fallback kada javni Mapbox token nije dostupan ili kada build ne ukljucuje Mapbox.

### Backend (Maven)

```xml
<!-- JWT -->
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>0.12.6</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
```

---

## 9. Verifikacija

### Backend (Spring Boot)

```bash
cd apps/backend
mvn verify
# Ocekivani izlaz: BUILD SUCCESS, 4 tests passed
```

### iOS (SPM)

```bash
cd apps/ios/SpotLink
swift build
# Ocekivani izlaz: Build complete!

swift test
# Ocekivani izlaz: Swift Testing suite pass
```

### iOS (Xcode – zahteva pun Xcode.app)

```bash
xcodebuild -list -project apps/ios/SpotLink.xcodeproj

xcodebuild build \
  -project apps/ios/SpotLink.xcodeproj \
  -scheme SpotLink \
  -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.1' \
  CODE_SIGN_IDENTITY="-" CODE_SIGNING_REQUIRED=NO
```

### Frontend (Angular)

```bash
cd apps/frontend
npm test -- --watch=false
# Ocekivani izlaz: Executed N tests, SUCCESS
```

---

## 10. Xcode app target – struktura i status (2026-04-22)

### Lokacija

```
apps/ios/
├── SpotLink.xcodeproj/                  # Xcode projekat
│   ├── project.pbxproj                  # pbxproj (objectVersion = 60, Xcode 14+ format)
│   └── xcshareddata/xcschemes/
│       └── SpotLink.xcscheme            # Shared shema (build + test + run + archive)
├── SpotLink/                            # SPM paket (nepromenjen)
├── Resources/                           # App target resursi
│   ├── Assets.xcassets/                 # AppIcon, AccentColor, Background
│   ├── Info.plist                       # App metadata (SPOTLINK_ENV resolvuje iz build settings)
│   └── PrivacyInfo.xcprivacy            # Manifest privatnosti (placeholder)
├── SpotLink.entitlements                # Entitlements (push, deep link – zakomentarisano)
├── SpotLinkTests/                       # Xcode unit test target (XCTest)
│   └── LaunchTests.swift
└── SpotLinkUITests/                     # Xcode UI test target (XCUITest)
    └── SpotLinkUITests.swift
```

### Bundle metadata

| Polje | Vrednost |
|-------|---------|
| Bundle ID | `com.spotlink.app` |
| Display Name | SpotLink |
| Min iOS | 17.0 |
| Ciljni uredjaji | iPhone + iPad (1,2) |
| Verzija | 1.0.0 (build 1) |
| Swift verzija (app target) | 5.0 |
| Swift verzija (library) | 6.0 (SPM) |
| SPOTLINK_ENV Debug | `local` |
| SPOTLINK_ENV Release | `production` |

### App target dizajn

Xcode app target:
- Kompajlira samo `SpotLinkApp.swift` (`@main` entry point, iskljucen iz SPM library targeta)
- Linkuje `SpotLink` biblioteku putem lokalnog SPM package reference-a (`XCLocalSwiftPackageReference`)
- Svi Core, Features i Shared moduli dolaze iz biblioteke – nema duplikacije koda

### Assets

| Asset | Status |
|-------|--------|
| `AppIcon.appiconset` | Placeholder – nema stvarne slike; struktura ispravna |
| `AccentColor.colorset` | Definisan (plava nijansa, light/dark) |
| `Background.colorset` | Definisan (bela/crna, light/dark) |

### Privatnost i entitlements

| Stavka | Status |
|--------|--------|
| `PrivacyInfo.xcprivacy` | Kreiran; UserDefaults CA92.1 deklarisan; ostalo PLACEHOLDER |
| `SpotLink.entitlements` | Kreiran; APNs/Associated Domains/Apple Pay zakomentarisano |
| `NSLocationWhenInUseUsageDescription` | U Info.plist |

### Popravke u ovoj fazi

| Fajl | Promena |
|------|---------|
| `App/SpotLinkApp.swift` | Uklonjena duplikacija `AppEnvironmentKey` i `EnvironmentValues` ekstenzije (vec definisane u `AppEnvironment.swift`) |

### Ogranicenja i sledeci koraci

| Oblast | Status |
|--------|--------|
| `xcodebuild` | Nije moguce izvrsiti lokalno (samo CLT, nije pun Xcode.app) |
| App icon | Placeholder – potrebna stvarna grafika |
| `DEVELOPMENT_TEAM` | Prazno – podesiti pre TestFlight |
| APNs entitlement | Zakomentarisan – aktivirati nakon konfiguracije u Developer portalu |
| `PrivacyInfo.xcprivacy` | Placeholder – finalizovati pre App Store slanja |
| Refresh token | Nije implementiran – po isteku JWT korisnik se odjavlja |
| MapKit / LocationsView | Nije implementiran – placeholder tab |
| Crash reporting | Nije integrisano |

### Preporuceni sledeci zadaci

1. **Instalirati Xcode.app** na razvojnoj masini i izvrsiti `xcodebuild -list` i `xcodebuild build` verifikaciju
2. **Kreirati App ID** `com.spotlink.app` u Apple Developer portalu i podesiti `DEVELOPMENT_TEAM`
3. **Aktivirati APNs entitlement** i implementirati `PushNotificationManager` integraciju sa bekendom
4. **Implementirati LocationsView** sa MapKit i `NSLocationWhenInUseUsageDescription` tokom
5. **Kreirati app icon** za sve potrebne velicine (1024x1024 izvorna grafika)
6. **Finalizovati PrivacyInfo.xcprivacy** na osnovu stvarne upotrebe API-ja
7. **Podesiti CI** (GitHub Actions) sa ubuntu-latest za SPM testove i macOS runner za Xcode build
