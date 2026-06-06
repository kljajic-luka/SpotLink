# SpotLink Native iOS Implementation Audit

Date: 2026-04-22

## Audit Scope

This audit reviews the current working-tree snapshot against the iOS enterprise readiness package in this directory. It is a review and planning artifact only. No implementation files were changed.

The repository appears to be actively changing. During `swift test`, SwiftPM reported that `apps/ios/SpotLink/Sources/SpotLink/Core/Auth/AuthService.swift` was modified during the build. Treat this audit as a point-in-time snapshot, not as a claim about concurrent work after the commands were run.

## Audit Plan

1. Establish current worktree state and existing readiness bar.
2. Inspect `apps/ios` structure, build metadata, native framework usage, and tests.
3. Compare iOS endpoints and DTOs with the backend controller/DTO contracts.
4. Run non-destructive diagnostics: Xcode project discovery, Swift build/test probe, backend tests, and frontend build.
5. Produce prioritized blockers and next tasks without editing implementation files.

## Current Repo and Worktree Snapshot

`git status --short` shows a dirty working tree with preexisting and concurrent work:

- Modified root/frontend files, including `.gitignore`, `README.md`, `package.json`, frontend package files, and several Angular foundation files.
- Untracked backend tree under `apps/backend/`.
- Untracked iOS tree under `apps/ios/`.
- Untracked docs under `docs/api/` and `docs/ios-enterprise-readiness/`.
- This audit added only Markdown files under `docs/ios-enterprise-readiness`.

The current snapshot includes backend changes that were not present in the earlier readiness package, notably JWT mobile bearer auth under `apps/backend/src/main/java/com/spotlink/security`.

## apps/ios Existence and Shape

`apps/ios` exists and contains a Swift Package:

- `apps/ios/SpotLink/Package.swift`
- `apps/ios/SpotLink/Sources/SpotLink/App`
- `apps/ios/SpotLink/Sources/SpotLink/Core`
- `apps/ios/SpotLink/Sources/SpotLink/Features`
- `apps/ios/SpotLink/Sources/SpotLink/Shared`
- `apps/ios/SpotLink/Tests/SpotLinkTests`

No Xcode project, workspace, app resources, asset catalog, entitlements, Info.plist, privacy manifest, or launch screen were found under `apps/ios`.

Evidence:

- `apps/ios/SpotLink/Package.swift:6` defines a Swift package named `SpotLink`.
- `apps/ios/SpotLink/Package.swift:12` exposes only a library product.
- `apps/ios/SpotLink/Package.swift:24` declares a `SpotLinkTests` target, but no test files were found.
- `find apps/ios -name '*.xcodeproj' -o -name '*.xcworkspace'` found nothing.

Assessment: the iOS tree is an early native source foundation, not a TestFlight-buildable iOS app project.

## Native Swift/SwiftUI Assessment

The implementation is native Swift/SwiftUI. No `WebView` or `WKWebView` usage was found.

Positive evidence:

- `apps/ios/SpotLink/Sources/SpotLink/App/SpotLinkApp.swift:1` imports SwiftUI.
- `apps/ios/SpotLink/Sources/SpotLink/App/MainAppShell.swift:19` uses `TabView`.
- `apps/ios/SpotLink/Sources/SpotLink/App/MainAppShell.swift:21` and following tab roots use `NavigationStack`.
- `apps/ios/SpotLink/Sources/SpotLink/Core/Location/LocationManager.swift:1` imports CoreLocation.

Concern: the current views are mostly placeholders, not a premium parking/search/reservation experience.

## Architecture Blueprint Alignment

Partial alignment:

- Good high-level folder names: `App`, `Core`, `Features`, `Shared`.
- Core boundaries exist for networking, auth, config, location, notifications, observability, session, storage, validation, and design system.
- Feature service files exist for locations, reservations, payments, vehicles, support, operator, admin, notifications, profile, and analytics.

Major gaps:

