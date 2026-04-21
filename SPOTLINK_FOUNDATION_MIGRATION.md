# SpotLink Foundation Migration

Date: 2026-04-21

## Scope

This migration used `/Users/kljaja01/Developer/Rentoza` as the donor codebase and created a focused SpotLink foundation in `/Users/kljaja01/Developer/SpotLink`.

SpotLink was empty and was not a Git repository before this work. Because there was no existing project structure to preserve, the foundation was scaffolded as a lean Angular workspace under:

- `apps/frontend`
- `apps/frontend/src/app/foundation`

The implementation intentionally avoids cloning the full Rentoza application. Rentoza is a mature Angular + Spring Boot rent-a-car marketplace with substantial car rental workflows, check-in/checkout procedures, compliance logic, payment lifecycle code, admin tooling, and migrations. SpotLink only needs the reusable marketplace foundation for a parking MVP.

## Repo Audit

### SpotLink

- Empty directory at the start of migration.
- No `.git` repository metadata.
- No package, frontend app, backend app, docs, or build configuration.

### Rentoza Donor

Reusable donor areas:

- Angular 20 standalone component architecture.
- Strict TypeScript configuration and path aliases.
- Shared design token approach.
- Dependency-light shared UI component patterns.
- Cookie-first auth/session shape.
- HTTP credential, XSRF, retry, and error interceptor patterns.
- API client and paginated response boundaries.
- User profile service and DTO boundaries.
- Geospatial location models and browser geolocation service.
- Reservation/booking lifecycle modeling patterns.
- Payment provider adapter abstraction.
- Notification, support/chat, admin, operator, and analytics service boundaries.
- Production-safe logger and storage utility patterns.

High-risk donor areas not suitable for direct reuse:

- Car rental check-in/checkout and photo comparison workflows.
- Damage claims, damage disputes, no-show flows, and rental agreement deadlines.
- Driver license verification, renter verification, and car document verification.
- Serbian rent-a-car legal terms, owner tax/payout infrastructure, and Monri-specific flows.
- Full Spring Boot database migration set, which is dense and domain-specific.

## Reuse Map

| Rentoza concept | SpotLink foundation adaptation |
| --- | --- |
| `core/services/logger.service.ts` | `foundation/core/logger.service.ts` with SpotLink prefixing |
| Rentoza design tokens | `src/styles/_design-tokens.scss` with neutral SpotLink palette and 8px radius cap |
| `shared/components/button` | `foundation/design-system/components/ui-button.component.ts` |
| `shared/components/form-input` | `foundation/design-system/components/text-field.component.ts` |
| Empty/error/loading/image components | `foundation/shared-components/*` |
| Auth service with cookie-first thinking | `foundation/auth/auth.service.ts` |
| Role guards | `foundation/auth/role.guard.ts` |
| HTTP interceptors | `foundation/networking/interceptors/*` |
| API response/pagination models | `foundation/networking/api.types.ts` |
| User profile models/services | `foundation/user-profile/*` |
| Location/geospatial models | `foundation/locations/*` |
| Car booking lifecycle | `foundation/reservations/*`, generalized to parking reservations |
| Car metadata | `foundation/vehicles/*`, limited to customer vehicle fit needs |
| Payment provider adapter | `foundation/payments/*` |
| Chat/support concepts | `foundation/support/*` |
| Notification models/services | `foundation/notifications/*` |
| Owner dashboard boundary | `foundation/operator/*` |
| Admin service boundary | `foundation/admin/*` |
| Analytics sendBeacon queue | `foundation/analytics/*` |

## Generalized Naming

The foundation uses marketplace-neutral and parking-ready naming:

- `Rentoza` -> `SpotLink`
- `booking` -> `reservation`
- `owner` / `host` -> `operator`
- `renter` / `guest` -> `customer`
- `car` -> `vehicle` only where the user vehicle matters
- `car listing` -> `parking location` / `parking resource`
- `trip` -> `reservation`
- `pickup` / `delivery` -> `location access`
- `checkout` -> `payment confirmation` or `reservation completion`

## Implemented Modules

### Core

Path: `apps/frontend/src/app/foundation/core`

Includes:

- App config injection token.
- Production-aware logger.
- Namespaced browser storage.
- Idempotency key utility.
- View-state helpers.
- Shared role type.

### DesignSystem

Path: `apps/frontend/src/app/foundation/design-system`

Includes:

- `sl-ui-button`
- `sl-text-field`
- `sl-status-pill`
- SpotLink CSS design tokens.

### Networking

Path: `apps/frontend/src/app/foundation/networking`

Includes:

- API client wrapper.
- API error and pagination types.
- HTTP context tokens.
- Cookie credential + XSRF interceptor.
- Retry interceptor for idempotent requests.
- API error mapping interceptor.

### Auth

Path: `apps/frontend/src/app/foundation/auth`

Includes:

