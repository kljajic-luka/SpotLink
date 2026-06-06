# iOS Contract Test Plan

Date: 2026-06-05

## Purpose

This plan tracks the automated iOS contract coverage that prevents Swift DTO drift from backend DTOs.

Current focused gate:

```bash
make validate-mobile-api-contract
```

The Swift side runs:

```bash
swift test --package-path apps/ios/SpotLink --filter MobileApiFixtureDecodingTests
```

## JSON Fixture Decoding Tests

Implemented tests load response fixtures from `docs/mobile-api-contract/json-fixtures` and decode them through the actual app models.

Current decode targets:

- `auth-login-response.json` -> `AuthResponseEnvelope`
- `auth-token-response.json` -> `MobileTokenResponse`
- `auth-me-response.json` -> `UserProfile`
- `profile-response.json` -> `UserProfileDetails`
- `vehicle-response.json` -> `VehicleProfile`
- `location-search-response.json` -> `APIPage<LocationSearchResult>`
- `parking-location-response.json` -> `ParkingLocation`
- `parking-resource-response.json` -> `ParkingResource`
- `reservation-quote-response.json` -> `ReservationQuote`
- `reservation-response-confirmed.json` -> `Reservation`
- `reservation-response-cancelled.json` -> `Reservation`
- `payment-capabilities-response.json` -> `PaymentCapabilities`
- `payment-intent-response.json` -> `PaymentIntent`
- `support-ticket-response.json` -> `SupportTicket`
- `support-message-response.json` -> `SupportMessage`
- `notification-response.json` -> `SpotLinkNotification`
- `notification-unread-count-response.json` -> `NotificationUnreadCount`
- `operator-dashboard-response.json` -> `OperatorDashboardSummary`
- `admin-dashboard-response.json` -> `AdminDashboardSummary`
- `standard-error-response.json` -> `APIErrorEnvelope`
- `validation-error-response.json` -> `APIErrorEnvelope`
- `auth-lockout-error-response.json` -> `APIErrorEnvelope`
- `paginated-response-example.json` -> `APIPage<SpotLinkNotification>`

Done criteria:

- Every listed response fixture decodes.
- Required fields are non-optional in Swift.
- Optional backend fields decode when missing or null.

Request-only fixtures are not decoded through Swift response models because app request types are `Encodable` only. Keep request encoding covered by service/model tests and add explicit request-fixture encoding checks if the backend starts enforcing generated examples.

## API Client Mock Tests

Use a mock `URLProtocol` or injectable transport.

Cases:

- Adds `Accept: application/json`.
- Adds `Content-Type: application/json` when body exists.
- Adds `X-Request-Id` to every request.
- Adds `Authorization: Bearer <token>` when authenticated.
- Does not require XSRF in bearer mode.
- Encodes query params correctly.
- Handles empty request body for payment confirm and optional reservation cancel.

Done criteria:

- Request construction can be asserted without live network.

## Error Decoding Tests

Cases:

- HTTP `400` with `VALIDATION_ERROR` maps to validation and preserves field details.
- HTTP `401` maps to unauthorized and triggers session-expired flow.
- HTTP `403` maps to forbidden.
- HTTP `404` maps to not found.
- HTTP `409` maps to conflict and preserves backend code.
- HTTP `500` maps to server error and preserves request ID.
- Malformed JSON maps to decoding error.
- Offline `URLError.notConnectedToInternet` maps to offline.

Done criteria:

- `requestId` and `code` remain available to UI/support diagnostics.

## No-Content and Accepted Tests

Cases:

- `204` from password reset request succeeds.
- `204` from password reset completion succeeds.
- `204` from vehicle delete succeeds.
- `204` from notification mark-read succeeds.
- `204` from device token register/unregister succeeds.
- `202` from analytics ingest succeeds with no body.

Done criteria:

- The API client does not attempt to decode arbitrary empty DTOs.

## Idempotency Key Tests

Cases:

- Reservation create request includes body `idempotencyKey`.
- Payment intent create request includes body `idempotencyKey`.
- Client can also attach future-safe `X-Idempotency-Key`.
- A reservation create retry after timeout reuses the same logical key.
- A new distinct reservation attempt gets a new key.

Done criteria:

- Tests prove duplicate taps and timeout retries cannot create duplicate reservation intents client-side.

## Auth State Tests

Cases:

- Login via `/auth/token` stores access token securely.
- Login response includes access token, refresh token, access expiry, refresh expiry, issuedAt, expiresAt, user, and roles.
- Refresh via `/auth/token/refresh` rotates the refresh token and replaces Keychain value atomically.
- Revoke via `/auth/token/revoke` clears Keychain value after backend success.
- Registration does not call token endpoint before registration.
- Restore handles valid token metadata.
- Restore clears expired token metadata.
- Logout clears token and cached profile.
- Backend logout/revoke call is added once backend supports bearer revocation.

Done criteria:

- Auth flow is deterministic with fake clock and secure store.

## Reservation Quote/Create Tests

Cases:

- Quote request encodes `Date` as ISO-8601.
- Quote response decodes `subtotalCents`, `feesCents`, `discountCents`, and `expiresAt`.
- Create request includes `paymentMode`.
- Create response decodes no `idempotencyKey` field because backend does not return one.
- Conflict response `RESOURCE_UNAVAILABLE` maps to user-actionable unavailable state.

Done criteria:

- Customer reservation flow DTOs align with backend fixtures.

## Location Search Tests

Cases:

- Search result decodes nested `location` and `resources`.
- `ParkingResource` decodes `capacity`, `confirmationMode`, and supported payment modes.
- `startingPriceCents` decodes as optional.
- `distanceKm` decodes as optional.
- Geocode suggestion decodes `id`, `address`, `coordinates`, `accuracyMeters`.
- Query params support repeated `resourceTypes` values or a backend-approved format.

Done criteria:

- Search DTO tests fail if iOS flattens backend search results.

## Notification and Device Token Tests

Cases:

- Notification item decodes `read`, not `readFlag`.
- Mark-read uses `POST`, not `DELETE`.
- Device token registration sends platform `IOS`.
- Device token unregister sends platform `IOS`.
- `204` token register/unregister succeeds.

Done criteria:

- APNs token lifecycle contract is correct before device testing.

## Contract Drift Checks

Implemented:

- CI runs backend `MobileApiContractTest` and Swift `MobileApiFixtureDecodingTests`.
- `make pre-staging-gate` runs `make validate-mobile-api-contract`.
- JSON response fixtures are now part of the SwiftPM contract gate.

Remaining:

- Export generated OpenAPI as an artifact.
- Compare exported schemas/examples against `openapi-mobile-v1.yaml` or regenerate the draft.
- Add negative/fuzz contract testing once the staging runtime exists.

## Recommended Commands

```bash
make validate-mobile-api-contract
swift test --package-path apps/ios/SpotLink
xcodebuild -project apps/ios/SpotLink.xcodeproj -scheme SpotLinkApp -destination 'platform=iOS Simulator,name=iPhone 16' test
mvn -f apps/backend/pom.xml test
```

## Done Criteria

- Listed response fixtures decode in Swift tests.
- API client mock tests pass.
- Error handling tests preserve backend `code` and `requestId`.
- Idempotency tests pass.
- Backend `mvn test` passes.
- Any covered backend route or DTO change causes a fixture or OpenAPI drift test to fail.
