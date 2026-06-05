# QA Test Strategy

Date: 2026-04-22

## QA Goal

SpotLink iOS should not reach external TestFlight until core customer flows are reliable under real mobile conditions: slow networks, session expiry, denied permissions, payment uncertainty, push token changes, and reservation race conditions.

This strategy covers backend, iOS, API contract, manual QA, and CI gates. The native SwiftUI app now has a deterministic simulator QA path, but real staging, physical-device APNs, Apple signing, PSP, and owner-approved legal content remain outside the automated simulator gate.

## Current iOS Pre-Staging Gate

`make pre-staging-gate` is the local proof bar before staging deployment work resumes. It runs the full release gate, then focused pre-staging hardening checks. For iOS, this currently proves:

- SwiftPM unit/model tests pass, including API decoding, payment capabilities, reservation idempotency retry behavior, offline search, slow-search loading state, unauthorized session sign-out, push-token lifecycle, and privacy-safe logging.
- Xcode `SpotLinkApp` unit tests and UI tests run against the app target instead of a web or mocked binary.
- UI tests launch with `--uitesting` and reset local session artifacts to avoid stale simulator Keychain state.
- Authenticated UI tests use the explicit `--spotlink-uitest-authenticated` DEBUG-only fixture. This seeds a local customer session and skips remote logout only for that fixture, keeping normal Debug, Staging, and Release behavior unchanged.
- Simulator UI coverage reaches signed-out login, registration legal links and disabled submit state, authenticated search shell, profile privacy/support/account-deletion surfaces, confirmation for destructive deletion request, and logout back to auth.

Command:

```bash
env PATH=/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin make pre-staging-gate
```

This gate is not a substitute for physical-device QA, signed TestFlight install validation, real APNs token delivery, real PSP authorization, or deployed staging smoke tests.

## Backend Tests That Must Exist

Current backend tests cover foundation auth/profile, reservation idempotency/overlap, and payment intent flow. Expand coverage before external TestFlight.

Required backend test areas:

- Auth: login success/failure, logout, session expiry, registration, duplicate email, password reset request/complete.
- Authorization: CUSTOMER, OPERATOR, SUPPORT, ADMIN matrix for every protected endpoint.
- Profile: self update, preferences update, account status.
- Vehicles: CRUD, ownership enforcement, validation, deletion while reservation exists.
- Location search: query, radius, coordinates, resource type, EV, availability window, pagination.
- Operator inventory: location/resource create/update ownership checks.
- Reservation quote: invalid windows, past start, unavailable resource, vehicle incompatibility, quote expiration behavior.
- Reservation create: idempotency replay, in-progress conflict, overlap race, unavailable resource, invalid vehicle.
- Payment: intent idempotency, declined cards, SCA/action required, confirmation replay, unknown provider response.
- Support: ticket ownership, message ordering, support/admin role access once added.
- Notifications: token registration, duplicate token reassignment, mark-read ownership.
- Admin: access restrictions, audit filters, user pagination.
- Analytics: schema validation, rate limit behavior once added.
- Error envelope: stable codes, validation details, request ID.

Command:

```bash
mvn -f apps/backend/pom.xml verify
```

## iOS Unit Tests

Required:

- DTO encoding/decoding for every backend response used by iOS.
- Enum raw value mapping and unknown enum policy.
- ISO-8601 date decoding and timezone display formatting.
- Money formatting from minor units and currency.
- Request ID generation.
- Idempotency key generation and persistence for create flows.
- API error mapping.
- Field validation for auth, vehicle, support, and reservation forms.
- Keychain store wrapper with mock implementation.
- Cache versioning/migration logic.

Command examples after iOS project exists:

```bash
xcodebuild -project apps/ios/SpotLink.xcodeproj -scheme SpotLinkApp -destination 'platform=iOS Simulator,name=iPhone 16' test
swift test
```

Use whichever command matches the final project layout.

## iOS View Model Tests

Required view model coverage:

- Auth session restore: unknown, signed out, signed in, expired.
- Login: success, invalid credentials, network failure, validation failure.
- Search: initial load, permission denied, manual search, empty results, retry, filters.
- Location detail: loading, unavailable, no resources, selected resource.
- Reservation quote: valid quote, expired quote, unavailable resource, incompatible vehicle.
- Reservation creation: idempotency key reuse after timeout, double tap prevention, success, conflict.
- Payment: authorized, failed, requires action, return from deep link, refresh unknown state.
- Vehicles: add/edit/delete, validation, empty state.
- Notifications: unread count, mark read, deep link route.
- Support: ticket create, message send optimistic state, retry failure.
- Operator/admin: dashboard load, forbidden, empty states.

