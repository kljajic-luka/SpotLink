# iOS Contract Test Plan

Date: 2026-04-22

## Purpose

This plan tells the iOS implementation agent how to turn this contract package into automated tests. The goal is to prevent Swift DTO drift from backend DTOs.

## JSON Fixture Decoding Tests

Create tests that load every file in `docs/mobile-api-contract/json-fixtures`.

Required decode targets:

- `auth-login-response.json` -> `AuthResponseDTO`
- `auth-token-response.json` -> `AuthSessionResponseDTO`
- `auth-refresh-request.json` -> `RefreshTokenRequestDTO`
- `auth-revoke-request.json` -> `RevokeTokenRequestDTO`
- `auth-me-response.json` -> `UserProfileDTO`
- `profile-response.json` -> `UserProfileDetailsDTO`
- `vehicle-response.json` -> `VehicleProfileDTO`
- `location-search-response.json` -> `ApiPage<LocationSearchResultDTO>`
- `parking-location-response.json` -> `ParkingLocationDTO`
- `parking-resource-response.json` -> `ParkingResourceDTO`
- `reservation-quote-response.json` -> `ReservationQuoteDTO`
- `reservation-response-confirmed.json` -> `ReservationDTO`
- `reservation-response-cancelled.json` -> `ReservationDTO`
- `payment-intent-response.json` -> `PaymentIntentDTO`
- `support-ticket-response.json` -> `SupportTicketDTO`
- `support-message-response.json` -> `SupportMessageDTO`
- `notification-response.json` -> `NotificationItemDTO`
- `notification-unread-count-response.json` -> `UnreadNotificationCountDTO`
- `operator-dashboard-response.json` -> `OperatorDashboardSummaryDTO`
- `admin-dashboard-response.json` -> `AdminDashboardSummaryDTO`
- `standard-error-response.json` -> `ApiErrorEnvelope`
- `validation-error-response.json` -> `ApiErrorEnvelope`
- `paginated-response-example.json` -> `ApiPage<NotificationItemDTO>`

Done criteria:

- Every fixture decodes.
- Required fields are non-optional in Swift.
- Optional backend fields decode when missing or null.

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
- `204` from device token register succeeds.
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
- Create response decodes no `idempotencyKey` field because backend does not return one.
- Conflict response `RESOURCE_UNAVAILABLE` maps to user-actionable unavailable state.

Done criteria:

- Customer reservation flow DTOs align with backend fixtures.

## Location Search Tests

Cases:

- Search result decodes nested `location` and `resources`.
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
- `204` token registration succeeds.

Done criteria:

- APNs token registration contract is correct before device testing.

## Contract Drift Checks

Recommended:

- Add CI task that runs backend tests and exports OpenAPI once backend generation is available.
- Compare exported OpenAPI against `openapi-mobile-v1.yaml` or regenerate this draft.
- Keep JSON fixtures in sync with backend integration tests.

## Recommended Commands

Use exact project paths once Agent 1 finalizes iOS structure:

```bash
swift test
xcodebuild -project apps/ios/SpotLink.xcodeproj -scheme SpotLink -destination 'platform=iOS Simulator,name=iPhone 16' test
mvn -f apps/backend/pom.xml test
```

## Done Criteria

- All JSON fixtures parse.
- All JSON fixtures decode in Swift tests.
- API client mock tests pass.
- Error handling tests preserve backend `code` and `requestId`.
- Idempotency tests pass.
- Backend `mvn test` passes.
- Any backend DTO change causes a fixture or OpenAPI drift test to fail.
