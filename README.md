# SpotLink

SpotLink is a parking reservation marketplace foundation. It provides the shared backend, web, and native iOS base that future SpotLink MVP workflows will build on.

The current repository is intentionally foundation-first: it establishes architecture, contracts, security defaults, persistence, native mobile structure, and verification gates without overbuilding product workflows before the domain is ready.

![SpotLink foundation dashboard](docs/assets/spotlink-foundation.png)

<img src="docs/assets/spotlink-foundation-mobile.png" alt="SpotLink mobile foundation dashboard" width="320" />

## Current State

| Area | Status | Notes |
| --- | --- | --- |
| Backend | Foundation implemented | Java 21, Spring Boot 3.5, Maven, Flyway, JPA, Security, Actuator, OpenAPI |
| Frontend | Foundation hardened | Angular 20, strict TypeScript, foundation services, API contracts, targeted tests |
| iOS | Native foundation implemented | SwiftUI, Swift Package, Xcode project, app resources, test runner |
| API contracts | Documented | Mobile contract, JSON fixtures, OpenAPI draft, Swift DTO alignment guide |
| Product workflows | Not complete | MVP flows such as map search, quote, reserve, and payment confirmation are next |

## Product Model

SpotLink uses parking-specific terminology throughout the codebase:

| Term | Meaning |
| --- | --- |
| `customer` | Person searching for and reserving parking |
| `operator` | Person or team managing parking locations and resources |
| `reservation` | Booked parking time window |
| `parking location` | Physical place where parking inventory exists |
| `parking resource` | Reservable parking inventory at a location |
| `vehicle` | Customer vehicle used for fit, access, and compatibility checks |

Car-rental workflows are intentionally out of scope. The foundation does not include driver-license verification, rental agreements, damage claims, no-show flows, payout ledgers, or rent-a-car compliance logic.

## Architecture