Rules:

- Tests should not hit the real network.
- Use deterministic clocks and IDs.
- Assert user-visible state, not only method calls.

## API Contract Tests

Purpose:

- Prevent backend changes from silently breaking iOS decoding or workflow assumptions.

Required:

- Generate or maintain OpenAPI for the backend version used by iOS.
- Validate iOS DTO fixtures against actual backend responses.
- Validate backend response bodies against OpenAPI.
- Add contract fixtures for success and error cases.
- Include pagination and validation-error examples.

Command examples:

```bash
mvn -f apps/backend/pom.xml verify
```

Optional tools to consider:

- OpenAPI generator validation.
- Schemathesis or equivalent API fuzz/contract tests.
- Snapshot tests for representative JSON fixtures.

## UI Tests

Critical iOS UI tests:

- First launch signed out.
- Customer registration.
- Login and session restore.
- Search by query.
- Search near me with mocked location permission granted.
- Search with location permission denied.
- Location detail open from result.
- Reservation quote and create using mock backend.
- Payment authorized using mock payment adapter.
- Reservation detail shows confirmed state.
- Vehicle add/edit/delete.
- Support ticket create and message send.
- Notification deep link to reservation or support ticket.
- Logout clears session.

Command:

```bash
xcodebuild -project apps/ios/SpotLink.xcodeproj -scheme SpotLinkApp -destination 'platform=iOS Simulator,name=iPhone 16' test
```

Current automated simulator coverage includes:

- Signed-out launch to the login screen.
- Registration legal links and disabled submit state before required fields/terms.
- Deterministic authenticated session restore through the DEBUG-only UI fixture.
- Search shell reachable after authenticated launch.
- Profile legal/support/account-deletion surfaces reachable.
- Account deletion request requires confirmation before sending.
- Logout returns to the auth state without depending on a live backend.

The deterministic fixture must remain test-only. It is enabled only by UI-test launch arguments/environment and must not be used to demonstrate real backend login success.

## Smoke Tests

Run on every release candidate:

- App launches.
- Correct environment is configured.
- Health endpoint reachable.
- Login works.
- `GET /auth/me` works after relaunch.
- Location search returns results or correct empty state.
- Reservation quote works.
- Reservation creation works once and does not duplicate on retry.
- Payment test flow completes.
- Push permission prompt appears only in context.
- Device token registration succeeds on real device.
- Logout clears local session.

Backend smoke:

```bash
curl -sS -i https://staging-api.example.com/api/health
```

Replace URL with the real environment.

## Regression Tests

Maintain regression suites for:

- Reservation overlap race.
- Payment confirmation unknown state.
- Session expiry during reservation creation.
- App killed during payment action and reopened through deep link.
- Network timeout after reservation create request reaches server.
- Device token changes after reinstall.
- Vehicle compatibility filtering.
- Timezone and daylight saving boundary reservations.

## Accessibility Tests

Automated:

- Snapshot or UI tests with larger Dynamic Type sizes.
- VoiceOver label checks on critical controls where feasible.
- Color contrast checks through design review tooling.

Manual:

- VoiceOver full flow: search, reserve, pay, support.
- Dynamic Type at accessibility sizes.
- Reduce Motion enabled.
- Increase Contrast enabled.
- Dark mode.
- Keyboard/external keyboard navigation where practical.

Acceptance:

- No critical action is only color-coded.
- Map results have an accessible list alternative.
- Reservation/payment errors are announced.
- Minimum touch targets are met.

## Performance Tests

Measure:

- Cold launch time.
- Auth session restore time.
- Search first result time.
- Map pin rendering with 50, 100, and 500 results.
- Reservation quote latency.
- Reservation create latency.
- Payment return-to-confirmed latency.
- Memory while panning map.
- Battery impact during location use.

Targets should be set after baseline measurement. Initial practical goals:

- Cold launch to usable signed-out state under 2 seconds on supported devices.
- Search results visible under 2 seconds on normal LTE/Wi-Fi with backend ready.
- No main-thread hangs over 200 ms during map/list scrolling.

## Offline and Network Failure Tests

Cases:

- Launch offline signed out.
- Launch offline with previous signed-in cached shell.
- Search offline.
- Quote offline.
- Reservation create timeout before response.
- Reservation create timeout after server success.
- Payment create timeout.
- Payment confirm timeout.
- Backend 500, 502, 503, and 504.
- Rate limited response with `Retry-After`.
- Captive portal or invalid JSON response.