- Customer/operator registration DTOs.
- Cookie-first login/logout/current-session service shape.
- Password reset request DTOs.
- Role guard factory.

### UserProfile

Path: `apps/frontend/src/app/foundation/user-profile`

Includes:

- User profile DTOs.
- Profile stats and preferences.
- Profile read/update service.

### Vehicles

Path: `apps/frontend/src/app/foundation/vehicles`

Includes:

- Customer vehicle profiles.
- Vehicle fit rules for parking resources.
- Vehicle CRUD service boundary.

### Locations

Path: `apps/frontend/src/app/foundation/locations`

Includes:

- Address and coordinate types.
- Parking location and parking resource types.
- Location search filters/results.
- Browser geolocation service.
- Location/geocode API service boundary.

### Reservations

Path: `apps/frontend/src/app/foundation/reservations`

Includes:

- Reservation status lifecycle.
- Quote and create reservation DTOs.
- Reservation API service boundary.
- Reservation card view model helper.

### Payments

Path: `apps/frontend/src/app/foundation/payments`

Includes:

- Payment method, intent, and status models.
- Payment provider adapter abstraction.
- Mock payment adapter.
- Payment API service boundary.

### Support

Path: `apps/frontend/src/app/foundation/support`

Includes:

- Support ticket categories/statuses.
- Support ticket and message DTOs.
- Support service boundary.

### Notifications

Path: `apps/frontend/src/app/foundation/notifications`

Includes:

- Notification type and item DTOs.
- Device token registration DTO.
- Notification service boundary.

### Operator

Path: `apps/frontend/src/app/foundation/operator`

Includes:

- Operator account model.
- Operator dashboard summary.
- Parking resource health model.
- Operator service boundary.

### Admin

Path: `apps/frontend/src/app/foundation/admin`

Includes:

- Admin dashboard summary.
- Admin user summary.
- Audit event model.
- Admin service boundary.

### Analytics

Path: `apps/frontend/src/app/foundation/analytics`

Includes:

- Analytics event DTO.
- Best-effort telemetry queue using `navigator.sendBeacon`.

### SharedComponents

Path: `apps/frontend/src/app/foundation/shared-components`

Includes:

- Empty state.
- Error state.
- Loading skeleton.
- Optimized image placeholder wrapper.

## What Was Reused

- Rentoza's standalone Angular architecture.
- Rentoza's strict TypeScript posture.
- Rentoza's component layering style: foundation components first, feature pages later.
- Rentoza's HTTP reliability patterns: credentials, XSRF, retries, error normalization.
- Rentoza's cookie-first auth service shape.
- Rentoza's logger/storage utility patterns.
- Rentoza's geospatial and location service boundaries.
- Rentoza's payment adapter idea, not its provider-specific implementation.
- Rentoza's notification/support/admin/operator service boundaries.
- Rentoza's analytics queue pattern.

## What Was Generalized

- Rent-a-car users became `CUSTOMER`, `OPERATOR`, `SUPPORT`, and `ADMIN`.
- Car listings became parking `Location` and `Resource` models.
- Car booking became parking `Reservation`.
- Owner dashboard became `Operator`.
- Chat and dispute concepts became `Support`.
- Payment authorization became generic payment intent handling.
- Exact route/page workflows were replaced with module service boundaries.

## What Was Skipped

- Full Rentoza backend copy.
- Full Rentoza frontend page copy.
- Driver license verification.
- Owner document verification.
- Check-in and checkout photo flows.
- Damage claim/dispute flows.
- Rental agreement workflows.
- No-show flows.
- Monri-specific payment provider code.
- Tax withholding and payout ledger workflows.
- Rentoza legal pages and Serbian rent-a-car policy text.
- Car availability rule complexity that does not apply to parking MVP.

## What Should Be Built Next

1. Choose backend shape:
   - Lightweight Spring Boot API if continuing from Rentoza backend patterns.
   - Supabase/PostgREST if the solo-founder priority is speed and managed auth/storage.

2. Define API contracts for:
   - Auth/session.
   - Location search.
   - Parking resource availability.
   - Reservation quote/create/cancel.
   - Payment intent create/confirm.
   - Operator dashboard.
   - Support tickets.

3. Build MVP pages:
   - Search parking.
   - Location detail.
   - Reservation quote and checkout.
   - My reservations.
   - Operator location/resource management.
   - Admin review dashboard.

4. Add real integrations:
   - Map/geocoding provider.
   - Payment provider.
   - Email provider.
   - Web Push provider.
   - Error/analytics provider.

5. Add tests:
   - Service DTO contract tests.
   - Reservation quote view model tests.
   - Auth guard tests.
   - Component smoke tests for shared UI.

## Current Status

The foundation now compiles as a standalone Angular app target once dependencies are installed. The app includes a first-screen foundation dashboard at `/` and exports all foundation modules from `src/app/foundation/index.ts`.
