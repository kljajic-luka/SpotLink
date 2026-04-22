# Next Engineering Tasks

Date: 2026-04-22

## Parallelization Guidance

Safe to run in parallel:

- Backend API versioning/auth hardening can proceed separately from iOS build fixes if endpoint compatibility is coordinated through OpenAPI.
- QA can create fixture tests from current backend JSON while iOS fixes compile blockers.
- Product/security can decide payment/auth/analytics policies without touching code.

Should wait:

- Real payment UI should wait for payment DTO alignment and PSP/Apple Pay decision.
- APNs real-device verification should wait for Xcode app target, entitlements, and device-token API fix.
- External TestFlight work should wait for compile, DTO alignment, and app metadata.

## Agent-Safe Parallel Tasks

### Task A1: Generate a current mobile API contract fixture set

- Objective: Capture canonical JSON examples for auth, profile, vehicles, search, reservations, payments, support, notifications, operator, admin, analytics, and errors.
- Files or areas likely touched: `docs/api`, `docs/ios-enterprise-readiness`, test fixtures if created later.
- Conflict risk: Low if documentation/fixtures only.
- Estimated complexity: Medium
- Verification command: `mvn -f apps/backend/pom.xml test`
- Done criteria: Fixture files or documented examples exist and every iOS DTO mismatch has a testable sample.

### Task A2: Decide mobile auth and payment release policy

- Objective: Decide whether mobile auth ships with JWT access-only, refresh-token rotation, or OIDC/PKCE, and whether payments are disabled, PSP test mode, or Apple Pay for TestFlight.
- Files or areas likely touched: docs only until decisions are accepted.
- Conflict risk: Low
- Estimated complexity: Medium
- Verification command: none; decision record review.
- Done criteria: Product/security decision record exists with owners and non-negotiable release gates.

### Task A3: Add release-readiness CI checklist

- Objective: Define CI gates for backend tests, iOS build/tests, API contract tests, secret scanning, and production config assertions.
- Files or areas likely touched: docs first; CI files later.
- Conflict risk: Low
- Estimated complexity: Low
- Verification command: `git status --short`
- Done criteria: CI plan maps each gate to a concrete command and owner.

## iOS-Only Tasks

### Task I1: Make the iOS project buildable

- Objective: Fix `swift test` or provide a real iOS build path that compiles.
- Files or areas likely touched: `apps/ios/SpotLink/Package.swift`, SwiftUI views, `SessionManager.swift`, `AuthModels.swift`.
- Conflict risk: High because many iOS files are actively changing.
- Estimated complexity: Medium
- Verification command: `swift test` or `xcodebuild build` once project exists.
- Done criteria: Build command passes and no source-modified-during-build issue appears.

### Task I2: Add real Xcode app target and resources

- Objective: Make the app archiveable for TestFlight.
- Files or areas likely touched: `apps/ios`, Xcode project or generator config, `Assets.xcassets`, Info.plist, entitlements, launch screen, privacy manifest.
- Conflict risk: Medium
- Estimated complexity: High
- Verification command: `xcodebuild -list` and `xcodebuild build`
- Done criteria: Xcode project/workspace lists schemes and builds an app target.

### Task I3: Align iOS DTOs with backend/OpenAPI

- Objective: Fix decode failures across core modules.
- Files or areas likely touched: `apps/ios/SpotLink/Sources/SpotLink/Shared/Models`, feature services, API tests.
- Conflict risk: High
- Estimated complexity: High
- Verification command: `swift test`
- Done criteria: Fixture tests pass for location, geocode, reservation quote, payment, notifications, support, profile, operator, and admin.

### Task I4: Fix API client response handling

- Objective: Correct 204/202 success, validation 400, request ID retention, and no-body response support.
- Files or areas likely touched: `Core/Networking/APIClient.swift`, `APIError.swift`, affected services.
- Conflict risk: Medium
- Estimated complexity: Medium
- Verification command: `swift test`
- Done criteria: Tests cover 204, 202, 400 validation, 401, 403, 404, 409, and 5xx responses.

### Task I5: Fix auth registration and logout behavior

- Objective: Remove pre-registration token call, add backend logout/revocation hook when supported, and handle expired sessions.
- Files or areas likely touched: `Core/Auth/AuthService.swift`, `Core/Session/SessionManager.swift`, auth views.
- Conflict risk: High
- Estimated complexity: Medium
- Verification command: `swift test`
- Done criteria: Login, customer registration, operator registration, restore, expiry, and logout tests pass.

### Task I6: Implement customer search/map shell

- Objective: Replace search placeholder with MapKit/list search and permission-denied fallback.
- Files or areas likely touched: `Features/Locations`, `Core/Location`, `App/MainAppShell.swift`.
- Conflict risk: Medium
- Estimated complexity: High
- Verification command: `swift test` plus manual simulator/device run.
- Done criteria: Search tab shows native map/list, supports manual search, and does not require location permission.

### Task I7: Persist reservation idempotency across retries

- Objective: Make reservation creation safe under mobile timeouts and app restarts.
- Files or areas likely touched: `Features/Reservations`, `Shared/Models/ReservationModels.swift`, local persistence.
- Conflict risk: Medium
- Estimated complexity: Medium
- Verification command: `swift test`
- Done criteria: Retried create after timeout reuses same idempotency key and does not create duplicates.

### Task I8: Wire APNs through real app lifecycle

