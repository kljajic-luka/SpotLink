# SpotLink Mobile API Contract

Date: 2026-04-22

## Contract Status

This contract documents the current backend shape under `server.servlet.context-path=/api`. Paths below include `/api` to match mobile client base URLs.

The backend preserves existing `/api/...` routes and also exposes `/api/v1/...` aliases for the mobile-critical API surface. Example:

- Current route: `POST /api/auth/token`
- Versioned mobile alias: `POST /api/v1/auth/token`

The `/api/v1/...` aliases share the same controller methods and DTOs as the existing routes.

## Common Rules

### Authentication

The backend currently supports two auth models:

- Web/session auth: `POST /api/auth/login` creates an HTTP session and XSRF cookie.
- Mobile bearer auth: `POST /api/auth/token` or `POST /api/v1/auth/token` returns a JWT access token plus rotating refresh token.

Native iOS should prefer bearer auth for current mobile work:

```text
Authorization: Bearer <accessToken>
```

Cookie/session clients must also send XSRF headers for protected mutations:

```text
Cookie: JSESSIONID=...
Cookie: XSRF-TOKEN=...
X-XSRF-TOKEN: <token from XSRF-TOKEN cookie>
```

### Common Request Headers

All mobile requests should send:

```text
Accept: application/json
X-Request-Id: mob-<uuid>
```

JSON request bodies should send:

```text
Content-Type: application/json
```

Authenticated mobile requests should send:

```text
Authorization: Bearer <accessToken>
```

### Idempotency

Current backend accepts idempotency keys in request bodies for:

- `POST /api/reservations`
- `POST /api/payments/intents`

Mobile clients should also prepare to send the future-safe header:

```text
X-Idempotency-Key: <key>
```

Until the backend explicitly consumes the header, the body `idempotencyKey` remains required.

### Mobile Token Lifecycle

The mobile token lifecycle is:

1. `POST /api/auth/token` issues an access token and refresh token.
2. iOS stores the refresh token in Keychain only.
3. `POST /api/auth/token/refresh` rotates the refresh token and returns a new access token plus new refresh token.
4. The previous refresh token is revoked during rotation and cannot be reused.
5. `POST /api/auth/token/revoke` revokes a supplied refresh token or, when authenticated, all refresh tokens for the current user.
6. `POST /api/auth/logout` preserves browser session logout and also revokes a supplied mobile refresh token.

Refresh tokens are persisted only as SHA-256 hashes.

### Dates and Times

All server timestamps and reservation windows are ISO-8601 UTC instants:

```text
2026-05-01T10:00:00Z
```

Reservation UI should display windows in the parking location/reservation `timezone` field, not the device timezone by default.

### Money

Amounts are integer minor units using current backend names ending in `Cents`, paired with ISO 4217 `currency`.

Example:

```json
{ "totalAmountCents": 1850, "currency": "USD" }
```

### Pagination

Paginated endpoints return:

```json
{
  "content": [],
  "totalElements": 0,
  "totalPages": 0,
  "page": 0,
  "size": 20
}
```

Pagination query parameters:

- `page`: zero-based integer.
- `size`: requested page size. Backend clamps common list sizes to `100`.

### Standard Error Envelope

Errors return:

```json
{
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Input validation failed",
  "requestId": "mob-123",
  "details": {
    "email": "must be a well-formed email address"
  },
  "timestamp": "2026-04-22T13:00:00Z",
  "path": "/api/auth/token"
}
```

iOS should preserve `code` and `requestId` in typed errors.

## Enums

### UserRole

- `CUSTOMER`
- `OPERATOR`
- `SUPPORT`
- `ADMIN`

### RegistrationStatus

- `INCOMPLETE`
- `ACTIVE`
- `SUSPENDED`
- `DELETED`

### OperatorType

- `INDIVIDUAL`
- `BUSINESS`

### VehicleType

- `CAR`
- `MOTORCYCLE`
- `VAN`
- `TRUCK`
- `BICYCLE`
- `OTHER`

### VehicleVerificationStatus