```text
.
|-- apps/
|   |-- backend/                  # Spring Boot API foundation
|   |-- frontend/                 # Angular web foundation
|   `-- ios/                      # Native SwiftUI iOS foundation
|-- docs/
|   |-- api/                      # Frontend API draft and contract notes
|   |-- ios-enterprise-readiness/ # iOS product, QA, security, and launch readiness docs
|   `-- mobile-api-contract/      # Authoritative mobile API contract and fixtures
|-- SPOTLINK_BACKEND_FOUNDATION_MIGRATION.md
|-- SPOTLINK_FRONTEND_FOUNDATION_HARDENING.md
|-- SPOTLINK_FOUNDATION_MIGRATION.md
|-- SPOTLINK_IOS_FOUNDATION_MIGRATION.md
|-- .env.example
|-- package.json
`-- README.md
```

## Backend

Location: [apps/backend](apps/backend)

The backend is a production-minded Spring Boot foundation with:

- Cookie/session auth for web clients.
- Bearer-token mobile auth with refresh-token lifecycle.
- Role model: `CUSTOMER`, `OPERATOR`, `SUPPORT`, `ADMIN`.
- `/api` routes plus `/api/v1` mobile compatibility aliases.
- Flyway migrations for a clean SpotLink schema.
- Global error handling, validation mapping, request correlation IDs, pagination helpers, CORS, CSRF, health, actuator, and OpenAPI.
- Foundation modules for users, vehicles, locations, reservations, payments, support, notifications, operator, admin, audit, analytics, and idempotency.
- Push device-token lifecycle endpoints for register/reactivate and ownership-safe unregister/deactivate; delivery remains mock-only.

Default local persistence uses H2. PostgreSQL is supported through environment configuration.

## Frontend

Location: [apps/frontend](apps/frontend)

The frontend is an Angular foundation with:

- Strict TypeScript.
- Modular `foundation` services and models.
- Auth, profile, vehicles, locations, reservations, payments, support, notifications, operator, admin, and analytics boundaries.
- HTTP client contracts, retry handling, credential handling, API error types, and idempotency helpers.
- Design-system primitives and shared loading, empty, error, and image components.
- Focused Jasmine/Karma tests for the hardened foundation layer.

## iOS

Location: [apps/ios](apps/ios)

The iOS foundation is native Swift/SwiftUI, not a webview shell.

It includes:

- [SpotLink.xcodeproj](apps/ios/SpotLink.xcodeproj)
- Swift Package foundation under [apps/ios/SpotLink](apps/ios/SpotLink)
- Session-aware app shell.
- Typed services and models for the same foundation domains as the backend/frontend.
- Keychain-ready session storage.
- Request correlation, analytics, push notification, location, and API client foundations.
- App resources: Info.plist, privacy manifest, asset catalog, and intentionally empty entitlements file.
- Native SwiftUI customer MVP slice za pretragu, rezervaciju, potvrdu i podrsku.
- Shared Xcode schemes for canonical app verification (`SpotLinkApp`), unsigned staging validation (`SpotLinkStaging`), and physical-device local backend development (`SpotLinkLocalDevice`).

Open this project in Xcode:

```bash
open apps/ios/SpotLink.xcodeproj
```

Full simulator build/test requires Xcode.app and accepted Apple SDK licenses.

## Prerequisites

| Tool | Version / Requirement |
| --- | --- |
| Node.js | `^20.19.0`, `^22.12.0`, or `^24.0.0` (LTS only; v25+ not supported) |
| npm | `>=10` |
| Java | 21 |
| Maven | 3.9+ preporuceno |
| Swift | Swift 6 toolchain |
| Xcode | Potrebno za iOS simulator buildove i TestFlight pripremu |
| PostgreSQL | Opciono za lokalni razvoj; H2 je podrazumevani fallback |

Pinned Node verzija je u `.nvmrc` (`22`). Ako koristis nvm:

```bash
nvm install && nvm use
```

If Apple tooling reports a license error, accept the local license before running iOS commands:

```bash
sudo xcodebuild -license
```

## Configuration

Start from the checked-in environment template:

```bash
cp .env.example .env
```

Do not commit `.env` or real secrets.

Important backend variables:

| Variable | Purpose |
| --- | --- |
| `DATABASE_URL` | JDBC URL for H2/PostgreSQL |
| `DATABASE_USERNAME` | Database username |
| `DATABASE_PASSWORD` | Database password |
| `JWT_SECRET` | 32+ byte secret string for mobile bearer auth |
| `JWT_ACCESS_TOKEN_TTL_MINUTES` | Mobile access-token TTL |
| `JWT_REFRESH_TOKEN_TTL_DAYS` | Mobile refresh-token TTL |
| `CORS_ORIGINS` | Allowed web origins |
| `COOKIE_SECURE` | Secure cookie flag |
| `PAYMENT_PROVIDER` | Active payment provider key (`mock` for explicit non-production mock, `none` until real PSP is configured) |
| `ONLINE_PAYMENTS_ENABLED` | Enables online reservation/payment-intent path only when the configured provider is available |
| `MOCK_PAYMENT_ENABLED` | Enables/disables mock payment provider |

Staging and production profiles reject the built-in development JWT secret.
Production profiles (`prod` or `production`) also reject `MOCK_PAYMENT_ENABLED=true`; mock payments are allowed only for dev/test/staging style profiles, and staging requires the setting to be explicit. Production should keep `PAYMENT_PROVIDER=none` and `ONLINE_PAYMENTS_ENABLED=false` until a real PSP provider is implemented and credentialed.

Staging/production runtime guard:

- `SPRING_PROFILES_ACTIVE=staging`, `prod`, or `production` fails startup if `DATABASE_URL` still points at H2, if `DATABASE_USERNAME=sa`, or if `DATABASE_PASSWORD` is blank.
- `JWT_SECRET` must be a real 32+ byte value and cannot be the dev/default or example placeholder.
- `CORS_ORIGINS` must be explicit `https://...` origins; wildcard and localhost origins are rejected.
- `COOKIE_SECURE=true` is required.
- Staging may use mock payment only when `PAYMENT_PROVIDER=mock`, `ONLINE_PAYMENTS_ENABLED=true`, and `MOCK_PAYMENT_ENABLED=true` are explicitly set. Production must set `MOCK_PAYMENT_ENABLED=false` and must not expose mock methods.

Placeholder-only runtime examples:

