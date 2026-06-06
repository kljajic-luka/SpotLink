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
- Current behavior: Generated Springdoc route coverage for mobile-critical endpoints and `/api/v1` aliases is now tested by `MobileApiContractTest`. The hand-authored OpenAPI draft is still not a complete generated schema source of truth.
- Desired mobile behavior: Backend-generated OpenAPI includes mobile examples, enums, errors, and no-content responses.
- Backend files likely affected: controller annotations, DTO annotations, springdoc config, tests.
- iOS impact: Covered route/fixture drift now fails the pre-staging gate; deeper schema drift can still slip through until generated schemas are exported and compared.
- Suggested acceptance criteria: CI exports generated OpenAPI, validates fixture compatibility, and either compares the draft or regenerates it from backend annotations.

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
- Current behavior: Device token register/reactivate/unregister lifecycle exists, raw token logging is avoided, server-side preference enforcement is in place, and backend APNs-ready provider/config/metrics scaffolding exists without committed credentials.
- Desired mobile behavior: Register, update, deactivate, track sandbox/production, and deliver via APNs on physical devices.
- Backend files likely affected: notification module, provider implementation, notification preference policy.
- iOS impact: Logout/reinstall/token-rotation cleanup and backend delivery boundaries are shaped, but real delivery and physical-device APNs validation remain blocked.
- Suggested acceptance criteria: Existing token lifecycle, preference-policy, and push readiness tests stay green, Apple entitlements/credentials are configured outside the repo, and APNs can be smoke-tested in staging once a real backend runtime exists.

## MOB-BE-008

- Area: Payment intent lifecycle
- Priority: P0
- Current behavior: Create, confirm, cancel/void authority, admin refund marker shaping, provider event records, and payment capabilities exist. There is still no real PSP, webhook reconciliation, settlement reporting, or production provider flow.
- Desired mobile behavior: Intent get/status refresh, cancel/void where applicable, PSP webhook reconciliation, `REQUIRES_ACTION` return handling, production config guard.
- Backend files likely affected: payment module, config, migrations, tests.
- iOS impact: The client can hide/disable online payment when the backend lacks a provider, but real payment interruption/reconciliation handling is still blocked by PSP implementation.
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
- Current behavior: Auth login, mobile token, registration, password reset, and analytics ingestion have configurable local rate limits with `429`, `Retry-After`, and `RATE_LIMITED`. Search/geocode, reservation, payment, support, account lockout, device-risk scoring, and provider/WAF limits remain future hardening.
- Desired mobile behavior: Rate limits with `429`, `Retry-After`, stable error code, and broader abuse policy coverage.
- Backend files likely affected: security/config filters, controllers, exception handling.
- iOS impact: Mobile retry policy cannot distinguish throttling from generic failures.
- Suggested acceptance criteria: Existing focused rate-limit tests stay green; future provider/WAF and account-risk controls have staged rollout tests.

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
- Current behavior: Analytics endpoint is public, schema validated at the envelope level, and rate-limited, but still accepts a free-form properties map.
- Desired mobile behavior: Event allowlist, consent state, app/build/platform fields, rate limiting, and PII stripping.
- Backend files likely affected: analytics module, security/rate limit config.
- iOS impact: Analytics should remain disabled for external users until privacy behavior is clear.
- Suggested acceptance criteria: Invalid events are rejected; opt-out events are not stored; rate limit exists.
