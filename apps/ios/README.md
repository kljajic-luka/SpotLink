# SpotLink iOS

## Pregled

Native iOS aplikacija za SpotLink parking platformu, izgradjenja na SwiftUI + MVVM arhitekturi.

---

## Struktura projekta

```
apps/ios/
├── SpotLink.xcodeproj/              # Xcode projekat (app target, test targeti, sheme)
│   ├── project.pbxproj
│   └── xcshareddata/xcschemes/
│       ├── SpotLinkApp.xcscheme
│       ├── SpotLinkStaging.xcscheme
│       └── SpotLinkLocalDevice.xcscheme
│
├── SpotLink/                        # Swift Package (biblioteka + test support)
│   ├── Package.swift
│   ├── Sources/SpotLink/            # Core, Features, Shared, App
│   ├── Sources/SpotLinkTestRunner/  # Legacy ostatak, nije aktivan target
│   └── TestSupport/SpotLinkTestSupport/
│
├── Resources/                       # App target resursi
│   ├── Assets.xcassets/             # AppIcon, AccentColor, Background
│   ├── Info.plist                   # Metadata i legal/support config kljucevi
│   └── PrivacyInfo.xcprivacy        # Manifest privatnosti
│
├── ExportOptions/                   # App Store Connect export templates
├── SpotLink.entitlements            # Intentionally empty until Apple capabilities are provisioned
├── SpotLinkTests/                   # Xcode unit test target (XCTest)
│   └── LaunchTests.swift
└── SpotLinkUITests/                 # Xcode UI test target (XCUITest)
    └── SpotLinkUITests.swift
```

---

## Pokretanje

### SPM build i testovi (bez Xcode.app)

```bash
cd apps/ios/SpotLink
swift build
swift test
```

`swift test` je standardni CLI test entrypoint za iOS paket. Root komanda `npm run test:ios` delegira na isti `swift test --package-path apps/ios/SpotLink` tok.

### Xcode projekat (zahteva pun Xcode.app)

```bash
# Lista targeta i shema
xcodebuild -list -project apps/ios/SpotLink.xcodeproj

# Build za iOS Simulator
xcodebuild build \
  -project apps/ios/SpotLink.xcodeproj \
  -scheme SpotLinkApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  CODE_SIGNING_ALLOWED=NO

# Unit testovi
xcodebuild test \
  -project apps/ios/SpotLink.xcodeproj \
  -scheme SpotLinkApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  CODE_SIGNING_ALLOWED=NO

# Unsigned Release build validation, bez Apple signing kredencijala
xcodebuild build \
  -project apps/ios/SpotLink.xcodeproj \
  -scheme SpotLinkApp \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  CODE_SIGNING_ALLOWED=NO

# Unsigned Staging build validation, bez Apple signing kredencijala
xcodebuild build \
  -project apps/ios/SpotLink.xcodeproj \
  -scheme SpotLinkStaging \
  -configuration Staging \
  -destination 'generic/platform=iOS' \
  CODE_SIGNING_ALLOWED=NO

# Signed archive/export config validation, bez Apple signing kredencijala
make validate-ios-signed-config

# Privacy manifest / entitlements / Info.plist lint
make validate-ios-privacy-config
```

---

## Sheme

| Shema | Namena |
|-------|--------|
| `SpotLinkApp` | Kanonska CI/test/build shema za app target. Ne postavlja `SPOTLINK_ENV` ili local-device override. |
| `SpotLinkStaging` | Interna staging shema. Koristi `Staging` build konfiguraciju, `SPOTLINK_ENV=staging` i bundle ID `com.spotlink.app.staging`. |
| `SpotLinkLocalDevice` | Dev-only shema za pokretanje na fizickom uredjaju prema backend-u na Mac-u. |

`SpotLinkApp` ostaje kanonska shema za CI Xcode testove i Release unsigned build. `SpotLinkStaging` se koristi za Staging build, archive i export putanju. Swift Package proizvod ostaje `SpotLink`.

## Konfiguracija okruzenja

| Konfiguracija | SPOTLINK_ENV | API URL |
|--------------|--------------|---------|
| Debug        | local        | http://localhost:8080/api |
| Debug device | localDevice  | http://192.168.1.151:8080/api |
| Staging      | staging      | https://api-staging.spotlink.app/api |
| Release      | production   | https://api.spotlink.app/api |

Za override u Debug build-u: postaviti `SPOTLINK_ENV` env varijablu u Xcode scheme argumentima.
Za testiranje na fizickom iPhone-u preko backend-a na Mac-u, koristiti `SpotLinkLocalDevice`, koji vec postavlja:

```text
SPOTLINK_ENV=localDevice
SPOTLINK_LOCAL_DEVICE_API_BASE_URL=http://192.168.1.151:8080/api
```

Ako se Mac IP promeni, azurirati:

```text
SPOTLINK_LOCAL_DEVICE_API_BASE_URL=http://<MAC_IP>:8080/api
```