- [apps/backend/env/staging.env.example](apps/backend/env/staging.env.example)
- [apps/backend/env/production.env.example](apps/backend/env/production.env.example)

Vazno za iOS mapu:

| Variable | Purpose |
| --- | --- |
| `SPOTLINK_MAPBOX_PUBLIC_TOKEN` | Javni Mapbox token koji se ucitava u runtime-u za iOS mapu |

- iOS app target koristi `SPOTLINK_MAPBOX_PUBLIC_TOKEN` umesto oslanjanja na hardkodovan `MBXAccessToken`.
- Ako token nije dostupan, aplikacija automatski pada nazad na `MapKit` fallback prikaz.
- Produkcioni token treba uneti kroz build settings, xcconfig ili CI secret, ne direktno u repozitorijum.

## Local Development

### Quickstart (H2 in-memory, no Postgres required)

```bash
# 1. Podesavanje (jednom)
make env          # kreiranje .env iz .env.example
make install      # npm install za frontend

# 2. Pokretanje
make dev          # backend (port 8080) + frontend (port 4200) paralelno
# ili pojedinacno:
make backend
make frontend
```

Backend automatski primenjuje Flyway migracije i (`dev` profil) sidi demo naloge:

| Nalog | Email | Lozinka | Uloge |
| --- | --- | --- | --- |
| Admin | `admin@spotlink.rs` | `Demo1234!` | ADMIN, CUSTOMER |
| Operater | `operator@spotlink.rs` | `Demo1234!` | OPERATOR, CUSTOMER |
| Korisnik | `korisnik@spotlink.rs` | `Demo1234!` | CUSTOMER |

Demo lokacija: `Parking Trg Republike – Demo`, Beograd (44.8175, 20.4562)
- Mesto A-01 – samo online placanje
- Mesto B-01 – placanje po dolasku (pay-on-arrival)

### Health check

```bash
curl http://localhost:8080/api/health
# {"status":"UP"}

curl http://localhost:8080/api/actuator/health
curl http://localhost:8080/api/actuator/health/liveness
curl http://localhost:8080/api/actuator/health/readiness
```

For staging deploy smoke checks, replace the host:

```bash
curl -fsS https://api-staging.spotlink.app/api/health
curl -fsS https://api-staging.spotlink.app/api/actuator/health/liveness
curl -fsS https://api-staging.spotlink.app/api/actuator/health/readiness
```

`/api/health` is the simple public health response. `/api/actuator/health/readiness` includes the DB-backed readiness group and is the deployment check that should fail if the staging database is unavailable.

### OpenAPI (Swagger UI)

```
http://localhost:8080/api/swagger-ui
```

## Installation

Install frontend dependencies:

```bash
npm install --prefix apps/frontend
# or
make install
```

Maven and SwiftPM resolve backend/iOS dependencies through their own toolchains.

## Common Commands

`make help` prikazuje sve dostupne Makefile naredbe.

| npm/make naredba | Svrha |
| --- | --- |
| `make dev` | Backend + frontend paralelno |
| `make backend` | Spring Boot backend (dev profil, H2) |
| `make frontend` | Angular dev server |
| `make test` | Backend + frontend testovi |
| `make test-backend` | Maven testovi |
| `make validate-backend-runtime-config` | Focused backend staging/prod runtime guard, health, and safe logging tests |
| `make build-backend-image` | Provider-neutral backend Docker image build, requires Docker |
| `make test-frontend` | Angular ChromeHeadless testovi (CI=1) |
| `make test-ios` | iOS Swift testovi |
| `make test-ios-xcode` | Xcode simulator testovi preko `SpotLinkApp` sheme |
| `make build` | CI Angular produkcioni build |
| `make build-backend` | Maven package (preskoci testove) |
| `make build-ios-xcode` | Xcode simulator build preko `SpotLinkApp` sheme |
| `make build-ios-release-unsigned` | Unsigned iOS Release build validation |
| `make build-ios-staging-unsigned` | Unsigned iOS Staging build validation preko `SpotLinkStaging` sheme |
| `make validate-ios-privacy-config` | Lint iOS PrivacyInfo, entitlements, and Info.plist |
| `make validate-ios-signed-config` | Lint i build-setting provera signed archive/export konfiguracije bez Apple kredencijala |
| `make export-ios-staging-testflight` | Signed Staging IPA export za human-controlled TestFlight upload |
| `make export-ios-release-testflight` | Signed Release IPA export za human-controlled TestFlight upload |
| `make release-gate` | Backend, frontend, SwiftPM, Xcode simulator testovi i unsigned Release/Staging iOS buildovi |
| `npm run start` | Angular dev server |
| `npm run start:backend` | Spring Boot (dev profil) |
| `npm run build` | Build Angular frontend |
| `npm run build:backend` | Package backend jar |
| `npm run build:ios` | Build iOS Swift package |
| `npm run build:dev` | Build Angular development bundle |
| `npm run test` | Frontend testovi (CI=1) |
| `npm run test:backend` | Backend Maven testovi |
| `npm run test:ios` | iOS Swift testovi |

