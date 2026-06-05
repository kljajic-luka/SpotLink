# iOS Architecture Blueprint

Date: 2026-04-22

## Architecture Goal

Build SpotLink iOS as a native Swift app with clear feature modules, typed API boundaries, testable state management, secure session handling, and production release discipline. The app should mirror the product domain contracts already present in the Angular and Spring Boot foundations without copying web architecture into iOS.

## Platform Baseline

Recommended baseline:

- Swift 6 or latest project-supported Swift version.
- SwiftUI-first app.
- async/await for networking and domain operations.
- Observation framework or `ObservableObject` depending on deployment target.
- Minimum iOS version chosen deliberately. If iOS 17+ is acceptable, SwiftData and Observation are reasonable. If iOS 16 support is required, use `ObservableObject` and a SQLite/Core Data strategy instead.
- UIKit only through adapters where needed.

## App Architecture

Recommended pattern:

- SwiftUI views render state and forward user intent.
- View models coordinate view state, validation, and use-case calls.
- Use cases contain business workflows such as quote reservation, create reservation, register device token, or confirm payment.
- Repositories hide API/cache details.
- API clients are typed and isolated from views.
- Security-sensitive storage is isolated behind protocols.

Suggested layers:

```text
SwiftUI View
ViewModel
UseCase
Repository
APIClient / LocalStore / KeychainStore
URLSession / Persistence / Keychain
```

Recommended type names:

- `SpotLinkApp`
- `AppEnvironment`
- `AppContainer`
- `AppSessionStore`
- `SessionState`
- `UserRole`
- `APIClient`
- `APIEndpoint`
- `APIRequest`
- `APIError`
- `APIPage<T>`
- `RequestIDProvider`
- `IdempotencyKeyProvider`
- `AuthRepository`
- `LocationRepository`
- `ReservationRepository`
- `PaymentRepository`
- `VehicleRepository`
- `NotificationRepository`
- `SupportRepository`
- `OperatorRepository`
- `AdminRepository`
- `AnalyticsClient`

## Module Boundaries

Use feature boundaries that match the current foundation:

| Module | Owns |
| --- | --- |
| `AppShell` | App entry, role-aware tabs, navigation, deep links, app lifecycle. |
| `Core` | Shared primitives, environment, clocks, IDs, formatting, validation, role checks. |
| `DesignSystem` | Native colors, typography, spacing, reusable controls, status views. |
| `Networking` | URLSession, typed requests, decoding, errors, retry, request IDs, auth attachment. |
| `Auth` | Sign in, registration, reset, session restore, logout. |
| `Profile` | Current user, preferences, account settings, legal links. |
| `Locations` | Search, geocode, map state, location/resource detail. |
| `Reservations` | Quote, create, list, detail, cancel, reservation state model. |
| `Payments` | Payment methods, intents, PSP/Apple Pay adapters, return handling. |
| `Vehicles` | Vehicle list, add/edit/delete, fit presentation. |
| `Notifications` | APNs registration, inbox, unread counts, deep links. |
| `Support` | Tickets, messages, ticket creation. |
| `Operator` | Dashboard, resource health, inventory surfaces. |
| `Admin` | Dashboard, users, audit events, admin read models. |
| `Analytics` | Consent-aware product analytics and event queue. |
| `TestSupport` | API stubs, fixtures, mock stores, deterministic clocks. |

## Suggested Folder Structure

Do not create source files from this document automatically. This is a target shape for a future `apps/ios` implementation.

```text
apps/ios/
  SpotLink/
    App/
      SpotLinkApp.swift
      AppContainer.swift
      AppEnvironment.swift
      AppRouter.swift
      DeepLinkRouter.swift
    Core/
      Models/
      Formatting/
      Security/
      Persistence/
      Utilities/
    DesignSystem/
      Colors.swift
      Typography.swift
      Components/
      StateViews/
    Networking/
      APIClient.swift
      APIEndpoint.swift
      APIError.swift
      APIPage.swift
      RequestIDProvider.swift
      RetryPolicy.swift
    Features/
      Auth/
      Profile/
      Locations/
      Reservations/
      Payments/
      Vehicles/
      Notifications/
      Support/
      Operator/
      Admin/
    Resources/
      Assets.xcassets
      Localizable.xcstrings
      PrivacyInfo.xcprivacy
      Config/
    Tests/
      Unit/
      Integration/
      UITests/
      Fixtures/
```

## Environment Configuration

Required environments:

- Local
- Development
- Staging
- Production

Configuration must include:

- API base URL.
- APNs environment.
- Payment provider mode.
- Analytics enabled flag.
- Logging level.
- Feature flags for operator/admin surfaces.
- Privacy policy, terms, support URL.

Rules:

- Do not hardcode production API URLs in Swift source.
- Use `.xcconfig`, build settings, or signed remote configuration for values.
- Runtime environment display should be visible in debug/internal builds only.
- Production builds must fail CI if mock payment or mock API mode is enabled.