- Objective: Add app delegate bridge, entitlements, token upload, and deep-link routing.
- Files or areas likely touched: `App/SpotLinkApp.swift`, `Core/Notifications`, entitlements, Info.plist.
- Conflict risk: Medium
- Estimated complexity: High
- Verification command: physical device APNs registration test.
- Done criteria: Token is received and uploaded after sign-in; notification routes to target screen.

## Backend-Only Tasks

### Task B1: Harden mobile token lifecycle

- Objective: Add refresh token rotation, logout/revocation, shorter access-token TTL, and production secret enforcement.
- Files or areas likely touched: `apps/backend/src/main/java/com/spotlink/auth`, `apps/backend/src/main/java/com/spotlink/security`, `application.properties`, tests.
- Conflict risk: Medium
- Estimated complexity: High
- Verification command: `mvn -f apps/backend/pom.xml test`
- Done criteria: Tests cover login, refresh, revoke, expired token, revoked token, and missing production secret.

### Task B2: Publish versioned mobile API contract

- Objective: Add `/api/v1` or version header strategy and OpenAPI examples.
- Files or areas likely touched: backend controllers/config, `docs/api`.
- Conflict risk: Medium
- Estimated complexity: Medium
- Verification command: `mvn -f apps/backend/pom.xml test`
- Done criteria: iOS points at a versioned API and contract fixtures are generated from it.

### Task B3: Make search map-ready

- Objective: Add radius/viewport filtering, availability filtering, distance sorting, and geospatial indexing plan.
- Files or areas likely touched: backend location service/repositories, database migration, tests.
- Conflict risk: Medium
- Estimated complexity: High
- Verification command: `mvn -f apps/backend/pom.xml test`
- Done criteria: Search tests cover center/radius, viewport, availability window, EV/resource filters, and pagination.

### Task B4: Add APNs token lifecycle endpoints

- Objective: Support token register, deactivate/unregister, environment, and preference enforcement.
- Files or areas likely touched: backend notification module and migration.
- Conflict risk: Medium
- Estimated complexity: Medium
- Verification command: `mvn -f apps/backend/pom.xml test`
- Done criteria: Register/deactivate/token-rotation tests pass and platform enum accepts `IOS`.

### Task B5: Replace mock payment for release path

- Objective: Add PSP test provider or explicitly disable paid reservations outside internal builds.
- Files or areas likely touched: backend payment module/config/tests.
- Conflict risk: Medium
- Estimated complexity: High
- Verification command: `mvn -f apps/backend/pom.xml test`
- Done criteria: Production profile fails if mock payment is enabled and payment state tests cover provider callbacks.

## QA-Only Tasks

### Task Q1: Add iOS DTO fixture tests

- Objective: Catch current model/backend drift.
- Files or areas likely touched: `apps/ios/SpotLink/Tests/SpotLinkTests`, fixtures.
- Conflict risk: Medium
- Estimated complexity: Medium
- Verification command: `swift test`
- Done criteria: Tests fail on intentional DTO drift and pass with current backend fixtures.

### Task Q2: Add critical path view model tests

- Objective: Cover auth, search, reservation, payment, support, vehicles, and notifications states.
- Files or areas likely touched: `Tests/SpotLinkTests/Features`.
- Conflict risk: Medium
- Estimated complexity: High
- Verification command: `swift test`
- Done criteria: Loading, empty, success, error, offline, and unauthorized states are tested.

### Task Q3: Define internal TestFlight smoke checklist

- Objective: Provide repeatable manual verification for device builds.
- Files or areas likely touched: docs only at first.
- Conflict risk: Low
- Estimated complexity: Low
- Verification command: checklist review.
- Done criteria: Checklist includes login, search, reservation, payment, support, push, location denied, logout, dark mode, and Dynamic Type.

### Task Q4: Add backend mobile contract tests

- Objective: Prove bearer-auth mobile flows and DTO shapes are stable.
- Files or areas likely touched: `apps/backend/src/test/java/com/spotlink`.
- Conflict risk: Medium
- Estimated complexity: Medium
- Verification command: `mvn -f apps/backend/pom.xml test`
- Done criteria: Tests cover `/auth/token`, bearer `/auth/me`, bearer reservation/payment, and iOS no-XSRF mutation flow.

## Human Product and Security Decisions

### Task H1: Mobile auth policy decision

- Objective: Decide JWT access-only vs refresh-token rotation vs OIDC/PKCE.
- Files or areas likely touched: decision docs first.
- Conflict risk: Low
- Estimated complexity: High
- Verification command: security review sign-off.
- Done criteria: Release-blocking auth requirements are documented and accepted.

### Task H2: Payment and Apple Pay decision

- Objective: Decide PSP, Apple Pay support, mock payment boundaries, and payment states for TestFlight.
- Files or areas likely touched: product/security docs first.
- Conflict risk: Low
- Estimated complexity: High
- Verification command: product/security sign-off.
- Done criteria: TestFlight payment strategy is documented with PSP owner and acceptance tests.

### Task H3: Launch market, language, and legal copy decision

- Objective: Decide primary language, supported market/currency/timezone assumptions, privacy policy, terms, support URL, and account deletion wording.
- Files or areas likely touched: docs, product copy, app resources.
- Conflict risk: Low
- Estimated complexity: Medium
- Verification command: product/legal review.
- Done criteria: App Store metadata and in-app legal links have approved copy.

### Task H4: Analytics and ATT decision

- Objective: Decide analytics provider, consent policy, ATT posture, and PII allowlist.
- Files or areas likely touched: docs, analytics implementation, privacy manifest.
- Conflict risk: Low
- Estimated complexity: Medium
- Verification command: privacy review.
- Done criteria: Analytics behavior is compliant and testable before external TestFlight.

