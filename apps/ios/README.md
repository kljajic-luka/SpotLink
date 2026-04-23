# SpotLink iOS

## Pregled

Native iOS aplikacija za SpotLink parking platformu, izgradjenja na SwiftUI + MVVM arhitekturi.

---

## Struktura projekta

```
apps/ios/
├── SpotLink.xcodeproj/              # Xcode projekat (app target, test targeti, sheme)
│   ├── project.pbxproj
│   └── xcshareddata/xcschemes/SpotLink.xcscheme
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
  -scheme SpotLink \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  CODE_SIGN_IDENTITY="-" \
  CODE_SIGNING_REQUIRED=NO

# Unit testovi
xcodebuild test \
  -project apps/ios/SpotLink.xcodeproj \
  -scheme SpotLink \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

---

## Konfiguracija okruzenja

| Konfiguracija | SPOTLINK_ENV | API URL |
|--------------|--------------|---------|
| Debug        | local        | http://localhost:8080/api |
| Release      | production   | https://api.spotlink.app/api |

Za override u Debug build-u: postaviti `SPOTLINK_ENV` env varijablu u Xcode scheme argumentima.

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
- App icon je placeholder (nema stvarne slike).