Acceptance:

- No duplicate reservations.
- No false successful payments.
- User can recover with retry or refresh.
- Request ID is available for support on server errors.

Current Swift coverage includes search offline, slow search loading, payment-unavailable fallback to pay-on-arrival, reservation idempotency-key reuse after a failed create attempt, and unauthorized session sign-out. Real carrier-network interruption, captive portal, and background/foreground retry behavior still require simulator network conditioning or physical-device QA.

## Push Notification Tests

Simulator can cover routing with injected payloads, but real device tests are required for APNs.

Required:

- Permission not requested on first launch.
- Permission requested in context.
- Permission denied path.
- APNs token registration after sign-in.
- Token refresh on app relaunch.
- Token deactivation on logout once backend supports it.
- Notification opens correct screen.
- Badge count updates and clears.
- Privacy-safe payload review.

## Location Permission Tests

Cases:

- Not determined.
- When In Use granted.
- Denied.
- Restricted.
- Approximate location.
- Location unavailable/timeout.
- Manual search fallback.

Acceptance:

- App remains useful without location.
- Permission copy matches actual use.
- No repeated nagging after denial.

## Payment Mock Tests

Internal/debug only:

- Authorized card.
- Declined card.
- SCA/action required card.
- Payment provider unavailable.
- Duplicate payment intent idempotency.
- Confirm already authorized intent.

Before external TestFlight:

- Repeat equivalent tests through PSP test mode or production-like sandbox.
- Verify deep link return and server webhook reconciliation.

## Reservation Idempotency Tests

Backend:

- Same customer, same idempotency key, same body returns same reservation.
- Same customer, same key, different body returns conflict or documented behavior.
- Different customer, same key does not collide.
- Timeout/retry after server success returns original reservation.
- Concurrent duplicate submits create one reservation.

iOS:

- Double-tap create button sends one logical operation.
- App stores in-flight idempotency key until terminal response.
- App reuses idempotency key after network timeout.
- App does not reuse key for a new distinct reservation attempt.

## App Store and TestFlight Release Validation

Before internal TestFlight:

- Debug and release builds compile.
- Unit tests pass.
- Core UI smoke tests pass.
- Backend staging health passes.
- Crash reporting configured.
- No secrets in repository.

Before external TestFlight:

- APNs works on physical device.
- Payment provider test mode works.
- Privacy policy and support URL live.
- Account deletion path defined.
- Accessibility pass for core customer flow.
- Production-like backend migration verified.
- Rate limiting enabled for auth and analytics.

Before App Store:

- Archive validation passes.
- App privacy manifest complete.
- Permission descriptions complete.
- App Store screenshots and metadata ready.
- Export compliance answered.
- Age rating answered.
- Review notes include test account and payment test instructions if required.
- Monitoring dashboard ready.

## Manual QA Checklist

Customer:

- Create account.
- Sign in/out.
- Reset password.
- Search by address.
- Search near current location.
- Filter by time, vehicle, EV, price/distance when available.
- Open location and resource detail.
- Add vehicle.
- Quote reservation.
- Create reservation.
- Complete payment.
- View access instructions.
- Cancel reservation.
- Create support ticket.
- Receive/open notification.

Operator:

- Sign in as operator.
- View dashboard.
- View resource health.
- Create/update location and resource if enabled.
- Review current/upcoming reservations once supported.
- Open operator-related support ticket once supported.

Support:

- View queue once supported.
- Open ticket.
- Reply.
- Escalate/close once supported.

Admin:

- View dashboard.
- View users.
- View audit events.
- Confirm unauthorized users cannot access admin screens.

Cross-cutting:

- Dark mode.
- Dynamic Type.
- VoiceOver.
- Offline.
- Slow network.
- Session expiry.
- App kill/relaunch.
- Timezone boundary.

## Automated CI Gates

Minimum gate set:

```bash
mvn -f apps/backend/pom.xml verify
npm run build
xcodebuild -project apps/ios/SpotLink.xcodeproj -scheme SpotLinkApp -configuration Debug build
xcodebuild -project apps/ios/SpotLink.xcodeproj -scheme SpotLinkApp -destination 'platform=iOS Simulator,name=iPhone 16' test
```

Add once available:

- Swift lint/format.
- OpenAPI contract validation.
- UI smoke suite.
- Accessibility smoke suite.
- Release archive validation.
- Secret scanning.
- Production config assertions: no mock payments, no local API URL, analytics consent honored.
