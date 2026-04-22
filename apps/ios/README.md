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
├── SpotLink/                        # Swift Package (biblioteka + CLI test runner)
│   ├── Package.swift
│   ├── Sources/SpotLink/            # Core, Features, Shared, App
│   ├── Sources/SpotLinkTestRunner/  # CLI Swift Testing runner
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
swift run SpotLinkTestRunner
```

`SpotLinkTestRunner` je standardni CLI test entrypoint za ovaj repozitorijum. Na ovoj kombinaciji SwiftPM + Command Line Tools, `swift test` gradi Swift Testing bundle ali ne izvrsava suite-ove pouzdano, pa runner eksplicitno poziva Swift Testing entry point i ispisuje normalan test run summary.

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

- Xcode.app nije dostupan na razvojnoj masini – `xcodebuild` nije moguce izvrsiti lokalno.
- `swift test` i dalje nije pouzdan CLI runner na ovoj kombinaciji Command Line Tools + SwiftPM; koristi se `swift run SpotLinkTestRunner`.
- `DEVELOPMENT_TEAM` je prazno – potrebno podesiti pre TestFlight slanja.
- App icon je placeholder (nema stvarne slike).