- No `AppContainer` or clear dependency injection container.
- View models instantiate live clients directly in views/view models, for example `AuthViews.swift:174` and `AuthViews.swift:307`.
- Networking is path-string based, not endpoint typed.
- No release app target or Xcode project.
- No resource bundle, app icon, launch screen, entitlements, or privacy manifest.
- No implemented customer map/search/reservation/payment flow.

Assessment: the architecture is directionally compatible with the blueprint, but not yet enterprise-grade or release-buildable.

## App Shell and Navigation

The shell is native but placeholder-heavy:

- `MainAppShell.swift:19` defines a customer-style `TabView`.
- `MainAppShell.swift:20-68` defines Search, Reservations, Vehicles, Support, and Profile tabs.
- `MainAppShell.swift:86-127` uses placeholder views for Search, Reservations, Vehicles, and Support.
- Role-aware operator/admin/support shell behavior is not implemented, despite role types existing.

Assessment: acceptable as a skeletal native shell, not as a premium iOS app foundation. The first screen does not yet deliver the map/search experience required by the product spec.

## Networking and API Client

Strengths:

- Uses `URLSession`.
- Adds `X-Request-Id` at `APIClient.swift:93`.
- Adds bearer token when available at `APIClient.swift:95-97`.
- Uses ISO-8601 encoder/decoder at `APIClient.swift:39` and `APIClient.swift:43`.
- Decodes structured errors in concept at `APIClient.swift:138-157`.

Problems:

- Path-string calls are scattered across feature services.
- No retry policy.
- No `Retry-After` handling.
- No idempotency header support.
- No request ID retained in `APIError`.
- `204` handling only succeeds for private `EmptyResponse`; feature code uses other empty response types, so password reset and device-token registration will fail on valid backend `204`.
- `202` with empty body is not handled, so analytics ingestion fails even when accepted.
- Error mapping expects validation at HTTP 422, but the backend returns validation errors as HTTP 400.

Evidence:

- `APIClient.swift:122-128` only handles `204` as `EmptyResponse`.
- `APIClient.swift:151-153` maps validation only from `422`.
- Backend validation handler returns `HttpStatus.BAD_REQUEST` in `GlobalExceptionHandler`.

Assessment: the networking layer is a useful start but currently incompatible with multiple valid backend responses.

## Auth and Session Storage

Current state:

- Backend now exposes `POST /api/auth/token` for mobile bearer auth at `AuthController.java:141-157`.
- iOS calls `/auth/token` for login at `AuthService.swift:21-24`.
- The token is stored in Keychain at `SessionManager.swift:120-122`.
- User profile is stored in `UserDefaults` at `SessionManager.swift:126-130`.

Critical problems:

- `AuthService.registerCustomer` calls `/auth/token` before creating the account at `AuthService.swift:31-34`, so customer registration can fail before registration.
- Logout only clears local session at `AuthService.swift:65-67`; it does not call backend logout or revoke bearer tokens.
- There is no refresh token, rotation, revocation, or session/device list.
- Backend JWT defaults to a 7-day access token at `application.properties:56`.
- Backend has a dev JWT secret fallback at `application.properties:55` and `AppProperties.java:109-111`.
- `UserRole` omits `SUPPORT` in `AuthModels.swift:133-137`.
- `UserProfile` is only `Decodable`, but `SessionManager` attempts to encode it at `SessionManager.swift:127`, causing compilation failure.
- Keychain items do not specify an accessibility class or device-only storage at `KeychainStorage.swift:63-69`.

Assessment: auth is not production-ready and currently has a compile blocker plus workflow bugs.

## Models and DTO Alignment

DTO alignment is the highest API risk. Several iOS models do not match backend response shapes:

- Location search: backend returns `{ location, resources, distanceKm, startingPriceCents, availableResourceCount }` at `LocationDtos.java:67-73`; iOS expects flat fields `id`, `name`, `address`, `coordinates`, `accessType`, `minHourlyRateCents` at `LocationModels.swift:166-175`.
- Geocode: backend returns `id`, `address`, `coordinates`, `accuracyMeters` at `LocationDtos.java:76-80`; iOS expects `displayName`, `latitude`, `longitude`, `placeId` at `LocationModels.swift:184-188`.
- Reservation quote: backend returns subtotal, fees, discount, total, currency, expiresAt at `ReservationDtos.java:41-50`; iOS expects `quoteId` and `durationHours` at `ReservationModels.swift:103-111`.
- Payment methods: backend returns `brand`, `last4`, `expMonth`, `expYear`, `default` at `PaymentDtos.java:13-20`; iOS expects `type`, `displayName`, `expiryMonth`, `expiryYear` at `PaymentModels.swift:74-81`.
- Payment confirmation: backend returns `PaymentProviderResult` at `PaymentDtos.java:41-46`; iOS `confirmIntent` expects `PaymentIntent` at `PaymentService.swift:20-22`.
- Notifications: backend returns `read` at `NotificationDtos.java:14-21`; iOS maps `read` from `readFlag` at `NotificationModels.swift:15-19`.
- Support statuses/categories differ between backend `SupportTicketStatus.java:3-7`, `SupportTicketCategory.java:3-9` and iOS `SupportModels.swift:5-27`.
- Profile stats differ: backend has `savedLocations` and `supportTickets` at `UserDtos.java:46-50`; iOS expects `openSupportTickets` at `ProfileModels.swift:24-28`.
- Operator/admin DTOs differ materially between backend `OperatorDtos.java:21-39`, `AdminDtos.java:15-43` and iOS `ProfileModels.swift:66-110`.

Assessment: without contract correction, many screens will decode-fail even if the backend is running.

## Location and Map Readiness

Current state:

- A CoreLocation wrapper exists.
- A `LocationService` can call search/geocode/detail/resource endpoints.
- No MapKit UI implementation was found.
- Search tab is a placeholder at `MainAppShell.swift:86-94`.

Problems:

- No map/list synchronization.
- No permission education UI beyond raw location manager.
- No `NSLocationWhenInUseUsageDescription` or Info.plist was found.
- Search DTOs do not match backend.
- `LocationSearchFilters.startsAt` and `endsAt` are `String`, not typed `Date`.

Assessment: location plumbing is early, map product readiness is missing.

## Reservation and Idempotency Readiness

Current state:

- Reservation service methods exist for list, get, quote, create, and cancel.
- `CreateReservationRequest` generates an idempotency key at `ReservationModels.swift:150`.

Problems:

- Idempotency key is generated inside the request initializer and not persisted across network timeout/app restart.
- No reservation creation UI exists.
- Reservation quote model does not match backend.
- Date strings are used for quote/create requests rather than typed date encoding.
- Reservation list has cancel support, but no confirmation dialog is visible at `ReservationsView.swift:127-132`.

Assessment: backend idempotency exists, but the iOS client is not retry-safe enough for mobile networks.

## Payments and Apple Pay Readiness

Current state:

- Payment service and models exist.
- No PassKit or Apple Pay usage was found.
- Backend still defaults mock payment enabled at `application.properties:53`.

Problems:

- Payment method/intent DTOs do not match backend.
- Payment confirmation expects the wrong response type.
- No `REQUIRES_ACTION` web authentication/deep-link flow.
- No PSP SDK/Apple Pay integration.
- No production guard visible on iOS to prevent mock payment mode.

Assessment: payment code is not ready for internal TestFlight beyond compilation experiments.

## Notifications and APNs Readiness

Current state:

- `PushNotificationManager` exists.
- `NotificationService.registerDeviceToken` posts to `/notifications/device-tokens`.

Problems:

- No AppDelegate bridge is wired in `SpotLinkApp.swift`; `UIApplicationDelegateAdaptor` was not found.
- No entitlements or APNs environment file found.
- `PushNotificationManager` has an optional `NotificationService` defaulting to nil at `PushNotificationManager.swift:15-18`, so token upload can be a no-op.
- Device token request sends platform `"iOS"` at `NotificationModels.swift:33-36`, but backend enum requires `IOS`.
- `markRead` uses `DELETE /notifications/{id}/read` at `NotificationService.swift:21-23`, but backend uses `POST`.
- Device token registration expects a decodable response even though backend returns `204`.

