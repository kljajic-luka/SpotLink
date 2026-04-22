# SpotLink iOS Enterprise Readiness

Date: 2026-04-22

## Purpose

This package defines what SpotLink must become as a native iOS product and what the current foundation must support before the app is ready for TestFlight, App Store review, and production operations.

It is intentionally documentation-only. It does not change backend, frontend, or iOS implementation code. The package is designed to remain useful even while another implementation agent changes the native app foundation in parallel.

## Repository Audit

Observed repository shape:

- `apps/frontend`: Angular 20 foundation with strict TypeScript, design-system primitives, typed services, auth/session helpers, retry/error interceptors, and domain models.
- `apps/backend`: Java 21, Spring Boot 3.5, Maven, Spring Security, JPA, Flyway, Validation, Actuator, and OpenAPI backend foundation.
- `apps/ios`: no local iOS tree was visible during this audit. If another agent creates or changes this folder concurrently, that work was not verified here.
- `docs/api`: frontend-derived API contract and draft OpenAPI document.
- `docs/assets`: existing foundation screenshots.
- The working tree already contained modified and untracked files outside this package before these docs were written. Those files are treated as existing parallel work and were not touched.

Required materials reviewed:

- `SPOTLINK_FOUNDATION_MIGRATION.md`
- `SPOTLINK_BACKEND_FOUNDATION_MIGRATION.md`
- `apps/frontend/src/app/foundation`
- `apps/backend`
- `docs/api/SPOTLINK_FRONTEND_API_CONTRACT.md`
- `docs/api/spotlink-api-draft.openapi.yaml`

## Product Domain Inferred

SpotLink is a parking reservation marketplace with four primary roles:

- `CUSTOMER`: searches for parking, manages vehicles, reserves resources, pays, receives access instructions, and contacts support.
- `OPERATOR`: manages parking locations/resources, monitors occupancy and resource health, handles operational support.
- `SUPPORT`: triages customer/operator issues, reservation problems, refunds, access incidents, and account escalations.
- `ADMIN`: oversees marketplace health, users, operators, audit activity, policy, and operational controls.

Core domain objects:

- Parking location
- Parking resource
- Vehicle profile
- Reservation
- Payment intent
- Support ticket and support message
- Notification and device token
- Operator account
- Audit event
- Analytics event

## Current Foundation Maturity

| Area | Maturity | Notes |
| --- | --- | --- |
| Frontend domain contracts | Strong foundation | Typed Angular services define useful module boundaries for a native client to mirror. |
| Backend endpoint coverage | Strong foundation | Auth, profiles, vehicles, locations, reservations, payments, support, notifications, operator, admin, analytics, and health endpoints exist. |
| Native iOS implementation | Not verifiable | No `apps/ios` folder was visible during this audit. |
| Auth/session model | Foundation only | Current backend is cookie/session and XSRF-oriented. Native iOS can use it for early testing, but production mobile token/session design needs a deliberate decision. |
| Reservation integrity | Good start | Reservation creation is idempotent and overlap-checked. Idempotency should become a first-class API header and receive concurrency hardening. |
| Payments | Mock foundation | Payment provider abstraction exists, but production PSP, Apple Pay decisions, webhooks, refunds, and SCA/deep-link flows are missing. |
| Search/geospatial | Basic foundation | Search exists, but radius filtering, availability filtering, ranking, map clustering, and PostGIS-grade behavior are not production-ready. |
| Notifications | Basic foundation | Device tokens support `IOS`, but APNs provider, token lifecycle, preferences, and privacy handling are missing. |
| Security/privacy | Needs hardening | Password reset delivery, rate limits, abuse controls, mobile token storage, privacy policy, analytics consent, and production secrets are not complete. |
| QA readiness | Early | Backend has foundation integration tests. Native iOS, API contract, accessibility, offline, push, payment, and release validation coverage still needs to be built. |

## Recommended Native iOS Direction

Build SpotLink as a real native iOS app:

- Use Swift and SwiftUI for the app shell, forms, dashboards, lists, reservation flow, vehicle management, support, notifications, profile, and most operator/admin views.
- Use MapKit and CoreLocation for search, map pins, user location, route context, and location permission behavior.
- Use UIKit only where the native framework surface requires it or where SwiftUI is not mature enough, such as advanced map clustering, payment authorization controllers, document/photo pickers, web authentication/payment redirects, or highly customized input accessory behavior.
- Prefer async/await, typed endpoints, structured API errors, testable view models, dependency injection, and clear feature modules.
- Keep the first screen useful after authentication: customers should land in search/map; operators in dashboard/resource health; support in ticket queue; admins in marketplace summary.

## Preserve

The implementation agent should preserve these foundation decisions:

- Parking marketplace terminology: `customer`, `operator`, `parking location`, `parking resource`, `reservation`, `vehicle`.
- Role model: `CUSTOMER`, `OPERATOR`, `SUPPORT`, `ADMIN`.
- Typed module boundaries already visible in frontend and backend.
- Request correlation with `X-Request-Id`.
- API pagination shape with `content`, `totalElements`, `totalPages`, `page`, and `size`.
- Structured API error responses with stable `code`, `message`, `requestId`, `details`, `timestamp`, and `path`.
- Reservation and payment idempotency concept.
- Money represented as minor units plus ISO currency.
- UTC instants for server timestamps plus explicit parking-location timezone where local time matters.
- Mock provider abstractions as test tools only.

## Do Not Carry Forward

The iOS implementation should reject these patterns:

- WebView-style UI or web-first navigation copied from Angular.
- Browser storage patterns for auth secrets.
- Hardcoded API URLs or environment constants in Swift source.
- Rental-car terminology such as renter, host, trip, checkout, damage, driver license, rental agreement, pickup, dropoff, or owner payout.
- Mock payments, mock geocoding, or mock notifications in production configurations.
- Business logic embedded directly in SwiftUI views.
- Untyped networking that passes dictionaries through the app.
- Missing loading, empty, offline, permission-denied, and recoverable error states.
- Reservation creation without idempotency.
- Date/time formatting that ignores the location timezone.

## Document Index

- [iOS Product Experience Spec](IOS_PRODUCT_EXPERIENCE_SPEC.md)
- [iOS Architecture Blueprint](IOS_ARCHITECTURE_BLUEPRINT.md)
- [API Contract Review](API_CONTRACT_REVIEW.md)
- [Security and Privacy Review](SECURITY_PRIVACY_REVIEW.md)
- [QA Test Strategy](QA_TEST_STRATEGY.md)
- [App Store Readiness Checklist](APP_STORE_READINESS_CHECKLIST.md)
- [MVP iOS Roadmap](MVP_IOS_ROADMAP.md)
- [Implementation Review Checklist](IMPLEMENTATION_REVIEW_CHECKLIST.md)

