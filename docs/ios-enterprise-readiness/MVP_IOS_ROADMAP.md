# MVP iOS Roadmap

Date: 2026-04-22

## Roadmap Principles

- Build the customer parking reservation path first.
- Keep operator/admin/support surfaces native but narrower until backend workflows mature.
- Do not ship mock payments or mock notifications to external users.
- Keep API contracts versioned before external clients depend on them.
- Treat security, privacy, and accessibility as release requirements, not polish.

## Phase 1: Foundation

### Goals

- Establish native iOS project structure.
- Implement app shell, environment configuration, typed networking, auth/session foundation, and design system primitives.
- Mirror backend DTOs and module boundaries.

### Required Engineering Work

- Create SwiftUI app target and test targets.
- Add environment configuration for local, staging, and production.
- Implement typed `APIClient`, `APIError`, `APIPage`, request ID generation, and ISO date decoding.
- Implement auth session restore, login, logout, customer registration, and operator registration against current backend.
- Implement role-aware app shell and customer tabs.
- Implement loading, empty, error, unauthorized, and offline state components.
- Add Keychain or controlled cookie/session store depending on selected auth model.
- Add base logging with PII redaction.

### Required Design/Product Work

- Define native design tokens, typography, colors, icons, and status patterns.
- Define customer tab IA and role switch behavior.
- Approve empty/error/loading state copy.
- Define app icon and launch screen direction.

### Backend Dependencies

- Stable auth endpoints.
- Stable profile DTOs.
- Stable error envelope.
- Request ID support.
- API versioning decision should begin here.

### QA Gates

- iOS build passes.
- Unit tests for API decoding and error mapping.
- Auth view model tests.
- Backend `mvn verify` passes.
- Manual login/logout smoke passes.

### Release Criteria

- Internal engineering build can sign in, restore session, and render customer shell.
- No implementation files outside intended iOS app are changed by this roadmap work.

### Risks

- Cookie/session auth may need rework if mobile token strategy changes.
- Missing `apps/ios` baseline could cause parallel implementation drift.

## Phase 2: Alpha

### Goals

- Complete customer search, location detail, vehicle management, reservation quote/create, and mock/internal payment flow.

### Required Engineering Work

- Implement MapKit search and list synchronization.
- Implement location detail and resources.
- Implement filters for time, vehicle, resource type, EV, and price/distance where supported.
- Implement vehicle CRUD.
- Implement reservation quote and create with idempotency.
- Implement payment intent create/confirm against mock/internal provider.
- Implement reservation list/detail/cancel.
- Implement support ticket create and basic thread.
- Implement notification inbox from backend.

### Required Design/Product Work

- Approve map pin, result card, reservation card, payment state, and support thread designs.
- Define reservation cancellation and payment copy.
- Define location permission education.
- Define mock payment labels for internal builds.

### Backend Dependencies

- Search endpoint must support enough filters for alpha.
- Reservation idempotency and overlap checks must pass concurrency tests.
- Payment mock provider acceptable for internal alpha only.
- Support and notification endpoints available.

### QA Gates

- Unit and view model tests for search, vehicles, quote, create, payment, and support.
- UI smoke tests for customer critical path.
- Backend tests for reservation and payment pass.
- Offline/no-network behavior verified.

### Release Criteria

- A tester can create a customer account, find parking, add a vehicle, reserve, complete internal payment, view reservation, and create support ticket.

### Risks

- Search quality may feel weak until backend geospatial filtering/ranking improves.
- Payment flow may need redesign when production PSP is selected.

## Phase 3: Internal TestFlight

### Goals

- Put a controlled internal build in testers' hands using staging backend and real device testing.

### Required Engineering Work

- Configure signing, bundle IDs, and TestFlight build pipeline.
- Add crash reporting.
- Add push permission flow and APNs token registration.
- Add deep links for reservation, support, and payment return.
- Add dark mode and Dynamic Type hardening.
- Add feature flags for unfinished operator/admin/support features.
- Add diagnostics screen in debug/internal builds.

### Required Design/Product Work

- App icon and launch screen ready.
- Internal release notes.
- Test account matrix for all roles.
- Manual QA scripts.

### Backend Dependencies

- HTTPS staging backend.
- APNs sandbox configured.
- Rate limiting on auth and analytics.
- Production-like secrets management.
- API versioning plan finalized.