Suggested type:

```swift
struct AppEnvironment {
    let name: EnvironmentName
    let apiBaseURL: URL
    let apnsEnvironment: APNSEnvironment
    let paymentMode: PaymentMode
    let analyticsEnabled: Bool
}
```

## Networking Layer

Use `URLSession` with typed requests:

- `APIEndpoint` defines method, path, query, body, auth requirement, idempotency key, and retry policy.
- `APIClient` handles encoding, decoding, headers, cookies/tokens, error mapping, and request IDs.
- Decoding must use ISO-8601 date decoding compatible with backend `Instant`.
- Money stays in minor units (`amountCents`) plus currency.
- The app generates `X-Request-Id` for every request and sends it to the backend.
- The app stores server `X-Request-Id` on failures for support diagnostics.

Recommended headers:

- `Accept: application/json`
- `Content-Type: application/json` for JSON bodies.
- `X-Request-Id` on every request.
- `X-Idempotency-Key` for idempotent mutations once backend supports header-based idempotency.
- `Authorization: Bearer ...` only if backend adopts a mobile token model.
- `X-XSRF-TOKEN` only if using cookie/session auth against current backend.

Retry policy:

- Retry safe methods (`GET`, `HEAD`) for transient network errors and HTTP 502/503/504.
- Do not blindly retry reservation creation or payment confirmation unless an idempotency key is attached and the API contract explicitly allows it.
- Honor `Retry-After`.
- Use exponential backoff with jitter.
- Surface final failures as typed recoverable errors.

## API Client Layer

Suggested protocols:

```swift
protocol APIClient {
    func send<Response: Decodable>(_ request: APIRequest<Response>) async throws -> Response
}

protocol AuthAPI {
    func login(_ request: LoginRequest) async throws -> AuthResponse
    func currentUser() async throws -> UserProfile
    func logout() async throws
}

protocol ReservationAPI {
    func quote(_ request: ReservationQuoteRequest) async throws -> ReservationQuote
    func create(_ request: CreateReservationRequest, idempotencyKey: String) async throws -> Reservation
}
```

Rules:

- Views never construct raw URLs.
- View models never parse raw JSON.
- DTOs should be `Decodable`, `Encodable`, `Equatable` where useful for tests.
- Preserve backend enum raw values exactly.
- Unknown enum handling should fail loudly in development and map to an `.unknown(String)` case only where forward compatibility is required.

## Auth and Session Handling

Current backend model:

- Cookie/session auth.
- XSRF cookie/header for mutating requests.
- `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`.
- Customer/operator registration.
- Password reset request and completion.

Recommended production mobile direction:

- Prefer OIDC/OAuth-style authorization code with PKCE or a backend-issued short-lived access token plus rotating refresh token.
- Store refresh token or session secret in Keychain.
- Keep access token in memory where possible.
- Support server-side session revocation.
- Support device/session list and remote logout later.

Interim foundation option:

- Use cookie/session auth with `URLSessionConfiguration` cookie storage and XSRF header mirroring.
- Treat this as a foundation/testing path, not a final App Store posture unless the backend explicitly hardens native cookie sessions.

Required session states:

- Unknown
- Anonymous
- Authenticating
- Authenticated
- Refreshing
- Expired
- Revoked
- Locked/suspended

## Keychain Usage

Use Keychain for:

- Refresh token or mobile session secret.
- Device-scoped installation ID if needed.
- Last authenticated user ID if required for cleanup.

Do not store in Keychain:

- Passwords.
- Raw card data.
- Full profile JSON unless there is a strong reason.
- Gate codes or access instructions unless encrypted and explicitly required.

Recommended protocol:

```swift
protocol SecureStore {
    func read(_ key: SecureStoreKey) throws -> Data?
    func write(_ data: Data, for key: SecureStoreKey) throws
    func delete(_ key: SecureStoreKey) throws
}
```

## Local Cache and Persistence

Cache goals:

- Faster app launch.
- Last-known non-sensitive reservations.
- Recent searches.
- User preferences.
- Notification inbox snapshot.

Do not cache:

- Payment secrets.
- Full payment method details beyond PSP-safe display data.
- Gate codes/access instructions unless product and security approve encrypted storage.
- Password reset tokens.

Recommended strategy:

- Use `URLCache` for HTTP caching only if backend cache headers are explicit.
- Use SwiftData for simple domain caches if iOS 17+ is the minimum.
- Use SQLite/GRDB or Core Data if supporting older iOS versions or requiring more explicit migration control.
- Add cache schema versioning from day one.
- Use stale labels for cached reservation and search data.

## MapKit and CoreLocation Strategy

Use:

- `CLLocationManager` through a small permission/location service.
- MapKit for map rendering, pins, clusters, and user location.
- Manual search fallback when permission is denied.
- Background location only if a future feature clearly requires it. Do not request background location for MVP parking search.

