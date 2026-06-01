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
│   ├── Info.plist                   # Metadata aplikacije
│   └── PrivacyInfo.xcprivacy        # Manifest privatnosti (videti napomenu)
│
├── SpotLink.entitlements            # Mogucnosti (push, deep link – placeholder)
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
```

---

## Sheme

| Shema | Namena |
|-------|--------|
| `SpotLinkApp` | Kanonska CI/test/build shema za app target. Ne postavlja `SPOTLINK_ENV` ili local-device override. |
| `SpotLinkLocalDevice` | Dev-only shema za pokretanje na fizickom uredjaju prema backend-u na Mac-u. |

`SpotLinkApp` je jedina shema koju treba koristiti u CI i release-gate komandama. Swift Package proizvod ostaje `SpotLink`.

## Konfiguracija okruzenja

| Konfiguracija | SPOTLINK_ENV | API URL |
|--------------|--------------|---------|
| Debug        | local        | http://localhost:8080/api |
| Debug device | localDevice  | http://192.168.1.151:8080/api |
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

---

## Bundle metadata

| Polje | Vrednost |
|-------|---------|
| Bundle ID | com.spotlink.app |
| Display Name | SpotLink |
| Min iOS | 17.0 |
| Ciljni uredjaji | iPhone + iPad |
| Verzija | 1.0.0 (build 1) |

---

## Privatnost i entitlements

- **PrivacyInfo.xcprivacy**: placeholder, deklarise UserDefaults pristup (CA92.1). Mora se finalizovati pre App Store slanja.
- **SpotLink.entitlements**: zakomentarisane mogucnosti (APNs, Associated Domains, Apple Pay). Aktivirati tek nakon konfiguracije u Apple Developer portalu.

---

## Poznata ogranicenja

- `swift test` koristi Swift Testing izlaz koji prvo prikazuje prazan XCTest summary, pa zatim stvarni Swift Testing run summary.
- `DEVELOPMENT_TEAM` je prazno – potrebno podesiti pre TestFlight slanja.
- Week 1 release gate validira samo unsigned Release build (`CODE_SIGNING_ALLOWED=NO`); signed archive i TestFlight upload su human-controlled koraci za kasnije.
- App icon je placeholder (nema stvarne slike).
