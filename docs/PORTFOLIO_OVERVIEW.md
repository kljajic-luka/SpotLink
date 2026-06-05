# SpotLink Portfolio Overview

SpotLink is a parking reservation platform built as a serious multi-surface product baseline. It targets three operational audiences:

- Customers who need to find and reserve parking from a native iOS app.
- Parking operators who need to manage inventory, upcoming arrivals, check-in/no-show state, and capacity controls.
- Admin/support teams who need booking oversight, payment/support visibility, audit history, and account lifecycle workflows.

The repository is intentionally positioned as internal release-readiness work, not as a production launch. The engineering value is in the domain modeling, safety gates, and cross-surface consistency.

## Architecture

- **Backend**: Spring Boot API under `/api` and `/api/v1`, Java 21, Spring Security, JPA/Hibernate, Flyway migrations, Actuator health/readiness, OpenAPI contracts, runtime safety guards, Docker image path.
- **Frontend**: Angular operator/admin portal with guarded routes, foundation services, strict TypeScript, design-system primitives, and CI tests.
- **iOS**: Native SwiftUI app with Swift Package modules, session-aware shell, typed services/models, shared Xcode schemes, privacy manifest, staging bundle ID, and signed export templates.
- **Verification**: `make release-gate` validates backend, frontend, SwiftPM, Xcode simulator tests, and unsigned Release/Staging iOS builds.

## Implemented Product And Platform Work

- Reservation/search foundations with idempotent reservation creation, booking holds, manual confirmation, cancellation, no-show, and refund-marker state.
- Operator dashboard/workspace for pilot inventory and operational booking management.
- Admin/support portal for booking search, payment attempts, support cases, audit events, and account deletion fulfillment.
- Backend payment authority layer that blocks production mock payment exposure and shapes future PSP operations without integrating a real PSP yet.
- Push device-token lifecycle with register/reactivate/unregister semantics, ownership-safe unregister behavior, APNs-ready provider scaffolding, delivery metrics, and token redaction.
- Account deletion request intake and admin-reviewed anonymization fulfillment using `RegistrationStatus.DELETED` rather than unsafe hard deletes.
- Staging/production runtime hardening: no H2/default DB, required JWT secret, explicit CORS, secure cookies, and production mock-payment rejection.
- iOS App Store privacy/legal scaffolding with conservative manifest declarations and owner-owned URL wiring.
- Signed iOS archive/export scaffolding for future human-controlled TestFlight upload.

## Deliberate Non-Claims

SpotLink is not yet deployed to real staging, uploaded to TestFlight, connected to a real PSP, or physically verified with APNs credentials and entitlements. Legal/privacy policy text and App Store Connect answers still need owner/legal approval. The repo is designed so those gaps are explicit and blocked by configuration/access instead of hidden behind optimistic documentation.

## Reviewer Signal

The project is meant to show how a production-minded platform is prepared before external credentials and providers are available:

- Safety-sensitive defaults fail closed in staging/production.
- Mock services are fenced to non-production use.
- Native, web, and backend contracts evolve together.
- Account deletion and privacy work are treated as operational workflows, not only UI copy.
- Release readiness is expressed as a repeatable gate instead of a list of manual hopes.