Recommended protocols:

```swift
protocol LocationPermissionService {
    var authorizationStatus: LocationAuthorizationStatus { get }
    func requestWhenInUseAuthorization() async
    func currentLocation() async throws -> CLLocationCoordinate2D
}

protocol MapSearchCoordinator {
    func search(in region: MapRegion, filters: LocationSearchFilters) async throws -> APIPage<LocationSearchResult>
}
```

## APNs and Push Notification Strategy

Required:

- APNs entitlement and provisioning.
- User notification permission flow.
- Device token registration with backend using platform `IOS`.
- Token refresh on launch and token change.
- Token deactivation on logout when backend supports it.
- Deep link routing from notification payloads.
- Privacy-safe payload design.

Backend gaps to close before real APNs rollout:

- Configure APNs provider credentials outside the repo and smoke-test delivery.
- Keep device token unregister/deactivate endpoint coverage green.
- Token environment metadata: sandbox vs production.
- Bundle ID/team ID metadata if backend needs it.
- Notification preference enforcement server-side.

## Analytics Strategy

Principles:

- Analytics must be privacy-aware and consent-aware.
- Do not send precise location, license plate, payment details, gate codes, or raw support message content.
- Use stable event names and typed properties.
- Include app version, build, environment, role, and coarse screen context.
- Include request IDs only for diagnostics, not broad behavioral tracking.

Recommended events:

- `search_submitted`
- `location_selected`
- `reservation_quote_requested`
- `reservation_create_submitted`
- `reservation_created`
- `payment_intent_created`
- `payment_confirmed`
- `support_ticket_created`
- `push_permission_changed`
- `session_expired`

## Error Handling Strategy

Use typed errors:

- `unauthorized`
- `forbidden`
- `validation(fields:)`
- `notFound`
- `conflict(code:)`
- `rateLimited(retryAfter:)`
- `serverUnavailable`
- `networkUnavailable`
- `decodingFailed`
- `unknown(requestId:)`

Required UI mapping:

- Auth errors return user to sign-in.
- Validation errors attach to form fields.
- Reservation conflicts show a specific unavailable/changed-time message.
- Payment unknown states require refresh from backend before retrying.
- Server failures include a support-safe request ID where useful.

## Logging and Observability

Use Apple `Logger` / `OSLog`:

- Redact PII and secrets.
- Include request ID, endpoint name, status, duration, and retry count.
- Do not log access tokens, cookies, XSRF tokens, payment secrets, license plates, gate codes, or full addresses.
- Capture crashes through a production crash reporting provider before TestFlight external testing.
- Add nonfatal error reporting for API decode failures and payment return failures.

## Dependency Injection

Use a lightweight app container:

- `AppContainer` owns live implementations.
- Preview/test containers provide stubs.
- Environment values are injected once at launch.
- View models receive protocol dependencies.

Avoid:

- Global mutable singletons for business services.
- Static network clients in views.
- Runtime service locator calls scattered through features.

## Testing Architecture

Test layers:

- Unit tests for formatting, validation, DTO decoding, idempotency key generation, and error mapping.
- View model tests for loading/empty/error states and workflows.
- API contract tests against backend fixtures/OpenAPI.
- Integration tests with a local or mocked backend.
- UI tests for critical flows.
- Accessibility tests for core screens.
- Performance tests for map search and reservation flow.

Use deterministic:

- `Clock`
- ID generator
- API stubs
- Location service
- Keychain store
- Payment adapter

## CI/CD Expectations

Minimum CI gates:

- Swift format/lint if selected by the team.
- iOS build for Debug and Release configurations.
- iOS unit tests.
- Key UI smoke tests.
- Backend `mvn verify`.
- API contract validation.
- No mock payment provider in production config.
- No hardcoded production secrets.
- Privacy manifest present.
- App Store archive validation before release candidate.

Example commands:

```bash
mvn -f apps/backend/pom.xml verify
npm run build
xcodebuild -project apps/ios/SpotLink.xcodeproj -scheme SpotLinkApp -configuration Debug build
xcodebuild -project apps/ios/SpotLink.xcodeproj -scheme SpotLinkApp -destination 'platform=iOS Simulator,name=iPhone 16' test
swift test
```

Use the exact workspace/project path once the iOS implementation exists.

## TestFlight and App Store Release Path

1. Internal debug build with local/staging backend.
2. Internal TestFlight with staging backend, mock payment only if clearly labeled and not externally distributed.
3. External TestFlight with production-like backend, APNs sandbox/production configured, real payment test mode, crash reporting, privacy links.
4. Release candidate archive with production backend, production APNs, production payment provider, production signing, privacy manifest, App Store metadata, and monitoring.
5. Phased App Store rollout with crash, payment, reservation, and support dashboards monitored.
