# SpotLink Backend Foundation Migration

Date: 2026-04-22

## Scope

This migration creates a clean SpotLink backend foundation under:

- `apps/backend`

The backend is intentionally foundation-only. It provides secure application structure, persistence, API contracts, and extensible service boundaries for the SpotLink parking MVP without carrying over Rentoza car-rental workflows.

## Backend Repo Audit

Before this work, SpotLink had:

- Angular frontend foundation in `apps/frontend`.
- Frontend API client configured for `/api`.
- Cookie credential interceptor with `XSRF-TOKEN` and `X-XSRF-TOKEN`.
- Raw DTO and `ApiPage` response expectations.
- No backend app under `apps/backend`.

The frontend foundation drove backend contracts for auth, user profile, vehicles, locations, reservations, payments, support, notifications, operator, admin, and analytics.

## Rentoza Backend Reuse Map

| Rentoza donor pattern | SpotLink backend adaptation |
| --- | --- |
| Java 21 + Spring Boot + Maven backend | New `com.spotlink` Spring Boot 3.5 app |
| App properties / profile configuration | `core.AppProperties` with CORS, cookie, currency, quote TTL, mock payment flags |
| Request correlation logging | `RequestCorrelationFilter` with `X-Request-Id` and MDC |
| Global exception handling | Flat frontend-compatible `ApiErrorResponse` with validation mapping |
| Cookie and CSRF patterns | Session-ready Spring Security, CORS, custom XSRF cookie repository |
| Repository/service/controller layering | Rebuilt per SpotLink module with parking terminology |
| Idempotency concept | Database-backed `idempotency_keys` foundation for reservation creation |
| Payment provider abstraction | Generic `PaymentProvider` plus `MockPaymentProvider` |
| Notification/support/admin boundaries | Simplified SpotLink support, notification, admin, and audit foundations |
| Geospatial primitives | Address and coordinate embeddables plus mock geocode provider |

## What Was Reused

- Spring Boot 3.x backend stack.
- Maven build and test lifecycle.
- Cookie-first/session-compatible auth posture.
- XSRF cookie/header convention used by the frontend.
- Request correlation and standard API error handling ideas.
- JPA/Flyway persistence pattern.
- Idempotent mutation pattern.
- Payment provider interface pattern.
- Notification provider abstraction pattern.
- Admin audit/event listing pattern.

## What Was Generalized

- `booking` became `reservation`.
- `owner` and `host` became `operator`.
- `renter` and `guest` became `customer`.
- Car listing inventory became `parking_locations` and `parking_resources`.
- Car data is limited to customer `vehicles` for fit and compatibility.
- Payment provider behavior is generic and PSP-ready, not Monri-specific.
- Location search is parking-resource focused, not pickup/delivery focused.
- Admin and support tooling are marketplace foundation surfaces, not rental dispute workflows.

## What Was Skipped

- Driver license, renter, owner, and car document verification.
- Check-in/checkout and photo workflows.
- Damage claims and damage disputes.
- Rental agreements and no-show flows.
- Rental-specific cancellation complexity.
- Monri-specific implementation.
- Tax withholding, payout ledgers, and Serbian rent-a-car legal/compliance text.
- Rentoza migration history and dense rental schema state.

## Module Structure

```text
apps/backend
├── pom.xml
├── src/main/java/com/spotlink
│   ├── SpotLinkApplication.java
│   ├── analytics
│   ├── admin
│   ├── auth
│   ├── core
│   ├── location
│   ├── notification
│   ├── operator
│   ├── payment
│   ├── reservation
│   ├── security
│   ├── support
│   ├── user
│   └── vehicle
├── src/main/resources
│   ├── application.properties
│   └── db/migration/V1__spotlink_backend_foundation.sql
└── src/test/java/com/spotlink
```

## API Endpoints Created

Core:

- `GET /api/health`
- Actuator endpoints under `/api/actuator`
- OpenAPI JSON at `/api/openapi`
- Swagger UI at `/api/swagger-ui`