### QA Gates

- Internal TestFlight smoke checklist passes on physical devices.
- APNs token registration works.
- Crash reporting receives test crash.
- Accessibility pass for core customer flow.
- Backend `mvn verify` passes against current code.

### Release Criteria

- Internal testers can complete the core customer flow on physical devices without developer tools.

### Risks

- APNs, signing, and provisioning delays.
- Staging/backend instability affecting TestFlight confidence.

## Phase 4: External TestFlight

### Goals

- Validate product with a limited external audience using production-like services.

### Required Engineering Work

- Replace mock payment with PSP test/production-like flow if payments are visible externally.
- Implement payment return/deep-link reconciliation.
- Add account deletion request flow.
- Add production privacy manifest.
- Harden session expiry and token/cookie cleanup.
- Add remote configuration or feature flags for risky surfaces.
- Add operator/admin access restrictions and hidden tabs unless ready.

### Required Design/Product Work

- External beta instructions.
- Privacy policy and terms live.
- Support process ready.
- App Store screenshot draft.
- Payment/cancellation/support policy copy approved.

### Backend Dependencies

- Production-like auth/session contract.
- Real PSP integration or paid features disabled.
- APNs provider.
- Device token unregister/deactivate.
- Search geospatial improvements for beta markets.
- Rate limiting and abuse monitoring.
- Audit logging for sensitive actions.

### QA Gates

- External TestFlight checklist passes.
- Payment test mode passes on physical device.
- Push notification flow passes on physical device.
- Offline/reservation timeout regression passes.
- Security/privacy review blockers closed.

### Release Criteria

- Limited external users can search, reserve, pay, receive notifications, and contact support with acceptable failure rates.

### Risks

- Payment provider edge cases and App Review beta feedback.
- Privacy/legal gaps delaying external approval.

## Phase 5: App Store MVP

### Goals

- Release a production-ready customer MVP with controlled operator/admin/support capabilities.

### Required Engineering Work

- Production environment configuration.
- Production APNs.
- Production payment provider and Apple Pay if in scope.
- Final App Store privacy manifest and Info.plist permission copy.
- Release archive and signing automation.
- Monitoring dashboards.
- Feature flags for rollback.
- App Store review test accounts and notes.

### Required Design/Product Work

- Final screenshots.
- App Store description.
- Privacy policy, terms, support URL.
- Launch support plan.
- Review notes and test credentials.

### Backend Dependencies

- Production database and migrations verified.
- Mock payment disabled.
- API version locked.
- Rate limiting and monitoring enabled.
- Account deletion path operational.
- Backups and incident runbook ready.

### QA Gates

- Full release candidate regression.
- Accessibility review.
- Performance baseline review.
- Security/privacy checklist complete.
- App Store archive validation.
- Production smoke in pre-release environment.

### Release Criteria

- App Store checklist complete.
- No P0 security/API/payment/reservation blockers.
- Monitoring and support team ready for launch.

### Risks

- App Review rejection due account deletion, privacy copy, payments, or incomplete metadata.
- Production backend incidents after release.

## Phase 6: Post-MVP Hardening

### Goals

- Improve reliability, scale, support operations, operator workflows, and marketplace controls.

### Required Engineering Work

- PostGIS or equivalent geospatial search.
- Cursor pagination for high-volume lists.
- Rich operator inventory management.
- Support staff queue and assignment.
- Admin moderation tools.
- Refund/cancellation automation.
- Receipts and invoices if required.
- Saved locations/favorites.
- App clips or widgets only after core app is stable.
- Enhanced observability and feature flag controls.

### Required Design/Product Work

- Operator workflow research.
- Support operations playbooks.
- Admin action UX.
- Market expansion requirements.
- Pricing/cancellation policy iteration.

### Backend Dependencies

- Search scale and indexing.
- Payment webhooks and reconciliation.
- Audit logging coverage.
- Data export/deletion automation.
- Real APNs credential/entitlement setup and physical-device delivery validation.

### QA Gates

- Load tests for search/reservation/payment.
- Contract tests for new endpoints.
- Role-based authorization regression.
- Crash/performance budget monitoring.

### Release Criteria

- Marketplace operations can scale beyond MVP without manual database intervention.

### Risks

- Operational complexity grows faster than admin/support tooling.
- Search/payment reliability becomes the limiting factor for user trust.
