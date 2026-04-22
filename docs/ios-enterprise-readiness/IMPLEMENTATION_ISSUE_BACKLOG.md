# Implementation Issue Backlog

Date: 2026-04-22

Priorities:

- `P0`: blocks a serious iOS foundation.
- `P1`: should be fixed before internal TestFlight.
- `P2`: should be fixed before external TestFlight.
- `P3`: can wait until after MVP foundation.

## SL-IOS-AUD-001

- Title: iOS package does not compile under the declared SwiftPM test target
- Priority: P0
- Area: iOS, QA
- Evidence: `apps/ios/SpotLink/Package.swift:8-10`, `apps/ios/SpotLink/Sources/SpotLink/Core/Session/SessionManager.swift:127`, `apps/ios/SpotLink/Sources/SpotLink/Features/Vehicles/VehiclesView.swift:81`, `apps/ios/SpotLink/Sources/SpotLink/Features/Reservations/ReservationsView.swift:97`, `apps/ios/SpotLink/Sources/SpotLink/Features/Support/SupportView.swift:69`, `apps/ios/SpotLink/Sources/SpotLink/Features/Auth/AuthViews.swift:130`
- Why it matters: A native foundation that cannot build or test cannot be reviewed, hardened, or used by other agents safely.
- Recommended fix: Either remove macOS from the package and build with an iOS destination, or add platform wrappers for iOS-only SwiftUI APIs. Make `UserProfile` encodable or stop encoding it directly.
- Acceptance criteria: `swift test` passes or an iOS-specific build/test command is documented and passes in CI.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-002

- Title: No Xcode app project, app target, assets, entitlements, or privacy manifest
- Priority: P0
- Area: iOS, Release
- Evidence: `apps/ios/SpotLink/Package.swift:12-14` exposes only a library product; no `.xcodeproj`, `.xcworkspace`, `Assets.xcassets`, `Info.plist`, entitlements, or `PrivacyInfo.xcprivacy` were found.
- Why it matters: TestFlight requires a buildable app target with signing, bundle ID, assets, launch screen, permission strings, entitlements, and archive settings.
- Recommended fix: Add a real iOS app project/workspace or checked-in XcodeGen/Tuist config that generates one, with resources and release configurations.
- Acceptance criteria: `xcodebuild -list` works and `xcodebuild build` succeeds for a simulator or generic iOS destination.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-003

- Title: Location search and geocode DTOs do not match backend
- Priority: P0
- Area: API Contract, iOS
- Evidence: backend `apps/backend/src/main/java/com/spotlink/location/LocationDtos.java:67-80`; iOS `apps/ios/SpotLink/Sources/SpotLink/Shared/Models/LocationModels.swift:166-188`
- Why it matters: Search and geocode responses will decode-fail, blocking the primary customer experience.
- Recommended fix: Change iOS models to match backend nested `location`, `resources`, `distanceKm`, `startingPriceCents`, and `availableResourceCount`, or update backend/OpenAPI and frontend contracts consistently.
- Acceptance criteria: Contract fixture tests decode live backend search/geocode responses successfully.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-004

- Title: Payment DTOs and confirm response do not match backend
- Priority: P0
- Area: API Contract, iOS
- Evidence: backend `PaymentDtos.java:13-46`; iOS `PaymentModels.swift:36-81`, `PaymentService.swift:20-22`
- Why it matters: Payment methods, intent creation, and confirmation are core revenue/reservation flows and currently decode the wrong shapes.
- Recommended fix: Align iOS `PaymentMethod`, `PaymentIntent`, and `PaymentProviderResult` with backend. `confirmIntent` should decode backend `PaymentProviderResult`, not `PaymentIntent`.
- Acceptance criteria: Payment method list, intent create, and confirm fixture tests pass against backend responses.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-005

- Title: Notification endpoints are method, enum, and no-content incompatible
- Priority: P0
- Area: API Contract, iOS
- Evidence: backend `NotificationController.java:36-43`, `DevicePlatform.java:3-6`, `NotificationDtos.java:14-28`; iOS `NotificationService.swift:21-29`, `NotificationModels.swift:15-36`
- Why it matters: Mark-read and APNs token registration will fail even when backend behavior is correct.
- Recommended fix: Use `POST /notifications/{id}/read`, send platform `IOS`, decode backend `read`, and handle `204 No Content` without attempting custom empty DTO decoding.
- Acceptance criteria: Notification list, unread count, mark-read, and device-token registration contract tests pass.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-006