- `UNVERIFIED`
- `PENDING`
- `VERIFIED`
- `REJECTED`

### ParkingAccessType

- `SELF_PARK`
- `VALET`
- `GATE_CODE`
- `APP_UNLOCK`
- `ATTENDANT`

### ParkingResourceType

- `PARKING_SPOT`
- `GARAGE`
- `DRIVEWAY`
- `EV_CHARGER`
- `LOT`

### ReservationStatus

- `DRAFT`
- `PENDING_PAYMENT`
- `CONFIRMED`
- `ACTIVE`
- `COMPLETED`
- `CANCELLED`
- `EXPIRED`
- `DISPUTED`

### PaymentStatus

- `REQUIRES_METHOD`
- `REQUIRES_ACTION`
- `AUTHORIZED`
- `CAPTURED`
- `FAILED`
- `REFUNDED`
- `CANCELLED`

### SupportTicketCategory

- `RESERVATION`
- `PAYMENT`
- `LOCATION_ACCESS`
- `SAFETY`
- `ACCOUNT`
- `OTHER`

### SupportTicketStatus

- `OPEN`
- `WAITING_ON_CUSTOMER`
- `WAITING_ON_OPERATOR`
- `RESOLVED`

### NotificationType

- `RESERVATION_CONFIRMED`
- `RESERVATION_CANCELLED`
- `PAYMENT_ACTION_REQUIRED`
- `ACCESS_INSTRUCTIONS_READY`
- `SUPPORT_REPLY`
- `OPERATOR_ALERT`
- `SYSTEM`

### DevicePlatform

- `WEB`
- `IOS`
- `ANDROID`

## DTO Schemas

### UserProfile

Fields:

- `id: UUID`
- `email: string`
- `firstName: string`
- `lastName: string`
- `phone?: string`
- `avatarUrl?: string`
- `bio?: string`
- `roles: UserRole[]`
- `operatorId?: UUID`
- `registrationStatus: RegistrationStatus`
- `createdAt: Instant`

Swift name: `UserProfileDTO` or `UserProfile`.

### UserProfileDetails

Extends `UserProfile` with:

- `stats: ProfileStats`
- `preferences: UserPreferences`

### ProfileStats

- `completedReservations: integer`
- `activeVehicles: integer`
- `savedLocations: integer`
- `supportTickets: integer`

### UserPreferences

- `locale: string`
- `marketingOptIn: boolean`
- `reservationAlerts: boolean`
- `paymentAlerts: boolean`
- `supportAlerts: boolean`

### VehicleProfile

- `id: UUID`
- `userId: UUID`
- `type: VehicleType`
- `nickname?: string`
- `make?: string`
- `model?: string`
- `color?: string`
- `licensePlate?: string`
- `heightMeters?: decimal`
- `lengthMeters?: decimal`
- `evCapable: boolean`
- `verificationStatus: VehicleVerificationStatus`
- `createdAt: Instant`
- `updatedAt: Instant`

### ParkingLocation

- `id: UUID`
- `operatorId: UUID`
- `name: string`
- `address: Address`
- `coordinates: GeoCoordinates`
- `timezone: string`
- `accessType: ParkingAccessType`
- `publicNotes?: string`
- `active: boolean`

### ParkingResource

- `id: UUID`
- `locationId: UUID`
- `type: ParkingResourceType`
- `label: string`
- `floor?: string`
- `bayNumber?: string`
- `fitRule?: VehicleFitRule`
- `hourlyRateCents: integer`
- `dailyRateCents?: integer`
- `currency: string`
- `instantReserve: boolean`
- `active: boolean`

### LocationSearchResult

Important: this object is nested. Do not flatten it in Swift.

- `location: ParkingLocation`
- `resources: ParkingResource[]`
- `distanceKm?: number`
- `startingPriceCents?: integer`
- `availableResourceCount: integer`

### Reservation

