# SpotLink Mobile API Contract Package

Date: 2026-06-05

## Purpose

This package is the authoritative mobile API contract reference for the current SpotLink backend snapshot. It exists so the native iOS implementation can align DTOs, endpoint paths, authentication behavior, no-content handling, idempotency, error decoding, pagination, and contract tests without guessing from incomplete Swift models.

The contract is now partially machine-checked. It is not a full schema-diff gate yet.

## Machine-Checked Gate

Run:

```bash
make validate-mobile-api-contract
```

This target is part of `make pre-staging-gate` and runs:

- backend `MobileApiContractTest`, which reads generated Springdoc JSON from `/api/openapi` in the test context and asserts mobile-critical routes plus `/api/v1` aliases exist.
- SwiftPM `MobileApiFixtureDecodingTests`, which loads checked-in response fixtures from `docs/mobile-api-contract/json-fixtures` and decodes them through the actual Swift models used by the app.

Backend coverage currently enforces:

- generated OpenAPI route coverage for auth/token lifecycle, profile, vehicles, search/location, reservations, payment capabilities/intents/cancel, support, notifications/device token register-unregister, operator/admin summaries, account-deletion admin processing, and public health.
- standard error envelope fields: `status`, `code`, `message`, `requestId`, `timestamp`, and `path`.
- `X-Request-Id` acceptance and response propagation.
- representative `204 No Content` endpoints returning an empty body.
- representative `/api/v1` aliases behaving like their unversioned routes.

iOS fixture coverage currently decodes:

- `auth-login-response.json` -> `AuthResponseEnvelope`
- `auth-token-response.json` -> `MobileTokenResponse`
- `auth-me-response.json` -> `UserProfile`
- `profile-response.json` -> `UserProfileDetails`
- `vehicle-response.json` -> `VehicleProfile`
- `location-search-response.json` -> `APIPage<LocationSearchResult>`
- `parking-location-response.json` -> `ParkingLocation`
- `parking-resource-response.json` -> `ParkingResource`
- `reservation-quote-response.json` -> `ReservationQuote`
- `reservation-response-confirmed.json` and `reservation-response-cancelled.json` -> `Reservation`
- `payment-capabilities-response.json` -> `PaymentCapabilities`
- `payment-intent-response.json` -> `PaymentIntent`
- `support-ticket-response.json` -> `SupportTicket`
- `support-message-response.json` -> `SupportMessage`
- `notification-response.json` -> `SpotLinkNotification`
- `notification-unread-count-response.json` -> `NotificationUnreadCount`
- `paginated-response-example.json` -> `APIPage<SpotLinkNotification>`
- `operator-dashboard-response.json` -> `OperatorDashboardSummary`
- `admin-dashboard-response.json` -> `AdminDashboardSummary`
- `standard-error-response.json` and `validation-error-response.json` -> `APIErrorEnvelope`

## Source Files Inspected

Primary sources:

- `docs/ios-enterprise-readiness/API_IOS_COMPATIBILITY_AUDIT.md`
- `docs/ios-enterprise-readiness/IMPLEMENTATION_ISSUE_BACKLOG.md`
- `apps/backend/src/main/java/com/spotlink`
- `apps/backend/src/main/resources/application.properties`
- `apps/frontend/src/app/foundation`
- `apps/ios/SpotLink/Sources/SpotLink`

Backend authority files:

- Controllers under `auth`, `user`, `vehicle`, `location`, `reservation`, `payment`, `support`, `notification`, `operator`, `admin`, `analytics`, and `core`.
- DTO records and enum classes in each backend module.
- `SecurityConfig`, `JwtService`, `ApiErrorResponse`, `ApiPage`, `RequestCorrelationFilter`, and `GlobalExceptionHandler`.

## Current Confidence Level

Confidence is high for the covered route and response-fixture surface.

Generated Springdoc route coverage is enforced by tests. The hand-authored `openapi-mobile-v1.yaml` remains a readable draft/reference and is not yet the generated source of truth for every schema, enum, example, or error response.

If another agent changes endpoint paths or DTOs, update the generated-route test, fixtures, Swift models, and draft docs in the same change.

## How iOS Agents Should Use This Package

iOS agents should:

- Treat `SPOTLINK_MOBILE_API_CONTRACT.md` as the readable endpoint behavior guide.
- Use `json-fixtures/` for `Codable` decoding tests and keep `MobileApiFixtureDecodingTests` updated when response fixtures change.
- Use `SWIFT_DTO_ALIGNMENT_GUIDE.md` when naming Swift DTOs and mapping backend enum raw values.
- Update the iOS API client so it handles `204 No Content`, `202 Accepted` with no body, `400 VALIDATION_ERROR`, `401`, `403`, `404`, `409`, and `5xx`.
- Decode backend shapes exactly, especially nested location search, payment confirmation, notification read fields, support enums, and profile/operator/admin metrics.
- Preserve idempotency keys across mobile retry boundaries for reservation and payment creation.

## How Backend Agents Should Use This Package

Backend agents should:

- Treat generated Springdoc `/api/openapi` as the route coverage authority and `openapi-mobile-v1.yaml` as a draft/reference until full generated schema export is adopted.
- Use `API_CONTRACT_GAPS_FOR_BACKEND.md` as the prioritized backend backlog for mobile hardening.
- Avoid changing enum raw values or DTO field names without versioning the API.
- Add missing lifecycle endpoints deliberately rather than forcing iOS to rely on local-only behavior.
- Update `MobileApiContractTest` and fixtures when mobile-facing endpoints or DTOs change.

## Known Limitations

- The backend preserves existing `/api/...` routes and now also exposes `/api/v1/...` aliases for the mobile-critical API surface.
- `/auth/token` returns access and refresh tokens. Refresh-token rotation and revocation endpoints are implemented.
- Payment provider behavior is still mock/non-production grade; no real PSP is integrated.
- APNs provider delivery is scaffolded and credential-free tests cover provider selection, delivery boundaries, invalid-token handling, metrics, and redaction. Real APNs credentials, entitlement, and physical-device delivery validation remain external.
- Search is not yet map-grade for viewport/radius/availability ranking.
- Account deletion request and admin-reviewed fulfillment exist; user-visible deletion status/export/privacy-choice endpoints remain future work.
- Request-only fixtures are not decoded through Swift response models because the app's request DTOs are intentionally `Encodable` only; request encoding is covered by focused Swift service/model tests.
- The OpenAPI file is a hand-authored draft/reference, not a complete generated schema export.
- Full schema/example diffing, generated OpenAPI publication, and fuzz/negative contract testing remain future work before production API governance.