Backend verify:

```bash
mvn -f apps/backend/pom.xml verify
```

Frontend headless test:

```bash
npm --prefix apps/frontend run test -- --watch=false --browsers=ChromeHeadless
```

iOS simulator build, after full Xcode is installed:

```bash
xcodebuild build \
  -project apps/ios/SpotLink.xcodeproj \
  -scheme SpotLinkApp \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

## Release Gate

The engineering release gate validates backend, frontend, SwiftPM, Xcode simulator tests, and unsigned iOS Release/Staging builds. Apple signing, App Store Connect credentials, signed archives, and TestFlight upload remain human-controlled later gates.

Run the complete local gate:

```bash
make release-gate
```

The target runs these checks in order:

```bash
mvn -f apps/backend/pom.xml clean test
npm --prefix apps/frontend run test:ci
npm --prefix apps/frontend run build:ci
plutil -lint apps/ios/Resources/PrivacyInfo.xcprivacy apps/ios/SpotLink.entitlements apps/ios/Resources/Info.plist
swift package clean --package-path apps/ios/SpotLink
swift test --package-path apps/ios/SpotLink
xcodebuild test -project apps/ios/SpotLink.xcodeproj -scheme SpotLinkApp -destination 'platform=iOS Simulator,id=<available simulator>' CODE_SIGNING_ALLOWED=NO
xcodebuild build -project apps/ios/SpotLink.xcodeproj -scheme SpotLinkApp -configuration Release -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO
xcodebuild build -project apps/ios/SpotLink.xcodeproj -scheme SpotLinkStaging -configuration Staging -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO
```

Passing means the engineering release gate is green for backend, frontend, SwiftPM, Xcode simulator tests, and unsigned iOS Release/Staging compilation. A failure means staging/TestFlight prep should not proceed until that command is fixed.

To verify only the staging unsigned build path:

```bash
make build-ios-staging-unsigned
```

Signed Internal TestFlight scaffolding is intentionally separate from the release gate. Validate the checked-in archive/export configuration without Apple credentials:

```bash
make validate-ios-signed-config
```

After a human installs the Apple Distribution certificate, App Store Connect provisioning profiles, and app records for both bundle IDs, run the signed staging export with:

```bash
SPOTLINK_APPLE_TEAM_ID=<TEAM_ID> \
SPOTLINK_STAGING_PROFILE_SPECIFIER="<App Store profile for com.spotlink.app.staging>" \
make export-ios-staging-testflight
```

For the production bundle:

```bash
SPOTLINK_APPLE_TEAM_ID=<TEAM_ID> \
SPOTLINK_RELEASE_PROFILE_SPECIFIER="<App Store profile for com.spotlink.app>" \
make export-ios-release-testflight
```

These targets create signed archives and exported IPA files under `build/ios/`. They do not upload to TestFlight and they fail clearly if the required signing env vars, certificates, or provisioning profiles are missing.

## Backend Deployability

The backend now has provider-neutral image scaffolding:

```bash
make build-backend-image
```

This builds `spotlink-backend:local` from [apps/backend/Dockerfile](apps/backend/Dockerfile). The target is optional and requires Docker locally; CI/runtime validation does not require Docker.

Before staging or production deploys, validate the runtime guard behavior:

```bash
make validate-backend-runtime-config
```

Expected staging environment variables:

| Variable | Staging expectation |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `staging` |
| `DATABASE_URL` | PostgreSQL JDBC URL, never H2 |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Secret-backed staging DB credentials |
| `DATABASE_DRIVER` | `org.postgresql.Driver` |
| `JWT_SECRET` | Secret-backed 32+ byte value |
| `CORS_ORIGINS` | Explicit HTTPS web origin, for example `https://staging.spotlink.app` |
| `COOKIE_SECURE` | `true` |
| `PAYMENT_PROVIDER` | `mock` only while staging deliberately uses mock PSP behavior |
| `ONLINE_PAYMENTS_ENABLED` | `true` for explicit staging mock PSP validation; `false` if staging payments should be hidden |
| `MOCK_PAYMENT_ENABLED` | `true` only while staging still uses the mock PSP |

