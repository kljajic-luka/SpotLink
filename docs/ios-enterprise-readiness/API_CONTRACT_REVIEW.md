# API Contract Review for iOS

Date: 2026-04-22

## Review Scope

This review evaluates the current Spring Boot backend foundation from a native iOS client perspective. It is based on the repository state observed during audit. The other implementation agent may be changing files concurrently; no assumptions are made about unverified future changes.

Ratings:

- `Ready`: usable by iOS with normal client implementation work.
- `Needs minor changes`: usable for MVP foundations, but should be tightened before wider release.
- `Needs major changes`: blocks a high-quality TestFlight/App Store path unless improved.
- `Missing`: not currently present in the audited backend.

Priorities:

- `P0`: required before production or external TestFlight if the feature is in scope.
- `P1`: required before App Store MVP.
- `P2`: should be planned soon after MVP or before scale.
- `P3`: improvement or future hardening.

## High-Level Findings

- Endpoint breadth is strong for a foundation: auth, profile, vehicle, location, reservation, payment, support, notification, operator, admin, analytics, health, Actuator, and OpenAPI surfaces exist.
- The backend is currently web/session-oriented. Native iOS can integrate with cookies and XSRF, but a deliberate production mobile auth/session contract is still needed.
- Structured errors, request correlation, pagination, and idempotency are present and should be preserved.
- Search/geospatial behavior is not yet production-grade: radius, availability, ranking, and map-scale performance need backend work.
- Payments and notifications are mock/foundation-grade: production PSP, Apple Pay decision, webhooks, APNs delivery, token lifecycle, and privacy controls are required.
- API versioning is missing.

## Module Review

### Auth Endpoints