- `id: UUID`
- `customerId: UUID`
- `operatorId: UUID`
- `locationId: UUID`
- `resourceId: UUID`
- `vehicleId?: UUID`
- `startsAt: Instant`
- `endsAt: Instant`
- `timezone: string`
- `status: ReservationStatus`
- `totalAmountCents: integer`
- `currency: string`
- `accessInstructionsVisible: boolean`
- `createdAt: Instant`
- `updatedAt: Instant`

Note: backend does not currently return `idempotencyKey` on `ReservationDto`.

### ReservationQuote

- `resourceId: UUID`
- `startsAt: Instant`
- `endsAt: Instant`
- `subtotalCents: integer`
- `feesCents: integer`
- `discountCents: integer`
- `totalAmountCents: integer`
- `currency: string`
- `expiresAt: Instant`

### PaymentIntent

- `id: UUID`
- `reservationId: UUID`
- `amountCents: integer`
- `currency: string`
- `status: PaymentStatus`
- `redirectUrl?: string`
- `clientSecret?: string`

### PaymentProviderResult

- `status: PaymentStatus`
- `paymentIntentId: UUID`
- `redirectUrl?: string`
- `message?: string`

### SupportTicket

- `id: UUID`
- `category: SupportTicketCategory`
- `status: SupportTicketStatus`
- `subject: string`
- `reservationId?: UUID`
- `locationId?: UUID`
- `createdAt: Instant`
- `updatedAt: Instant`

### NotificationItem

- `id: UUID`
- `type: NotificationType`
- `title: string`
- `body: string`
- `relatedEntityId?: UUID`
- `read: boolean`
- `createdAt: Instant`

## Endpoint Contract

### Health

| Field | Value |
| --- | --- |
| Method | `GET` |
| Path | `/api/health` |
| Auth | Public |
| Role | None |

Success `200`:

- `status: "UP"`
- `service: "spotlink-backend"`
- `timestamp: Instant`

Mobile retry: safe to retry with backoff.

### Auth Login

| Field | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/auth/login` |
| Auth | Public |
| Role | None |

Body:

- `email: string`
- `password: string`

Success `200`:

- `authenticated: boolean`
- `user: UserProfile`
- `message: string`

Notes: creates cookie/session and XSRF cookie. Native iOS should generally use `/auth/token`.

### Auth Token

| Field | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/auth/token` |
| Auth | Public |
| Role | None |

Body:

- `email: string`
- `password: string`
- `deviceId?: string`

Success `200`:

- `accessToken: string`
- `refreshToken: string`
- `tokenType: "Bearer"`
- `expiresIn: integer seconds` for backward compatibility
- `expiresInSeconds: integer`
- `refreshExpiresInSeconds: integer`
- `issuedAt: Instant`
- `expiresAt: Instant`
- `refreshExpiresAt: Instant`
- `user: UserProfile`
- `roles: UserRole[]`

Mobile retry: do not retry invalid credentials. Safe to retry network failures before a response.

Swift names: `MobileTokenRequest`, `AuthSessionResponse`, `AuthSession`.

### Auth Token Refresh

| Field | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/auth/token/refresh` |
| Auth | Public; refresh token is the credential |
| Role | None |

Body:

- `refreshToken: string`
- `deviceId?: string`

Success `200`: same schema as Auth Token.

Behavior:

- The submitted refresh token is revoked during rotation.
- The response contains a new refresh token.
- Reusing an old rotated token returns `401 UNAUTHORIZED` and invalidates active refresh tokens for that user.

### Auth Token Revoke

| Field | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/auth/token/revoke` |
| Auth | Public for single refresh token revocation; bearer required for `allForCurrentUser` |
| Role | Authenticated only when revoking all current user's tokens |

Body:

- `refreshToken?: string`
- `allForCurrentUser?: boolean`

Success `204 No Content`.

### Auth Logout