Expected production differences: `SPRING_PROFILES_ACTIVE=production`, production DB credentials, production HTTPS origin, `PAYMENT_PROVIDER=none`, `ONLINE_PAYMENTS_ENABLED=false`, and `MOCK_PAYMENT_ENABLED=false`.

Startup should fail clearly when these requirements are not met. That is intentional; do not bypass it by switching profiles or weakening the guard.

## Payment Readiness

Online payment authority is backend-driven. Clients must read capabilities before showing or enabling online payment actions:

| Endpoint | Purpose |
| --- | --- |
| `GET /api/payments/capabilities` | Reports whether online payments are enabled, the active provider, mock/test method availability, and supported operations |
| `GET /api/payments/methods` | Returns mock card methods only when the mock provider is explicitly allowed |
| `POST /api/payments/intents` | Creates/authorizes a payment intent only when provider authority allows online payments |
| `POST /api/payments/intents/{id}/confirm` | Confirms an existing authorized/capable intent |
| `POST /api/payments/intents/{id}/cancel` | Cancels/voids an unfinalized intent through the provider contract |

Mobile clients may also use the `/api/v1/payments/...` aliases.

Current modes:

- Dev/test: mock online payments are available by default for internal validation.
- Staging: mock online payments are allowed only when `PAYMENT_PROVIDER=mock`, `ONLINE_PAYMENTS_ENABLED=true`, and `MOCK_PAYMENT_ENABLED=true` are explicitly configured.
- Production: mock payment is rejected at startup, mock card methods are not exposed, and online payments should remain disabled until a real PSP provider is implemented.

The provider abstraction is shaped for future `authorize`, `capture`, `cancel/void`, `refund`, webhook, and reconciliation work. The current admin refund action remains a marker, but records provider-style events so a real provider refund can plug in later.

Remaining PSP work is provider/human owned: select PSP, add credentials through secret storage, implement webhook signature verification, model SCA/deep-link returns, reconcile capture/refund state, and build settlement/reporting workflows. Do not collect or store raw card details.

## Push Notification Readiness

Push delivery is not production APNs yet. The current readiness slice only makes the token lifecycle production-shaped:

- iOS can upload APNs tokens after registration, after login, and after authenticated session restore.
- iOS attempts token unregister before local logout; local logout still proceeds if the network/backend cleanup fails.
- The backend supports authenticated register/reactivate and non-enumerating unregister/deactivate endpoints.
- Missing or foreign tokens return the same no-content unregister response and do not reveal ownership.
- Raw APNs/device tokens are not logged.

Current backend endpoints:

| Endpoint | Purpose |
| --- | --- |
| `POST /api/notifications/device-tokens` | Register or reactivate the current user's device token |
| `POST /api/notifications/device-tokens/unregister` | Deactivate the current user's matching device token without leaking missing/foreign ownership |

Mobile clients may also use `/api/v1/notifications/device-tokens` and `/api/v1/notifications/device-tokens/unregister`.

Remaining APNs setup is provider/human owned: enable the Push Notifications capability in Apple Developer and Xcode entitlements, create/install APNs key or certificate material, provision staging/release bundle IDs, and replace the mock notification provider with a real APNs provider. Do not commit APNs secrets.