Current state:

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/auth/register/customer`
- `POST /api/auth/register/operator`
- `POST /api/auth/password/reset-request`
- `POST /api/auth/password/reset`
- Session cookie and XSRF cookie are established by backend.
- Public auth paths are CSRF-exempt.
- Roles are returned in profile DTOs.

iOS readiness rating: `Needs major changes`

Priority: `P0`

Recommended changes:

- Decide production mobile auth model: hardened cookie sessions for native clients or mobile access/refresh tokens.
- Prefer OIDC/OAuth with PKCE or backend-issued short-lived access token plus rotating refresh token.
- Add refresh/session rotation endpoint if token model is adopted.
- Add email verification, account lockout, and suspicious login controls. Public auth/password-reset/analytics rate limits now exist as local backend guardrails.
- Replace the local `safe-log` password reset mail provider with a real email provider before external staging; reset token material must remain out of logs.
- Add explicit session revocation behavior on logout.
- Return consistent auth error codes for expired, revoked, suspended, and invalid credentials.
- Consider device/session listing for support and user self-service later.

### User and Profile Endpoints

Current state:

- `GET /api/users/me/profile`
- `GET /api/users/{userId}/profile`
- `PATCH /api/users/me/profile`
- DTOs include identity fields, roles, operator ID, registration status, profile stats, and preferences.
- Public profile endpoint currently only allows the current user's own profile.

iOS readiness rating: `Needs minor changes`

Priority: `P1`

Recommended changes:

- Rename or document `GET /users/{userId}/profile` as self-only until public profiles are intentionally supported.
- Add account deletion request/status endpoint for App Store compliance.
- Add email/phone verification state if used for trust or support.
- Add avatar upload contract if avatar editing is in scope.
- Clarify preference behavior for push notifications vs email/marketing.

### Vehicle Endpoints

Current state:

- `GET /api/vehicles/me`
- `POST /api/vehicles`
- `PUT /api/vehicles/{vehicleId}`
- `DELETE /api/vehicles/{vehicleId}`
- DTOs include type, nickname, make, model, color, license plate, height, length, EV capability, and verification status.

iOS readiness rating: `Needs minor changes`

Priority: `P1`

Recommended changes:

- Add default vehicle support if reservation UX should preselect a vehicle.
- Add duplicate license plate behavior and regional formatting rules if license plate matters operationally.
- Add `PATCH` if partial updates are expected.
- Clarify whether verification status is user-visible and what actions are possible.
- Treat license plate as PII in API docs and logging policy.

### Location and Search Endpoints

Current state:

- `GET /api/locations/search`
- `GET /api/locations/geocode`
- `GET /api/locations/{locationId}`
- `GET /api/locations/{locationId}/resources`
- Operator mutations also exist: create/update location and create/update resource.
- Search accepts query, latitude, longitude, radiusKm, resourceTypes, evChargingRequired, startsAt, endsAt, page, and size.
- Implementation filters active resources by type/EV and computes distance, but does not fully enforce radius, availability window, geospatial ranking, or resource availability filtering in search.

iOS readiness rating: `Needs major changes`

Priority: `P0`

Recommended changes:

- Apply radius filtering server-side.
- Sort and rank by distance, availability, price, and relevance.
- Apply `startsAt` and `endsAt` availability filtering in search results.
- Add map viewport search support: bounding box or center/radius.
- Add PostGIS or equivalent geospatial indexing before scale.
- Add stable filter/sort contract for the iOS map/list experience.
- Add image/media fields if location detail requires visual trust.
- Add access instruction privacy fields separate from public notes.
- Add operator-safe vs customer-safe resource labels.

### Reservation Endpoints

Current state:

- `GET /api/reservations/me`
- `GET /api/reservations/{reservationId}`
- `POST /api/reservations/quote`
- `POST /api/reservations`
- `POST /api/reservations/{reservationId}/cancel`
- Reservation creation is idempotent by body field `idempotencyKey`.
- Overlap checks block conflicting reservations.
- Quote includes subtotal, fees, discount, total, currency, and expiration.
- `quoteId` exists in create request but is not enforced.

iOS readiness rating: `Needs minor changes`

Priority: `P0`

Recommended changes:

- Promote idempotency to `X-Idempotency-Key` header while accepting body field during transition.
- Return stable idempotency replay responses with the same status/body semantics.
- Enforce or remove `quoteId`; current unused field can confuse mobile clients.
- Add reservation timeline/status history if customer support and UI need it.
- Add operator reservation list endpoints.
- Add cancellation policy, refund preview, and reason handling if cancellations affect money.
- Add access instructions endpoint or field with clear reveal rules.
- Add conflict details such as next available windows when resource unavailable.

### Payment Endpoints

Current state:

- `GET /api/payments/methods`
- `POST /api/payments/intents`
- `POST /api/payments/intents/{paymentIntentId}/confirm`
- Mock payment provider exists.
- Intent creation is idempotent by body field `idempotencyKey`.
- Payment authorization can confirm reservation and reveal access instructions.

iOS readiness rating: `Needs major changes`

Priority: `P0`

Recommended changes:

- Replace mock provider with production PSP integration before external TestFlight if payments are in scope.
- Decide Apple Pay support and entitlement requirements.
- Add PSP webhook handling and idempotent webhook processing.
- Add payment status refresh endpoint.
- Add refund/capture/void policy endpoints if needed.
- Support `REQUIRES_ACTION` return/deep-link flow for iOS.
- Add payment method setup/delete/default contracts if saving methods is in scope.
- Never expose card data beyond PSP-safe display fields.
- Add production config guard that prevents mock payment in release builds.

### Support Endpoints

Current state:

- `GET /api/support/tickets`
- `POST /api/support/tickets`
- `GET /api/support/tickets/{ticketId}/messages`
- `POST /api/support/tickets/{ticketId}/messages`
- Customer-owned tickets and message threads exist.
- Ticket can relate to reservation or location.

iOS readiness rating: `Needs minor changes`

Priority: `P1`

Recommended changes:

- Add support staff queue endpoints filtered by status, category, age, priority, and assignment.
- Add ticket assignment/escalation for `SUPPORT`.
- Add attachment upload contract if images/screenshots are expected.
- Add ticket close/reopen behavior.
- Add operator-visible support queues for operator-related tickets.
- Add privacy rules for what support/admin/operator can see.

### Notification and Device Token Endpoints

Current state:

- `GET /api/notifications`
- `GET /api/notifications/unread-count`
- `POST /api/notifications/{notificationId}/read`
- `POST /api/notifications/device-tokens`
- `POST /api/notifications/device-tokens/unregister`
- `DevicePlatform` includes `IOS`.
- Device-token lifecycle and APNs-ready provider scaffold exist with credential-free tests.

iOS readiness rating: `Needs major changes`

Priority: `P0`

Recommended changes:

- Configure real APNs provider credentials outside the repo.
- Store APNs environment: sandbox vs production.
- Store app bundle/build metadata if useful for operations.
- Add payload schema and deep-link contract.
- Add privacy rules for push payload content.
- Add badge count update strategy.
- Add token rotation handling and duplicate token ownership rules.

### Operator Endpoints

Current state:

- `GET /api/operator/me`
- `GET /api/operator/dashboard/summary`
- `GET /api/operator/resources/health`
- Location/resource create/update endpoints exist under `/locations`.
- Security config restricts `/operator/**` to OPERATOR or ADMIN, but `/locations` mutations rely on service-level operator account checks.

iOS readiness rating: `Needs major changes`

Priority: `P1`

Recommended changes:

- Add explicit operator inventory list/detail endpoints or document `/locations` mutations as operator APIs.
- Add operator reservation list filtered by location/resource/date/status.
- Add operating hours, blackout windows, pricing rules, and resource availability controls.
- Add operator support ticket queues.
- Add audit logging for operator inventory changes.
- Add role/permission tests around location mutations.

### Admin Endpoints

Current state:

- `GET /api/admin/dashboard/summary`
- `GET /api/admin/users`
- `GET /api/admin/audit-events`
- Security restricts `/admin/**` to ADMIN.

iOS readiness rating: `Needs major changes`

Priority: `P1`

Recommended changes:

- Add admin user detail and role-management endpoints only after authorization and audit logging are strong.
- Add operator review/moderation endpoints.
- Add reservation/payment investigation endpoints.
- Add support escalation overview.
- Add audit filters by actor, resource, action, date, and request ID.
- Add admin action confirmation and audit requirements.

### Analytics Endpoints

Current state:

- `POST /api/analytics/events`
- Public and CSRF-exempt.
- Accepts event name, properties map, timestamp, URL, and session ID.

iOS readiness rating: `Needs major changes`

Priority: `P1`

Recommended changes:

- Add rate limiting and abuse protection.
- Add mobile fields: app version, build, platform, role, install/session ID, consent state.
- Avoid URL-centric fields as the primary mobile screen identifier.
- Add event schema registry and validation.
- Enforce privacy restrictions on properties.
- Respect analytics opt-in/opt-out.

### Health and Observability Endpoints

Current state:

- `GET /api/health`
- Actuator under `/api/actuator`
- OpenAPI under `/api/openapi`
- Swagger UI under `/api/swagger-ui`
- Request correlation filter returns `X-Request-Id`.

iOS readiness rating: `Needs minor changes`

Priority: `P1`

Recommended changes:

- Expose only safe health details publicly.
- Add mobile status/degraded service response if the app should show maintenance banners.
- Add documented request ID support for support diagnostics.
- Ensure production Actuator endpoints are locked down.
- Add trace/log correlation into monitoring.

## Cross-Cutting Contract Review

### Error Envelope Quality

Current state:

- `ApiErrorResponse` includes status, code, message, requestId, details, timestamp, and path.

iOS readiness rating: `Needs minor changes`

Priority: `P0`

Recommended changes:

- Freeze stable error codes before iOS external TestFlight.
- Add `retryAfterSeconds` or use `Retry-After` consistently for retryable errors.
- Add field-level validation path format for nested fields.
- Avoid localizing backend messages if iOS owns UI copy.

### Pagination Quality

Current state:

- `ApiPage<T>` includes content, totalElements, totalPages, page, and size.

iOS readiness rating: `Ready`

Priority: `P2`

Recommended changes:

- Add sort metadata when sort is supported.
- Consider cursor pagination for high-volume lists such as notifications, audit events, and search.

### Idempotency Support

Current state:

- Reservation creation uses database-backed idempotency records.
- Payment intent creation uses customer and idempotency key lookup.
- CORS exposes `X-Idempotency-Key`, but current DTOs carry idempotency in request bodies.

iOS readiness rating: `Needs minor changes`

Priority: `P0`

Recommended changes:

- Standardize `X-Idempotency-Key` for reservation creation, payment intent creation, and other payment-like mutations.
- Document retry behavior for timeout and in-progress cases.
- Store response status/body for every completed idempotent mutation.
- Add expiration behavior and cleanup job.

### Correlation and Request IDs

Current state:

- `X-Request-Id` is accepted or generated and returned.
- Request ID is included in error body.

iOS readiness rating: `Ready`

Priority: `P1`

Recommended changes:

- Require iOS to generate a request ID per request.
- Include request ID in support diagnostics and nonfatal API error reporting.

### Mobile Retry Behavior

Current state:

- Backend has no broad mobile retry contract beyond idempotency support and request IDs.

iOS readiness rating: `Needs minor changes`

Priority: `P1`

Recommended changes:

- Document retryable status codes.
- Use `Retry-After` for rate limits and transient overload.
- Ensure idempotent mutations are safe to retry after client timeout.

### API Versioning

Current state:

- No versioned API path or version header was observed.

iOS readiness rating: `Missing`

Priority: `P0`

Recommended changes:

- Introduce `/api/v1` or an explicit API version header before external iOS clients depend on the contract.
- Publish OpenAPI for the exact mobile-supported version.
- Define deprecation policy.

### DTO Naming Consistency

Current state:

- DTOs are generally parking-domain aligned and consistent.
- Some frontend docs are multilingual and some fields remain web-shaped, such as analytics `url`.

iOS readiness rating: `Needs minor changes`

Priority: `P2`

Recommended changes:

- Keep English API documentation for the mobile team.
- Define naming conventions for IDs, timestamps, money, booleans, and enum values.
- Replace web-centric analytics fields with mobile screen/context fields while preserving backwards compatibility.

### Enum Consistency

Current state:

- Roles, reservation statuses, payment statuses, vehicle types, notification types, access types, and resource types exist as enums.

iOS readiness rating: `Needs minor changes`

Priority: `P1`

Recommended changes:

- Publish enum lists in OpenAPI.
- Add forward-compatibility policy for unknown enum values.
- Avoid renaming enum raw values after iOS release.

### Date and Time Handling

Current state:

- Backend uses `Instant` for reservation windows and timestamps.
- Parking locations include timezone.
- Jackson is configured for UTC and ISO date output.

iOS readiness rating: `Needs minor changes`

Priority: `P0`

Recommended changes:

- Require iOS to send UTC instants.
- Require UI to display reservation windows in the parking location timezone.
- Add validation around daylight saving transitions and ambiguous local times.
- Include timezone in quote/reservation responses, already present for reservations.

### Money and Currency Handling

Current state:

- Amounts use cents/minor units and currency string.
- Quote includes subtotal, fees, discount, total.

iOS readiness rating: `Needs minor changes`

Priority: `P0`

Recommended changes:

- Document ISO 4217 currency behavior and minor-unit assumptions.
- Avoid naming every minor-unit field `Cents` if non-2-decimal currencies may be supported later. Consider `amountMinor`.
- Add taxes/fees breakdown if required for App Store, receipts, or local regulation.

### Geospatial Search Support

Current state:

- Coordinates and geocode suggestion contracts exist.
- Search accepts location inputs but needs server-side geospatial behavior.

iOS readiness rating: `Needs major changes`

Priority: `P0`

Recommended changes:

- Add geospatial index, radius filtering, viewport filtering, distance sorting, and availability-aware result counts.
- Return stable map clustering data if needed by iOS at scale.
- Add location permission denied/manual search fallback contract only in app, not API.

### APNs and Device Token Support

Current state:

- Device token registration accepts `deviceToken` and `platform`; `IOS` is supported.
- Device token unregister/reactivation, APNs-ready provider scaffolding, metrics, redaction, and server-side preference enforcement exist without committed credentials.

iOS readiness rating: `Needs major changes`

Priority: `P0`

Recommended changes:

- Configure real APNs provider credentials and environment metadata outside the repo.
- Keep token unregister/deactivation tests green.
- Add notification payload schema with deep-link target and privacy constraints.