i proveriti sa iPhone Safari-jem da `http://<MAC_IP>:8080/api/health` vraca `UP`.

### Mapa i token

- `SPOTLINK_MAPBOX_PUBLIC_TOKEN` se cita u runtime-u iz `Info.plist` / build settings konfiguracije.
- Ako token nije dostupan, aplikacija koristi `MapKit` fallback bez rupe u shell-u.
- Produkcioni token treba obezbediti kroz lokalni xcconfig ili CI secret, ne hardkodirati ga direktno u repozitorijum.

### Staging backend readiness

`SpotLinkStaging` pokazuje na `https://api-staging.spotlink.app/api`. Pre internal TestFlight staging builda, backend deploy mora proci:

```bash
curl -fsS https://api-staging.spotlink.app/api/health
curl -fsS https://api-staging.spotlink.app/api/actuator/health/liveness
curl -fsS https://api-staging.spotlink.app/api/actuator/health/readiness
```

`/actuator/health/readiness` je DB-backed readiness provera. Ako staging baza, JWT secret, CORS ili cookie konfiguracija nisu ispravni, backend treba da padne na startu umesto da iOS build pokazuje lazno spreman staging.

### Placanja

iOS ne pretpostavlja da su online placanja dostupna. `ReservationBookingViewModel` prvo cita backend capabilities:

```text
GET /payments/capabilities
```

Ako backend vrati `onlinePaymentsEnabled=false`, aplikacija uklanja `.online` iz dostupnih rezima placanja i prelazi na placanje po dolasku ako ga partner resurs podrzava. `GET /payments/methods` se zove samo kada backend kaze da online autorizacija moze da se koristi.

Trenutno stanje:

- Dev/test/staging mogu koristiti mock provider samo kada je backend eksplicitno konfigurisan za to.
- Produkcija ne sme prikazivati mock kartice niti mock payment capability.
- iOS ima servisne metode za capabilities, create/confirm i cancel intent, ali nema real PSP SDK, Apple Pay, SCA deep-link povratak ili settlement UI.

Za real PSP jos su potrebni izbor provajdera, credentials u secret store-u, webhook signature verifikacija, SCA/deep-link povratak, capture/refund reconciliation i izvestaji za poravnanje.

### Push obavestenja

Trenutni iOS sloj je spreman za lifecycle APNs tokena, a backend ima APNs-ready delivery scaffold. Aplikacija i dalje ne salje prava APNs obavestenja dok Apple capability, provisioning i credentials ne budu obezbedjeni:

- `PushNotificationManager` cuva poslednji APNs token lokalno dovoljno da ga moze odjaviti posle restarta aplikacije.
- Token se uploaduje kada APNs vrati token, posle uspesne prijave i posle restore-a autentifikovane sesije.
- Odjava pokusava backend unregister pre lokalnog brisanja sesije; lokalna odjava se ipak zavrsava ako unregister/revoke poziv ne uspe.
- Uspesan unregister brise lokalno zapamcen token state.
- Profil prikazuje native SwiftUI toggle kontrole za obavestenja o rezervacijama, placanjima, odgovorima podrske i marketing saglasnost. Promene se cuvaju kroz `PATCH /users/me/profile` kao parcijalni `preferences` objekat.
- Backend unregister je ownership-safe: missing/foreign token ne otkriva vlasnistvo.
- Backend delivery radi posle commit-a notifikacije, postuje server-side preference za rezervacije, placanja i podrsku, preskace inactive tokene, deaktivira APNs tokene koje provider prijavi kao trajno nevazece i meri attempted/succeeded/failed/invalid-token/disabled/preference-skipped ishode.
- Marketing saglasnost se ne koristi za transakcione push notifikacije. In-app inbox redovi ostaju sacuvani i kada se push delivery preskoci zbog preference.
- Backend logovi smeju imati samo stabilan hash tokena, ne raw APNs token, bearer token, APNs key material ili payload body.

Pre pravog APNs rada jos uvek je potrebno:

- Omoguciti Push Notifications capability za staging/release bundle ID-jeve u Apple Developer portalu.
- Ukljuciti odgovarajuci entitlement u Xcode projektu bez lomljenja unsigned gate-a.
- Obezbediti APNs key/certificate kroz provider secret store, ne kroz repo, i podesiti `PUSH_DELIVERY_ENABLED=true`, `PUSH_PROVIDER=apns`, `APNS_ENVIRONMENT`, `APNS_BUNDLE_ID`, `APNS_TEAM_ID`, `APNS_KEY_ID` i APNs private key vrednost/path u runtime okruzenju.
- Izvrsiti fizicki device smoke test za sandbox/production delivery; simulator i unsigned gate ne dokazuju APNs isporuku.

---

## Bundle metadata

| Polje | Vrednost |
|-------|---------|
| Bundle ID | com.spotlink.app |
| Staging Bundle ID | com.spotlink.app.staging |
| Display Name | SpotLink |
| Min iOS | 17.0 |
| Ciljni uredjaji | iPhone + iPad |
| Verzija | 1.0.0 (build 1) |