Auth:

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/auth/register/customer`
- `POST /api/auth/register/operator`
- `POST /api/auth/password/reset-request`
- `POST /api/auth/password/reset`
- `POST /api/auth/token`
- `POST /api/auth/token/refresh`
- `POST /api/auth/token/revoke`

Mobile API version aliases:

- The backend preserves all existing `/api/...` endpoints.
- Mobile-critical endpoints are also exposed under `/api/v1/...` using the same controller methods and DTOs.
- Examples: `GET /api/v1/health`, `POST /api/v1/auth/token`, `GET /api/v1/auth/me`, `GET /api/v1/locations/search`, `POST /api/v1/reservations`, `POST /api/v1/payments/intents`.

Users and vehicles:

- `GET /api/users/me/profile`
- `GET /api/users/{userId}/profile`
- `PATCH /api/users/me/profile`
- `GET /api/vehicles/me`
- `POST /api/vehicles`
- `PUT /api/vehicles/{vehicleId}`
- `DELETE /api/vehicles/{vehicleId}`

Locations:

- `GET /api/locations/search`
- `GET /api/locations/geocode`
- `GET /api/locations/{locationId}`
- `GET /api/locations/{locationId}/resources`
- `POST /api/locations`
- `PUT /api/locations/{locationId}`
- `POST /api/locations/{locationId}/resources`
- `PUT /api/locations/{locationId}/resources/{resourceId}`

Reservations and payments:

- `GET /api/reservations/me`
- `GET /api/reservations/{reservationId}`
- `POST /api/reservations/quote`
- `POST /api/reservations`
- `POST /api/reservations/{reservationId}/cancel`
- `GET /api/payments/methods`
- `POST /api/payments/intents`
- `POST /api/payments/intents/{paymentIntentId}/confirm`

Support and notifications:

- `GET /api/support/tickets`
- `POST /api/support/tickets`
- `GET /api/support/tickets/{ticketId}/messages`
- `POST /api/support/tickets/{ticketId}/messages`
- `GET /api/notifications`
- `GET /api/notifications/unread-count`
- `POST /api/notifications/{notificationId}/read`
- `POST /api/notifications/device-tokens`

Operator, admin, analytics:

- `GET /api/operator/me`
- `GET /api/operator/dashboard/summary`
- `GET /api/operator/resources/health`
- `GET /api/admin/dashboard/summary`
- `GET /api/admin/users`
- `GET /api/admin/audit-events`
- `POST /api/analytics/events`

## Database Migration

Created:

- `apps/backend/src/main/resources/db/migration/V1__spotlink_backend_foundation.sql`
- `apps/backend/src/main/resources/db/migration/V2__mobile_refresh_tokens.sql`

Tables:

- `users`
- `user_roles`
- `user_preferences`
- `operator_accounts`
- `password_reset_tokens`
- `vehicles`
- `parking_locations`
- `parking_resources`
- `reservations`
- `payment_intents`
- `support_tickets`
- `support_messages`
- `notifications`
- `device_tokens`
- `audit_events`
- `idempotency_keys`
- `analytics_events`
- `refresh_tokens`

The schema uses UUID primary keys, `created_at` / `updated_at`, optimistic `version` columns, explicit enum strings, foreign keys, uniqueness constraints, and indexes for common lookups. It is PostgreSQL-ready and H2-compatible for local development/tests.

`refresh_tokens` stores only SHA-256 token hashes, never raw refresh tokens. It tracks `user_id`, `device_id`, `user_agent`, `issued_at`, `expires_at`, `revoked_at`, and `replaced_by_token_id` for rotation and revocation.

## Mobile Auth Hardening

Added after the initial backend foundation:

- Mobile bearer login now issues both an access token and a refresh token.
- Access-token TTL is configurable with `JWT_ACCESS_TOKEN_TTL_MINUTES`.
- Refresh-token TTL is configurable with `JWT_REFRESH_TOKEN_TTL_DAYS`.
- JWT issuer and audience are configurable with `JWT_ISSUER` and `JWT_AUDIENCE`.
- Production profiles reject the built-in development JWT secret.
- Refresh tokens are generated with secure randomness, stored hashed, rotated on refresh, and revoked on logout/revoke.
- Reusing a rotated or revoked refresh token fails and invalidates active refresh tokens for that user.
- Browser cookie/session login remains compatible.

## Verification Commands Run

From `apps/backend`:

```bash
mvn test
mvn verify
```

From the repo root:

```bash
npm run build
```

Smoke test:

```bash
java -jar apps/backend/target/spotlink-backend-0.1.0-SNAPSHOT.jar --server.port=18080
curl -sS -i http://localhost:18080/api/health
```

Results:

- `mvn verify` passed.
- Backend integration tests passed: context load, auth/profile, reservation idempotency and overlap checks, and payment intent contract flow.
- `npm run build` passed for the existing frontend.
- `/api/health` returned HTTP 200 with `status: UP`.

## What Should Be Built Next

- Replace local auth with the chosen production identity provider or harden local auth with email delivery, refresh/session rotation, lockout, and rate limiting.
- Add operator inventory management workflows on top of location/resource CRUD.
- Add real availability/pricing rules, operating hours, blackout windows, and access instruction release policies.
- Integrate a real payment provider through the existing `PaymentProvider` interface.
- Add support staff and admin moderation workflows.
- Add production PostgreSQL deployment config and environment-specific secret management.
- Expand tests around authorization matrices, payment states, location search, notification delivery, and admin/operator reporting.
