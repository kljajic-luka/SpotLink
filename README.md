# SpotLink

SpotLink is a parking reservation marketplace foundation built with Angular, strict TypeScript, and modular service boundaries. The project focuses on the product foundation needed for customers to find parking, operators to manage parking inventory, and administrators to support marketplace operations.

![SpotLink foundation dashboard](docs/assets/spotlink-foundation.png)

<img src="docs/assets/spotlink-foundation-mobile.png" alt="SpotLink mobile foundation dashboard" width="320" />

## Status

- Frontend foundation is implemented in `apps/frontend`.
- Backend foundation is implemented in `apps/backend`.
- iOS foundation is implemented in `apps/ios`.
- Production build is passing.
- Foundation migration notes live in `SPOTLINK_FOUNDATION_MIGRATION.md`.
- Backend migration notes live in `SPOTLINK_BACKEND_FOUNDATION_MIGRATION.md`.
- Frontend hardening notes live in `SPOTLINK_FRONTEND_FOUNDATION_HARDENING.md`.
- iOS foundation notes live in `SPOTLINK_IOS_FOUNDATION_MIGRATION.md`.
- This repository intentionally tracks source, documentation, lockfiles, and README screenshots while excluding dependencies, build output, and local smoke-test artifacts.

## Product Scope

SpotLink generalizes marketplace concepts into a parking domain:

- `customer`: the person searching for and reserving parking.
- `operator`: the person or team managing parking locations and resources.
- `reservation`: a booked parking time window.
- `location/resource`: the parking inventory customers can reserve.
- `vehicle`: used only for fit, access, and compatibility checks.

Car-rental workflows such as damage claims, rental agreements, driver-license verification, check-in photos, no-show logic, payout ledgers, and rent-a-car compliance are intentionally out of scope for this foundation.

## Tech Stack

- Angular 20 standalone application architecture.
- Java 21 and Spring Boot 3.5 backend foundation.
- Maven, Spring Security, Spring Data JPA, Flyway, Validation, Actuator, and OpenAPI.
- Strict TypeScript configuration.
- SCSS design-token layer.
- RxJS service patterns.
- Angular HTTP interceptors for credentials, retry, and API error handling.
- Adapter boundary for payment providers.

## Foundation Modules

The current frontend foundation includes:

- Core application services and utilities.
- Design system primitives.
- Networking client, pagination contracts, and interceptors.
- Auth and role-based access boundaries.
- User profile and vehicle compatibility services.
- Location and geospatial service boundaries.
- Reservation models, services, and view models.
- Payment adapter and mock payment provider.
- Support, notification, operator, admin, and analytics services.
- Shared loading, empty, error, and image components.

The current backend foundation includes:

- Cookie/session-ready auth endpoints and SpotLink roles.
- User profiles, preferences, vehicles, parking locations/resources, reservations, payments, support, notifications, operator, admin, audit, and analytics modules.
- PostgreSQL-ready Flyway baseline migration.
- Request correlation, CORS, XSRF, validation, error mapping, idempotency, health, actuator, and OpenAPI foundations.

## Quick Start

Install dependencies:

```bash
npm install --prefix apps/frontend
```

Run the production build from the repository root:

```bash
npm run build
```

Run backend tests from the repository root:

```bash
npm run test:backend
```

Run the iOS package build and tests from the repository root:

```bash
npm run build:ios
npm run test:ios
```

Start the backend locally:

```bash
npm run start:backend
```

Start the Angular development server:

```bash
npm run start
```

The app is served by Angular CLI from `apps/frontend`.

## Scripts

```bash
npm run start      # Start the Angular dev server
npm run start:backend # Start the Spring Boot backend
npm run build      # Production build
npm run build:backend # Package the Spring Boot backend
npm run build:ios  # Build the iOS Swift package
npm run build:dev  # Development build
npm run test       # Angular test target
npm run test:backend # Backend Maven tests
npm run test:ios   # Execute the iOS Swift Testing runner
```

`npm run test:ios` is the workspace-standard iOS test command. It uses the package-local `SpotLinkTestRunner` executable so Swift Testing executes correctly on Command Line Tools environments where `swift test` only compiles the bundle.

## Project Layout

```text
.
├── apps/
│   ├── backend/
│   │   ├── src/main/java/com/spotlink/
│   │   ├── src/main/resources/db/migration/
│   │   └── pom.xml
│   ├── ios/
│   │   ├── SpotLink/
│   │   ├── SpotLink.xcodeproj/
│   │   └── Resources/
│   └── frontend/
│       ├── src/app/foundation/
│       ├── src/app/pages/
│       ├── angular.json
│       ├── package.json
│       └── package-lock.json
├── docs/assets/
├── SPOTLINK_BACKEND_FOUNDATION_MIGRATION.md
├── SPOTLINK_FOUNDATION_MIGRATION.md
├── package.json
└── README.md
```

## Verification

Recent local verification:

- `mvn verify` in `apps/backend` passed.
- `GET /api/health` returned HTTP 200 with `status: UP` during backend smoke testing.
- `npm run build` passed.
- `npm run test:ios` executes the Swift Testing suite and prints a pass/fail summary on CLT-only environments.

## Roadmap

- Connect the frontend foundation to a real API backend.
- Add persistence, authentication provider integration, and authorization enforcement.
- Build map search, availability, pricing, and reservation workflows.
- Replace the mock payment adapter with provider-specific implementations.
- Add operator inventory management screens.
- Add admin moderation and support workflows.
- Expand unit, integration, and browser coverage.