## App Store Privacy And Legal Readiness

The iOS privacy manifest is now an engineering declaration rather than a placeholder. It keeps tracking disabled, declares app-owned UserDefaults required-reason API usage, and lists the current SpotLink data classes sent to backend systems: account/contact data, location/search data, vehicle/license-plate data when provided, reservation/payment-attempt metadata, support/account deletion tickets, analytics events, APNs token lifecycle data, and diagnostics/request IDs.

Validate the plist surface locally:

```bash
make validate-ios-privacy-config
```

Legal/support destinations are wired in iOS and frontend config:

| Surface | Default destination |
| --- | --- |
| Privacy Policy | `https://spotlink.app/privacy` |
| Terms | `https://spotlink.app/terms` |
| Support URL | `https://spotlink.app/support` |
| Support email | `support@spotlink.app` |
| Account deletion information | `https://spotlink.app/account-deletion` |

Registration links to Terms and Privacy Policy. Profile exposes privacy, terms, support, support email, account-deletion information, and the destructive account deletion request action. Requests are backed by support tickets, and admins can now process an approved account-deletion ticket through the support-case admin action. Fulfillment marks the user `DELETED`, anonymizes direct profile PII, revokes refresh/password-reset/device-token artifacts, clears preferences/idempotency state, anonymizes vehicle/support-message owner fields, and preserves reservation/payment/audit/support referential history. Active/future reservations, disputed reservations, and unresolved payment state block fulfillment with explicit reason codes. Legal/payment/fraud retention policy and final owner-approved process wording remain operational/legal work.

Engineering checklist: [docs/app-store-privacy-readiness.md](docs/app-store-privacy-readiness.md). This repository does not provide legal policy text or claim App Store privacy compliance is complete; owner-approved policy pages and App Store Connect answers are still required before signed TestFlight/App Review.

## API Surface

The backend runs under `/api`.

Examples:

| Endpoint | Purpose |
| --- | --- |
| `GET /api/health` | Service health |
| `POST /api/auth/login` | Web cookie/session login |
| `POST /api/auth/token` | Mobile bearer-token login |
| `POST /api/auth/token/refresh` | Refresh-token rotation |
| `GET /api/payments/capabilities` | Online payment provider authority and operation support |
| `POST /api/payments/intents/{id}/cancel` | Provider-ready cancel/void contract for unfinalized intents |
| `POST /api/auth/token/revoke` | Refresh-token revocation |
| `GET /api/auth/me` | Current user profile |
| `POST /api/users/me/deletion-request` | Account deletion request backed by support tickets |
| `POST /api/admin/support-cases/{ticketId}/process-account-deletion` | Admin-reviewed account deletion fulfillment/anonymization action |
| `POST /api/notifications/device-tokens` | Register/reactivate current user's mobile push token |
| `POST /api/notifications/device-tokens/unregister` | Deactivate current user's matching mobile push token |
| `GET /api/locations/search` | Location search foundation |
| `POST /api/reservations/quote` | Reservation quote foundation |
| `POST /api/reservations` | Idempotent reservation creation |

Mobile clients may use `/api/v1/...` aliases for the mobile-critical API surface.

Authoritative mobile contract:

- [docs/mobile-api-contract/SPOTLINK_MOBILE_API_CONTRACT.md](docs/mobile-api-contract/SPOTLINK_MOBILE_API_CONTRACT.md)
- [docs/mobile-api-contract/openapi-mobile-v1.yaml](docs/mobile-api-contract/openapi-mobile-v1.yaml)
- [docs/mobile-api-contract/SWIFT_DTO_ALIGNMENT_GUIDE.md](docs/mobile-api-contract/SWIFT_DTO_ALIGNMENT_GUIDE.md)
- [docs/mobile-api-contract/json-fixtures](docs/mobile-api-contract/json-fixtures)

## Verification Baseline

The foundation has been verified with:

- Backend Maven tests and package verification.
- Frontend Angular headless tests.
- Frontend production build.
- iOS Swift package build.
- iOS Swift test suite preko `swift test` i `npm run test:ios`.
- JSON fixture parsing.
- OpenAPI YAML parsing.
- Backend local smoke tests for health, mobile token issuance, refresh rotation, and revoke.

