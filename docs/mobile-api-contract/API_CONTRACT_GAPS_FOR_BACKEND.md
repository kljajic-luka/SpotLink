# API Contract Gaps for Backend

Date: 2026-04-22

## Purpose

This report lists backend gaps that currently block or weaken a serious native iOS app. It is scoped to API contract and mobile platform concerns.

## Implemented in Mobile Auth Hardening Pass

The backend now includes:

- Refresh-token persistence in `refresh_tokens`.
- Hashed refresh-token storage.
- Access token plus refresh token issuance from `/api/auth/token`.
- Refresh-token rotation from `/api/auth/token/refresh`.
- Refresh-token revocation from `/api/auth/token/revoke`.
- Mobile refresh token revocation through `/api/auth/logout` when a refresh token is supplied.
- `/api/v1/...` aliases for the mobile-critical API surface.

The gaps below now track remaining hardening work.

## MOB-BE-001

- Area: Mobile auth lifecycle
- Priority: P2
- Current behavior: Access and refresh token issuance, refresh rotation, refresh revocation, and logout refresh-token revocation are implemented. Device metadata is stored. There is no user-facing session list or admin session inspection yet.
- Desired mobile behavior: Add session listing and remote session revocation for user support and account-security UX.
- Backend files likely affected: `apps/backend/src/main/java/com/spotlink/auth`, `apps/backend/src/main/java/com/spotlink/security`, `apps/backend/src/main/resources/db/migration`, `application.properties`.
- iOS impact: Basic secure session lifecycle works; advanced account-security UI cannot list devices yet.
- Suggested acceptance criteria: Authenticated user can list active mobile sessions and revoke a chosen session.

## MOB-BE-002

- Area: Production JWT/session hardening
- Priority: P0
- Current behavior: Backend has a default JWT secret fallback and default 7-day expiry.
- Desired mobile behavior: Production must fail startup if `JWT_SECRET` is missing or weak, and access-token TTL should be short.
- Backend files likely affected: `AppProperties.java`, `JwtService.java`, `application.properties`, profile-specific config.
- iOS impact: Token compromise risk is high and hard to remediate.
- Suggested acceptance criteria: Production profile cannot start with default secret; test profile can use controlled test secret.

## MOB-BE-003

- Area: API versioning
- Priority: P2
- Current behavior: Existing `/api/...` routes are preserved and `/api/v1/...` aliases are implemented for the mobile-critical API surface using the same controller methods.
- Desired mobile behavior: Keep `/api/v1` as the mobile contract surface and add a formal deprecation policy for unversioned mobile usage.
- Backend files likely affected: controllers, routing config, OpenAPI config.
- iOS impact: iOS can target `/api/v1`; long-term compatibility still needs policy and generated OpenAPI.
- Suggested acceptance criteria: CI-generated OpenAPI includes `/api/v1` routes and iOS base path targets that version.

## MOB-BE-004

- Area: OpenAPI generation from backend
- Priority: P1
- Current behavior: Contract is hand-authored and may drift.
- Desired mobile behavior: Backend-generated OpenAPI includes mobile examples, enums, errors, and no-content responses.
- Backend files likely affected: controller annotations, DTO annotations, springdoc config, tests.
- iOS impact: DTO drift has already occurred; generated schemas reduce ambiguity.
- Suggested acceptance criteria: CI exports OpenAPI and validates fixture compatibility.

## MOB-BE-005

- Area: Response consistency
- Priority: P1
- Current behavior: Success responses are raw DTOs or `204`; `ApiEnvelope` exists but is not used consistently.
- Desired mobile behavior: Either keep raw DTOs as the stable contract and document that choice, or introduce a versioned envelope consistently.
- Backend files likely affected: all controllers, API docs.
- iOS impact: iOS needs a stable decoding strategy.
- Suggested acceptance criteria: Contract states raw DTO success shape for v1, and tests lock it.

## MOB-BE-006

- Area: Geospatial map search
- Priority: P0
- Current behavior: Search accepts coordinates/radius/time window but foundation behavior is not full map-grade viewport/radius/availability ranking.
- Desired mobile behavior: Viewport or bounds search, radius filtering, distance sorting, availability filtering, price ranking, and geospatial index.
- Backend files likely affected: `LocationController`, `LocationService`, repositories, database migrations.
- iOS impact: Search/map is the primary customer screen.
- Suggested acceptance criteria: Tests cover viewport, center/radius, availability window, EV filter, resource type filter, and sorting.

## MOB-BE-007