Assessment: APNs readiness is mostly scaffolding and currently API-incompatible.

## Accessibility, Dynamic Type, and Dark Mode

Positive:

- SwiftUI system fonts are used in many places.
- Several controls include accessibility labels.
- Semantic colors are used for background/labels in `DesignTokens.swift:28-54`.

Concerns:

- No accessibility tests.
- Placeholder screens cannot validate real map/list/payment/reservation accessibility.
- Status badges use color with text, which is acceptable, but contrast was not verified.
- Design tokens include radii above the readiness package's 8px cap (`DesignTokens.swift:87-92`).
- `navigationBarHidden` is deprecated/unavailable for the macOS SwiftPM target and should be replaced with modern toolbar hiding.

Assessment: early accessibility consideration exists, but it is not validated and not complete.

## Security and Privacy Readiness

Critical/security gaps:

- JWT access tokens are long-lived and non-refreshable.
- No server-side bearer token revocation on logout.
- Default JWT secret fallback exists and must be production-blocked.
- Keychain accessibility is not set.
- User profile is cached in UserDefaults.
- No privacy manifest.
- No location permission string.
- No account deletion flow.
- First-party analytics is consent-disabled by default and privacy-filtered; owner-approved user-facing consent/policy copy remains.
- No crash reporting or PII-redaction policy enforcement in app code.
- License plate is displayed in list/accessibility label at `VehiclesView.swift:114-128`; that may be acceptable in the vehicle screen but needs privacy review and snapshot/test discipline.

Assessment: not ready for external TestFlight or App Store.

## Test Coverage

Current state:

- No Swift test files were found under `apps/ios/SpotLink/Tests/SpotLinkTests`.
- `swift test` fails before running tests.
- Backend `mvn test` passes 4 tests.
- Frontend `npm run build` passes.

Diagnostics:

```text
xcodebuild -list: not applicable; no Xcode project/workspace found.
swift test: failed.
xcrun --sdk iphonesimulator swift build --triple arm64-apple-ios17.0-simulator: failed because iphonesimulator SDK cannot be located in this environment.
mvn -f apps/backend/pom.xml test: passed, 4 tests.
npm run build: passed.
```

Notable `swift test` failures:

- `SessionManager.swift:127`: `UserProfile` does not conform to `Encodable`.
- `VehiclesView.swift:81`, `ReservationsView.swift:97`, `SupportView.swift:69`, `MainAppShell.swift:181`: `.insetGrouped` unavailable in macOS despite package advertising macOS support.
- `VehiclesView.swift:87`, `SupportView.swift:75`: `.topBarTrailing` unavailable in macOS.
- `AuthViews.swift:130`, `232`, `387`: `.navigationBarHidden` unavailable in macOS.
- SwiftPM also reported `AuthService.swift` was modified during build, consistent with active concurrent edits.

Assessment: iOS test coverage is effectively missing.

## App Store and TestFlight Readiness

Not ready.

Missing:

- Xcode app project/workspace or equivalent generated project checked in.
- Bundle identifier.
- Signing configuration.
- Provisioning profiles.
- App icon.
- Launch screen.
- Asset catalog.
- Info.plist permission strings.
- Privacy manifest.
- Push notification entitlement.
- Apple Pay entitlement/decision.
- Crash reporting.
- Account deletion path.
- Privacy policy/terms/support URL integration.
- Release configuration validation.
- Real device APNs/payment testing.

## Overall Verdict

`Needs major refactor`

The implementation is moving in the right broad direction because it is native Swift/SwiftUI and roughly follows the desired module names. However, it is not a serious iOS foundation yet because it does not compile under the advertised SwiftPM test setup, lacks an app project/release assets, has many DTO and endpoint mismatches, has only placeholder product screens, and is missing major security, privacy, APNs, payment, and TestFlight foundations.