## Signed Internal TestFlight archive/export

Signed archive/export putanja je pripremljena, ali nije deo `make release-gate` i ne moze proci bez Apple kredencijala. Repozitorijum ne sadrzi signing secrets, provisioning profile fajlove, App Store Connect kljuceve ili personalni `DEVELOPMENT_TEAM`.

Potrebno je lokalno/human-controlled podesavanje:

- Apple Developer team ID za SpotLink.
- `Apple Distribution` sertifikat sa private key u login keychain-u.
- App ID i App Store Connect app record za `com.spotlink.app.staging`.
- App ID i App Store Connect app record za `com.spotlink.app`.
- App Store Connect provisioning profile instaliran lokalno za svaki bundle ID.
- Mapbox downloads token u `~/.netrc` ako Xcode mora da resolve-uje Mapbox binary dependency.

Repo validacija bez kredencijala:

```bash
make validate-ios-signed-config
```

Staging signed archive/export za internal TestFlight pripremu:

```bash
SPOTLINK_APPLE_TEAM_ID=<TEAM_ID> \
SPOTLINK_STAGING_PROFILE_SPECIFIER="<App Store profile for com.spotlink.app.staging>" \
make export-ios-staging-testflight
```

Release signed archive/export:

```bash
SPOTLINK_APPLE_TEAM_ID=<TEAM_ID> \
SPOTLINK_RELEASE_PROFILE_SPECIFIER="<App Store profile for com.spotlink.app>" \
make export-ios-release-testflight
```

Output lokacije su:

```text
build/ios/archives/SpotLinkStaging.xcarchive
build/ios/archives/SpotLinkRelease.xcarchive
build/ios/exports/staging/
build/ios/exports/release/
```

`export-ios-*-testflight` proizvodi signed IPA za human-controlled upload kroz Xcode Organizer, Transporter ili App Store Connect tooling. Ovaj repo jos uvek ne radi automatski upload na TestFlight.

## Release gate i staging provera

Root `make release-gate` pokrece backend, frontend, SwiftPM, Xcode simulator testove preko `SpotLinkApp`, unsigned Release build i unsigned Staging build. Za izolovanu staging proveru:

```bash
make build-ios-staging-unsigned
```

Obe provere su unsigned (`CODE_SIGNING_ALLOWED=NO`). Signed archive/export je odvojena human-controlled putanja opisana iznad.

---

## Privatnost i entitlements

- **PrivacyInfo.xcprivacy**: deklarise `NSPrivacyTracking=false`, app-owned UserDefaults required-reason API usage (`CA92.1`), and current SpotLink-collected data classes for account/contact, location/search, vehicle/license plate, reservation/payment-attempt metadata, support/account deletion tickets, analytics, APNs token lifecycle, and diagnostics/request IDs.
- **Info.plist legal/support keys**: `SPOTLINK_PRIVACY_POLICY_URL`, `SPOTLINK_TERMS_URL`, `SPOTLINK_SUPPORT_URL`, `SPOTLINK_SUPPORT_EMAIL`, and `SPOTLINK_ACCOUNT_DELETION_URL` are resolved from build settings or runtime env overrides. Defaults point at owner-owned `spotlink.app` destinations and must serve real policy/support content before signed TestFlight/App Review.
- **SpotLink.entitlements**: intentionally empty. APNs, Associated Domains, and Apple Pay remain disabled until the Apple Developer portal, provisioning profiles, APNs/merchant credentials, and product/legal review are ready.

Validacija:

```bash
make validate-ios-privacy-config
```

Registration links to Terms and Privacy Policy. Profile exposes Privacy Policy, Terms, support URL/email, account-deletion information, and the destructive account deletion request action. Backend support admins can process approved account-deletion tickets through the admin API; the iOS app keeps the request flow and signs out with Serbian messaging if the backend later rejects the session because the account is no longer active. Legal/privacy owner retention policy and final public process wording remain operational/legal work.

---

## Poznata ogranicenja

- `swift test` koristi Swift Testing izlaz koji prvo prikazuje prazan XCTest summary, pa zatim stvarni Swift Testing run summary.
- `Staging` i `Release` konfiguracije ne postavljaju tim za potpisivanje u projektu; signed Makefile targeti ga primaju kroz `SPOTLINK_APPLE_TEAM_ID`.
- Release gate validira unsigned Release i Staging buildove (`CODE_SIGNING_ALLOWED=NO`); signed archive/export zahteva Apple Developer sertifikat i provisioning profile.
- Online payment UI se vodi backend capabilities odgovorom, ali real PSP provider/credentials/SCA/capture/refund/webhook/reconciliation nisu implementirani.
- Push token lifecycle i backend APNs delivery scaffold su spremni, ali APNs credentials, push entitlement i fizicka isporuka jos nisu implementirani/verifikovani.
- Legal/support linkovi su tehnicki povezani, ali stvarni Privacy Policy, Terms, support stranice i App Store Connect privacy answers ostaju owner/legal odgovornost.