- Area: APNs device token lifecycle
- Priority: P1
- Current behavior: Device token registration exists. No delete/deactivate endpoint, APNs environment, bundle metadata, or APNs provider implementation is present.
- Desired mobile behavior: Register, update, deactivate, track sandbox/production, enforce preferences, and deliver via APNs.
- Backend files likely affected: notification module, database migration, provider implementation.
- iOS impact: Logout/reinstall/token-rotation behavior is incomplete.
- Suggested acceptance criteria: Register/deactivate tests pass and APNs provider can be smoke-tested in staging.

## MOB-BE-008

- Area: Payment intent lifecycle
- Priority: P0
- Current behavior: Create and confirm exist. No get, cancel, refresh, webhook reconciliation, refund, capture, or production provider flow.
- Desired mobile behavior: Intent get/status refresh, cancel/void where applicable, PSP webhook reconciliation, `REQUIRES_ACTION` return handling, production config guard.
- Backend files likely affected: payment module, config, migrations, tests.
- iOS impact: Payment uncertainty after app kill, redirect, or timeout cannot be resolved cleanly.
- Suggested acceptance criteria: iOS can refresh payment status after any interruption and production cannot run with mock provider enabled.

## MOB-BE-009

- Area: Account deletion and privacy endpoints
- Priority: P1
- Current behavior: Account deletion request intake exists through support tickets, and admins can process approved deletion tickets with anonymization/revocation. No user-visible deletion status/export/privacy-choice endpoint exists yet.
- Desired mobile behavior: In-app account deletion request with status/retention messaging and owner-approved privacy-choice flows where legally required.
- Backend files likely affected: user/account module, support/admin module, audit logging.
- iOS impact: Request flow is usable, but richer status/privacy-choice UX remains blocked until product/legal defines it.
- Suggested acceptance criteria: Authenticated user can request deletion; admin/support can audit and process it; app receives clear request and completion/unauthorized status.

## MOB-BE-010

- Area: Rate limiting and abuse prevention
- Priority: P1
- Current behavior: No visible rate limiting on auth, reset, search/geocode, reservation, payment, support, or analytics.
- Desired mobile behavior: Rate limits with `429`, `Retry-After`, and stable error code.
- Backend files likely affected: security/config filters, controllers, exception handling.
- iOS impact: Mobile retry policy cannot distinguish throttling from generic failures.
- Suggested acceptance criteria: Rate-limit tests assert `429`, `Retry-After`, and `RATE_LIMITED` code.

## MOB-BE-011

- Area: Idempotency header support
- Priority: P1
- Current behavior: Reservation and payment creation require body `idempotencyKey`; `X-Idempotency-Key` is allowed/exposed but not documented as consumed.
- Desired mobile behavior: Backend accepts `X-Idempotency-Key` for idempotent mutations and optionally mirrors it in the body during transition.
- Backend files likely affected: reservation/payment controllers/services, idempotency service, tests.
- iOS impact: API client can centralize idempotency instead of passing it through every DTO.
- Suggested acceptance criteria: Header-only and body-only compatibility tests pass during migration.

## MOB-BE-012

- Area: Support ticket detail and staff workflows
- Priority: P2
- Current behavior: List tickets, create ticket, list/create messages exist. No ticket detail endpoint or support staff queue.
- Desired mobile behavior: Ticket detail, close/reopen, staff assignment, operator/support/admin visibility rules.
- Backend files likely affected: support module, security tests.
- iOS impact: Support UI remains shallow and role-specific workflows are blocked.
- Suggested acceptance criteria: Customer and support-role tests cover ticket detail and message access.

## MOB-BE-013

- Area: Parking resource detail endpoint
- Priority: P2
- Current behavior: Resources are listed by location only.
- Desired mobile behavior: Detail by resource ID or by location/resource path.
- Backend files likely affected: location controller/service.
- iOS impact: Deep links to a resource and reservation recovery flows are awkward.
- Suggested acceptance criteria: `GET /api/locations/{locationId}/resources/{resourceId}` or equivalent returns `ParkingResource`.

## MOB-BE-014

- Area: Analytics privacy and schema validation
- Priority: P2
- Current behavior: Analytics endpoint is public and accepts free-form properties map.
- Desired mobile behavior: Event allowlist, consent state, app/build/platform fields, rate limiting, and PII stripping.
- Backend files likely affected: analytics module, security/rate limit config.
- iOS impact: Analytics should remain disabled for external users until privacy behavior is clear.
- Suggested acceptance criteria: Invalid events are rejected; opt-out events are not stored; rate limit exists.
