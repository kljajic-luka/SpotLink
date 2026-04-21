# SpotLink

SpotLink is a parking reservation marketplace foundation built with Angular, strict TypeScript, and modular service boundaries. The project focuses on the product foundation needed for customers to find parking, operators to manage parking inventory, and administrators to support marketplace operations.

![SpotLink foundation dashboard](docs/assets/spotlink-foundation.png)

<img src="docs/assets/spotlink-foundation-mobile.png" alt="SpotLink mobile foundation dashboard" width="320" />

## Status

- Frontend foundation is implemented in `apps/frontend`.
- Production build is passing.
- Foundation migration notes live in `SPOTLINK_FOUNDATION_MIGRATION.md`.
- This repository intentionally tracks source, documentation, lockfiles, and README screenshots while excluding dependencies, build output, and local smoke-test artifacts.

## Product Scope

SpotLink generalizes marketplace concepts into a parking domain:

- `customer`: the person searching for and reserving parking.
- `operator`: the owner or manager of parking locations and resources.
- `reservation`: a booked parking time window.
- `location/resource`: the parking inventory customers can reserve.
- `vehicle`: used only for fit, access, and compatibility checks.

Car-rental workflows such as damage claims, rental agreements, driver-license verification, check-in photos, no-show logic, payout ledgers, and rent-a-car compliance are intentionally out of scope for this foundation.

## Tech Stack

- Angular 20 standalone application architecture.
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

## Quick Start

Install dependencies:

```bash
npm install --prefix apps/frontend
```

Run the production build from the repository root:

```bash
npm run build
```

Start the Angular development server:

```bash
npm run start
```

The app is served by Angular CLI from `apps/frontend`.

## Scripts

```bash
npm run start      # Start the Angular dev server
npm run build      # Production build
npm run build:dev  # Development build
npm run test       # Angular test target
```

## Project Layout

```text
.
├── apps/
│   └── frontend/
│       ├── src/app/foundation/
│       ├── src/app/pages/
│       ├── angular.json
│       ├── package.json
│       └── package-lock.json
├── docs/assets/
├── SPOTLINK_FOUNDATION_MIGRATION.md
├── package.json
└── README.md
```

## Verification

Latest local verification:

- `npm install` completed with 0 vulnerabilities.
- `npm run build` passed.
- Desktop and mobile Playwright smoke tests completed with no console errors or warnings.

## Roadmap

- Connect the frontend foundation to a real API backend.
- Add persistence, authentication provider integration, and authorization enforcement.
- Build map search, availability, pricing, and reservation workflows.
- Replace the mock payment adapter with provider-specific implementations.
- Add operator inventory management screens.
- Add admin moderation and support workflows.
- Expand unit, integration, and browser coverage.