- Title: Customer registration calls token endpoint before registration
- Priority: P0
- Area: iOS, Auth
- Evidence: `apps/ios/SpotLink/Sources/SpotLink/Core/Auth/AuthService.swift:30-38`
- Why it matters: A new customer cannot get a token before the account exists; this can make registration fail before the registration request is sent.
- Recommended fix: Remove the pre-registration `/auth/token` call. Register first, then establish the returned session if backend returns a token, or call `/auth/token` after successful registration.
- Acceptance criteria: New customer registration succeeds in an integration test against the backend.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-007

- Title: API client mishandles valid 204 and 202 empty responses
- Priority: P0
- Area: iOS, API Contract
- Evidence: `APIClient.swift:122-128`, `AuthService.swift:51-60`, `NotificationService.swift:26-30`, backend `AnalyticsController.java:19-21`
- Why it matters: Password reset, device token registration, notification mark-read, delete operations, and analytics can report false failures after successful server responses.
- Recommended fix: Add a public `EmptyResponse`/`NoContent` type and treat 204 and accepted empty responses as success when the caller expects no body.
- Acceptance criteria: Tests cover 204 password reset, 204 device token registration, 204 mark-read, and 202 analytics accepted.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-008

- Title: Reservation quote and idempotency client behavior are not mobile-safe
- Priority: P0
- Area: iOS, API Contract, Product
- Evidence: backend `ReservationDtos.java:41-62`; iOS `ReservationModels.swift:103-151`
- Why it matters: Quote decoding can fail, and idempotency keys generated only inside request init are not persisted for retry after timeout/app kill.
- Recommended fix: Align quote fields and introduce an in-flight reservation draft model that persists the idempotency key until terminal success/failure.
- Acceptance criteria: Double-tap and network-timeout tests prove one reservation is created and retried with the same key.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-009

- Title: iOS test target is empty
- Priority: P0
- Area: QA, iOS
- Evidence: `apps/ios/SpotLink/Package.swift:24-27`; `find apps/ios/SpotLink/Tests/SpotLinkTests -type f` returned no files.
- Why it matters: Contract drift and state regressions are already present and would have been caught by fixture/view model tests.
- Recommended fix: Add unit tests for DTO decoding, API error mapping, 204 handling, auth flow, idempotency, and feature view models.
- Acceptance criteria: `swift test` or equivalent Xcode test target runs non-empty tests in CI.
- Suggested owner: QA agent

## SL-IOS-AUD-010

- Title: App shell is mostly placeholders, not a usable parking app
- Priority: P1
- Area: Product, iOS
- Evidence: `MainAppShell.swift:86-127`, `VehiclesView.swift:88-90`, `SupportView.swift:83-85`
- Why it matters: A premium native foundation must expose the real first-screen workflows: search/map, reservation, vehicle, support, and profile.
- Recommended fix: Replace placeholders with real feature entry points, even if backed by stubs for incomplete backend workflows.
- Acceptance criteria: A signed-in customer can search, view detail, start reservation, manage vehicles, and create support from native screens.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-011

- Title: No MapKit search experience exists
- Priority: P1
- Area: iOS, Product
- Evidence: `MainAppShell.swift:86-94`; no `MapKit` import or `Map` view was found.
- Why it matters: Map search is the core premium customer experience for a parking app.
- Recommended fix: Add MapKit search/list view with filters, permission-denied fallback, and result preview sheet.
- Acceptance criteria: Search tab renders a native map/list experience and handles location denied/manual search.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-012

- Title: Support, profile, operator, and admin DTOs drift from backend
- Priority: P1
- Area: API Contract, iOS
- Evidence: backend `SupportTicketStatus.java:3-7`, `SupportTicketCategory.java:3-9`, `UserDtos.java:46-50`, `OperatorDtos.java:21-39`, `AdminDtos.java:15-43`; iOS `SupportModels.swift:5-27`, `ProfileModels.swift:24-110`
- Why it matters: Non-customer-role and support/profile surfaces will decode-fail or show wrong metrics.
- Recommended fix: Generate or hand-align DTOs from a versioned OpenAPI contract and add fixtures.
- Acceptance criteria: Profile, support, operator, and admin fixture tests decode backend responses.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-013

- Title: Mobile JWT model lacks refresh, revocation, and production-safe defaults
- Priority: P1
- Area: Security, Backend, API Contract
- Evidence: `JwtService.java:41-78`, `application.properties:55-56`, `AuthService.swift:65-67`
- Why it matters: A 7-day bearer access token with no refresh/revoke path is high impact if lost and does not support clean logout.
- Recommended fix: Add refresh token rotation, access-token short TTL, revocation endpoint, logout revocation, and production failure if `JWT_SECRET` is missing.
- Acceptance criteria: Mobile auth has login, refresh, revoke/logout, expired token, and revoked token tests.
- Suggested owner: Backend agent