| Field | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/auth/logout` |
| Auth | Cookie/session or bearer accepted by security layer |
| Role | Authenticated |

Success `204 No Content`.

Behavior:

- Browser/session behavior is preserved: the HTTP session is invalidated and XSRF token is cleared.
- If body contains `refreshToken`, that mobile refresh token is revoked.
- If body contains `allForCurrentUser: true` and the request is bearer-authenticated, all active refresh tokens for the current user are revoked.
- Access JWTs remain self-contained until expiry; iOS must still clear local access tokens on logout.

### Auth Me

| Field | Value |
| --- | --- |
| Method | `GET` |
| Path | `/api/auth/me` |
| Auth | Required |
| Role | Authenticated |

Success `200`: `UserProfile`.

### Customer Registration

| Field | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/auth/register/customer` |
| Auth | Public |
| Role | None |

Body:

- `firstName: string`
- `lastName: string`
- `email: string`
- `phone?: string`
- `password: string`
- `acceptsTerms: boolean`

Success `201`: `AuthResponse`.

Notes: currently creates cookie/session, not mobile token. iOS may call `/auth/token` after successful registration.

### Operator Registration

| Field | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/auth/register/operator` |
| Auth | Public |
| Role | None |

Body includes customer fields plus:

- `companyName?: string`
- `operatorType: INDIVIDUAL | BUSINESS`
- `acceptsOperatorAgreement: boolean`

Success `201`: `AuthResponse`. Registered operator receives `CUSTOMER` and `OPERATOR` roles.

### Password Reset Request

| Field | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/auth/password/reset-request` |
| Auth | Public |

Body:

- `email: string`

Success `204 No Content`.

### Password Reset

| Field | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/auth/password/reset` |
| Auth | Public |

Body:

- `token: string`
- `newPassword: string`

Success `204 No Content`.

### Current User Profile

| Field | Value |
| --- | --- |
| Method | `GET` |
| Path | `/api/users/me/profile` |
| Auth | Required |

Success `200`: `UserProfileDetails`.

### User Profile by ID

| Field | Value |
| --- | --- |
| Method | `GET` |
| Path | `/api/users/{userId}/profile` |
| Auth | Required |

Success `200`: `UserProfileDetails`.

Current behavior: foundation exposes only current user's profile; other IDs may return `403`.

### Update Profile and Preferences

| Field | Value |
| --- | --- |
| Method | `PATCH` |
| Path | `/api/users/me/profile` |
| Auth | Required |

Body:

- `firstName?: string`
- `lastName?: string`
- `phone?: string`
- `avatarUrl?: string`
- `bio?: string`
- `preferences?: PartialPreferences`

Success `200`: `UserProfileDetails`.

Preferences are updated through this endpoint. There is no standalone preferences endpoint.

Notification preference behavior:

- reservation push notifications (`RESERVATION_CONFIRMED`, `RESERVATION_CANCELLED`, `ACCESS_INSTRUCTIONS_READY`) require `reservationAlerts=true`
- payment-action push notifications require `paymentAlerts=true`
- support replies and current operator alert notifications require `supportAlerts=true`
- `SYSTEM` notifications are reserved for account/safety/security-critical messages and are not preference-skipped
- `marketingOptIn` is not used for transactional notification delivery

Preference skips suppress outbound push delivery only. Notification inbox rows remain persisted unless a future product policy explicitly changes that behavior.

### Vehicles

List:

- `GET /api/vehicles/me`
- Auth required.
- Success `200`: `VehicleProfile[]`.

Create:

- `POST /api/vehicles`
- Auth required.
- Body: `VehicleUpsertRequest`.
- Success `201`: `VehicleProfile`.

Update:

- `PUT /api/vehicles/{vehicleId}`
- Auth required and vehicle ownership enforced.
- Body: `VehicleUpsertRequest`.
- Success `200`: `VehicleProfile`.

Delete:

- `DELETE /api/vehicles/{vehicleId}`
- Auth required and vehicle ownership enforced.
- Success `204 No Content`.

Mobile retry: list is safe. Create/update/delete should not be auto-retried without explicit user action or future idempotency support.

### Location Search

| Field | Value |
| --- | --- |
| Method | `GET` |
| Path | `/api/locations/search` |
| Auth | Public for GET |

Query params:

- `query?: string`
- `latitude?: decimal`
- `longitude?: decimal`
- `radiusKm?: decimal`
- `resourceTypes?: ParkingResourceType[]`
- `evChargingRequired?: boolean`
- `startsAt?: Instant`
- `endsAt?: Instant`
- `page?: integer`, default `0`
- `size?: integer`, default `20`

Success `200`: `ApiPage<LocationSearchResult>`.

Mobile retry: safe to retry with backoff.

Known backend limitation: current foundation does not yet provide full viewport/radius/availability-ranked map search behavior.

### Geocode

| Field | Value |
| --- | --- |
| Method | `GET` |
| Path | `/api/locations/geocode` |
| Auth | Public for GET |

Query params:

- `query: string`

Success `200`: `GeocodeSuggestion[]`.

### Location Detail

- `GET /api/locations/{locationId}`
- Public GET.
- Success `200`: `ParkingLocation`.

### Parking Resource List

- `GET /api/locations/{locationId}/resources`
- Public GET.
- Success `200`: `ParkingResource[]`.

Current limitation: no `GET /api/resources/{resourceId}` or `GET /api/locations/{locationId}/resources/{resourceId}` exists.

### Reservation Quote

| Field | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/reservations/quote` |
| Auth | Required |