Recommended pre-push check:

```bash
make test-backend
make test-frontend
make build
mvn -f apps/backend/pom.xml verify
swift build --package-path apps/ios/SpotLink
swift test --package-path apps/ios/SpotLink
```

## Documentation Index

| Document | Purpose |
| --- | --- |
| [SPOTLINK_FOUNDATION_MIGRATION.md](SPOTLINK_FOUNDATION_MIGRATION.md) | Original frontend foundation transfer notes |
| [SPOTLINK_BACKEND_FOUNDATION_MIGRATION.md](SPOTLINK_BACKEND_FOUNDATION_MIGRATION.md) | Backend foundation audit, reuse map, modules, endpoints, migrations |
| [SPOTLINK_FRONTEND_FOUNDATION_HARDENING.md](SPOTLINK_FRONTEND_FOUNDATION_HARDENING.md) | Frontend hardening changes and verification |
| [SPOTLINK_IOS_FOUNDATION_MIGRATION.md](SPOTLINK_IOS_FOUNDATION_MIGRATION.md) | Native iOS foundation structure and verification |
| [docs/mobile-api-contract](docs/mobile-api-contract) | Mobile API contract, fixtures, Swift DTO guide, backend gap report |
| [docs/ios-enterprise-readiness](docs/ios-enterprise-readiness) | iOS UX, architecture, QA, security, TestFlight, App Store readiness |
| [docs/api](docs/api) | Frontend API contract draft |

## Source Control Standards

This repository uses a source-control style suitable for staged platform work:

- Keep `main` releasable.
- Prefer small, reviewable commits grouped by architectural concern.
- Use Conventional Commit style where possible: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`.
- Do not mix generated artifacts with source changes.
- Do not commit local state: `.env`, `target`, `dist`, `.build`, `xcuserdata`, `.xcuserstate`.
- Keep backend, frontend, iOS, and docs changes separated unless a single product slice requires them together.
- Run the relevant verification commands before pushing.

Recommended commit order for future vertical slices:

1. API contract update.
2. Backend implementation and tests.
3. iOS/frontend DTO and client alignment.
4. UX implementation.
5. Documentation and release notes.

## Security Notes

- Real secrets belong in environment variables or the deployment secret store.
- `.env.example` is intentionally safe to commit; `.env` is not.
- Production startup rejects the development JWT secret.
- Staging and production startup reject H2/default DB settings, local/wildcard CORS, insecure cookies, and placeholder JWT secrets.
- Refresh tokens are stored as hashes, not raw token values.
- Payment provider authority is capability-driven; mock payment is non-production only, and production online payments remain disabled until a real PSP is implemented.
- APNs/device tokens are lifecycle-managed for register/unregister, but raw token values must not be logged and APNs secrets must stay outside the repo.
- Privacy manifests, entitlements, and App Store declarations must be finalized before external TestFlight or App Store submission.
- iOS signing credentials and App Store Connect upload credentials are not stored in the repository; signed archive/export targets require local human-controlled Apple Developer setup.

## Known Foundation Limits

These are intentional limits of the current phase:

- The iOS app shell is native but not yet a complete customer product flow.
- Map-grade geospatial search is not fully implemented.
- Payment lifecycle is provider-contract foundation only; real PSP authorization/capture/refund/webhook/reconciliation is not implemented.
- Device-token lifecycle is ready for APNs integration, but APNs provider delivery, credentials, and enabled push entitlement remain incomplete.
- Account deletion request intake and admin-reviewed anonymization fulfillment exist, but legal/privacy owner retention policy and final operating procedure approval are still required.
- Full iOS simulator validation requires Xcode.app.

## Next Engineering Slice

Recommended first MVP slice:

1. Mobile auth session restore and refresh.
2. Map/list search for parking locations.
3. Location detail with resource compatibility.
4. Reservation quote.
5. Idempotent reservation create.
6. Mock payment confirmation.
7. Confirmation and notification foundation.

Use [docs/mobile-api-contract](docs/mobile-api-contract) as the source of truth before expanding native UI.