## SL-IOS-AUD-014

- Title: APNs is not wired into a real app lifecycle
- Priority: P1
- Area: iOS, Release
- Evidence: `PushNotificationManager.swift:15-18`, `PushNotificationManager.swift:45-68`, `SpotLinkApp.swift:7-24`; no `UIApplicationDelegateAdaptor` or entitlements were found.
- Why it matters: Device token registration cannot work reliably without app delegate hooks and APNs entitlements.
- Recommended fix: Add app delegate adapter, entitlements, APNs environment, token lifecycle, and signed-in service injection.
- Acceptance criteria: A real device can grant permission, receive token, upload token, and route notification deep links.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-015

- Title: Hardcoded API URLs are embedded in Swift source
- Priority: P1
- Area: Release, Security
- Evidence: `AppEnvironment.swift:10-20`
- Why it matters: Release builds need auditable environment configuration and must prevent accidental local/dev API use.
- Recommended fix: Move URLs to `.xcconfig`, generated config, or signed runtime config with release validation.
- Acceptance criteria: Production archive fails if API base URL is missing or non-production.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-016

- Title: Keychain storage lacks accessibility and device-only policy
- Priority: P1
- Area: Security, iOS
- Evidence: `KeychainStorage.swift:63-69`
- Why it matters: Token storage needs explicit protection class and sync policy.
- Recommended fix: Set `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` or stricter and document the background access decision.
- Acceptance criteria: Keychain tests verify attributes and logout deletes token.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-017

- Title: Backend mock payment remains enabled by default and Apple Pay is absent
- Priority: P1
- Area: Payments, Release, Backend
- Evidence: `application.properties:53`, `PaymentService.swift:1-22`, no `PassKit` usage found.
- Why it matters: External TestFlight cannot present production-like paid reservations with mock payments and no PSP/Apple Pay decision.
- Recommended fix: Add production config guard, PSP test integration, payment refresh/return flow, and Apple Pay decision.
- Acceptance criteria: Production profile cannot start with mock payment enabled; payment test mode succeeds on device.
- Suggested owner: Human decision required

## SL-IOS-AUD-018

- Title: Analytics payload is incompatible and lacks consent model
- Priority: P2
- Area: API Contract, Security, Product
- Evidence: backend `AnalyticsDtos.java:16-27`; iOS `Analytics.swift:70-93`
- Why it matters: Analytics events will fail validation and the privacy model is undefined.
- Recommended fix: Send `{ events: [...] }` with backend field names or update backend contract; add consent/preferences and PII allowlist.
- Acceptance criteria: Analytics accepted test passes and opt-out prevents event submission.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-019

- Title: API is still unversioned for mobile clients
- Priority: P1
- Area: API Contract, Backend
- Evidence: endpoints are under `/api` context path and controllers map unversioned paths such as `AuthController.java:61`, `ReservationController.java:24`, `PaymentController.java:23`
- Why it matters: Native clients are harder to update than web clients; unversioned APIs make backward compatibility risky.
- Recommended fix: Introduce `/api/v1` or explicit version header and publish mobile-supported OpenAPI.
- Acceptance criteria: iOS uses versioned API and contract tests pin that version.
- Suggested owner: Backend agent

## SL-IOS-AUD-020

- Title: App Store privacy/release metadata is missing
- Priority: P2
- Area: Release, Security
- Evidence: no `PrivacyInfo.xcprivacy`, asset catalog, launch screen, Info.plist, entitlements, signing config, or support/legal URL integration found under `apps/ios`.
- Why it matters: These are hard blockers for TestFlight/App Store distribution.
- Recommended fix: Add release resources and metadata early, even if initially minimal.
- Acceptance criteria: App archive includes privacy manifest, icons, launch screen, permission strings, and entitlements where used.
- Suggested owner: iOS implementation agent

## SL-IOS-AUD-021

- Title: Accessibility and Dynamic Type are not verified
- Priority: P2
- Area: QA, Design System
- Evidence: no iOS test files; placeholder UI in `MainAppShell.swift:86-127`; no snapshot/accessibility test setup found.
- Why it matters: Map, payment, reservation, and support flows must be accessible before external users.
- Recommended fix: Add accessibility smoke tests and manual QA checklist runs for VoiceOver, Dynamic Type, dark mode, and reduced motion.
- Acceptance criteria: Core customer flow passes accessibility QA with documented results.
- Suggested owner: QA agent