Body:

- `resourceId: UUID`
- `vehicleId?: UUID`
- `startsAt: Instant`
- `endsAt: Instant`
- `promoCode?: string`

Success `200`: `ReservationQuote`.

Mobile retry: safe to retry quote requests; quote creation is not persisted as a reservation.

### Reservation Create

| Field | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/reservations` |
| Auth | Required |
| Idempotency | Required body field `idempotencyKey`; future-safe header recommended |

Body:

- `resourceId: UUID`
- `vehicleId?: UUID`
- `startsAt: Instant`
- `endsAt: Instant`
- `promoCode?: string`
- `quoteId?: string`
- `paymentMethodId?: string`
- `idempotencyKey: string`

Success `201`: `Reservation`.

Mobile retry: retry only with the exact same `idempotencyKey` and same logical request after timeouts or transport failures.

### Reservation Get

- `GET /api/reservations/{reservationId}`
- Auth required.
- Success `200`: `Reservation`.

### Reservation List

- `GET /api/reservations/me?page=0&size=20`
- Auth required.
- Success `200`: `ApiPage<Reservation>`.

### Reservation Cancel

- `POST /api/reservations/{reservationId}/cancel`
- Auth required.
- Body: `{ "reason": "optional string" }` or empty body.
- Success `200`: `Reservation`.

### Payment Methods

- `GET /api/payments/methods`
- Auth required.
- Success `200`: `PaymentMethod[]`.

### Payment Intent Create

- `POST /api/payments/intents`
- Auth required.
- Idempotency required in body.
- Body: `reservationId`, optional `paymentMethodId`, `idempotencyKey`.
- Success `201`: `PaymentIntent`.

Mobile retry: retry only with the same idempotency key.

### Payment Intent Confirm

- `POST /api/payments/intents/{paymentIntentId}/confirm`
- Auth required.
- Body: `{}`.
- Success `200`: `PaymentProviderResult`.

Current limitation: no payment intent get or cancel endpoint exists.

### Support Tickets

List:

- `GET /api/support/tickets?page=0&size=20`
- Auth required.
- Success `200`: `ApiPage<SupportTicket>`.

Create:

- `POST /api/support/tickets`
- Auth required.
- Body: `category`, `subject`, `body`, optional `reservationId`, optional `locationId`.
- Success `201`: `SupportTicket`.

Current limitation: no `GET /api/support/tickets/{ticketId}` exists.

### Support Messages

List:

- `GET /api/support/tickets/{ticketId}/messages`
- Auth required.
- Success `200`: `SupportMessage[]`.

Create:

- `POST /api/support/tickets/{ticketId}/messages`
- Auth required.
- Body: `{ "body": "message" }`.
- Success `201`: `SupportMessage`.

### Notifications

List:

- `GET /api/notifications?page=0&size=20`
- Auth required.
- Success `200`: `ApiPage<NotificationItem>`.

Unread count:

- `GET /api/notifications/unread-count`
- Auth required.
- Success `200`: `{ "count": 3 }`.

Mark read:

- `POST /api/notifications/{notificationId}/read`
- Auth required.
- Success `204 No Content`.

Device token register:

- `POST /api/notifications/device-tokens`
- Auth required.
- Body: `{ "deviceToken": "...", "platform": "IOS" }`.
- Success `204 No Content`.

Device token unregister:

- `POST /api/notifications/device-tokens/unregister`
- Auth required.
- Body: `{ "deviceToken": "...", "platform": "IOS" }`.
- Success `204 No Content`.

Unregister is non-enumerating: missing or foreign tokens return the same no-content shape and do not reveal token ownership.

### Operator

Current operator account:

- `GET /api/operator/me`
- Auth required.
- Role: `OPERATOR` or `ADMIN`.
- Success `200`: `OperatorAccount`.

Dashboard:

- `GET /api/operator/dashboard/summary`
- Auth required.
- Role: `OPERATOR` or `ADMIN`.
- Success `200`: `OperatorDashboardSummary`.

Resource health:

- `GET /api/operator/resources/health`
- Auth required.
- Role: `OPERATOR` or `ADMIN`.
- Success `200`: `OperatorResourceHealth[]`.

### Admin

Dashboard:

- `GET /api/admin/dashboard/summary`
- Auth required.
- Role: `ADMIN`.
- Success `200`: `AdminDashboardSummary`.

Users:

- `GET /api/admin/users?page=0&size=25`
- Auth required.
- Role: `ADMIN`.
- Success `200`: `ApiPage<AdminUserSummary>`.

Audit events:

- `GET /api/admin/audit-events?page=0&size=25`
- Auth required.
- Role: `ADMIN`.
- Success `200`: `ApiPage<AdminAuditEvent>`.

### Analytics Event Ingest

| Field | Value |
| --- | --- |
| Method | `POST` |
| Path | `/api/analytics/events` |
| Auth | Public currently |

Body:

- `events: AnalyticsEvent[]`

Each event:

- `event: string`
- `properties?: object`
- `timestamp?: Instant`
- `url?: string` (deprecated; accepted for backwards compatibility but not persisted)
- `sessionId: string`

Allowed event names:

- `app_open`
- `screen_view`
- `login`
- `logout`
- `registration_started`
- `registration_completed`
- `search_performed`
- `reservation_quote_requested`
- `reservation_flow_started`
- `reservation_created`
- `reservation_create_failed`
- `payment_intent_created`
- `payment_unavailable`
- `support_ticket_created`
- `account_deletion_requested`
- `notification_preferences_updated`
- `profile_updated`
- `error`

Allowed property keys are limited to low-sensitivity first-party fields:

- `platform`
- `appVersion`
- `appBuild`
- `environment`
- `screen`
- `context`
- `source`
- `flow`
- `type`
- `result`
- `status`
- `reason`
- `category`
- `provider`
- `registrationType`
- `paymentMode`
- `reservationStatus`
- `notificationType`
- `errorName`

The backend rejects batches with unknown events, unknown/unsafe property keys, nested property values, more than 20 events, more than 20 properties per event, property string values over 120 characters, or obvious PII/secrets such as email, phone, full names, license plates, bearer/refresh/reset/APNs tokens, exact addresses, precise coordinates, card/payment method data, or free-form error descriptions. iOS strips unsafe properties client-side before best-effort submission and does not submit analytics unless local analytics consent is enabled.

Success `202 Accepted`, no body.

Mobile retry: best-effort only. Do not block UX.

## Missing Mobile Endpoints

The following are not currently implemented and should not be called by iOS:

- Account deletion status/export endpoint. The request endpoint exists and is used by iOS.
- Payment intent get/cancel endpoints.
- Device token delete/deactivate endpoint.
- Support ticket detail endpoint.
- Parking resource detail by resource ID.
- Map viewport search endpoint.
