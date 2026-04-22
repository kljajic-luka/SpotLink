# Implementation Review Checklist

Date: 2026-04-22

Use this checklist to review the parallel native iOS implementation after it lands. The checklist is intentionally strict: it should catch web-shaped UI, weak security, brittle architecture, and API contract drift before TestFlight.

## Native iOS UX Checks

- [ ] App launches into native SwiftUI/UIKit surfaces, not a WebView shell.
- [ ] Customer default workspace prioritizes Search/map after authentication.
- [ ] Role-aware tabs are stable and native.
- [ ] Navigation uses `NavigationStack`, native sheets, confirmation dialogs, and tab navigation appropriately.
- [ ] Search has map and list modes or a clear path to both.
- [ ] Location permission is requested in context.
- [ ] App remains useful when location permission is denied.
- [ ] Reservation flow has quote, review, create, payment, and confirmation states.
- [ ] Payment unknown/failure states are explicit and recoverable.
- [ ] Support is reachable from reservation, payment error, location, profile, and inbox where implemented.
- [ ] Operator/admin/support surfaces are native and not copied from web dashboard layout without adaptation.
- [ ] Loading, empty, offline, permission-denied, unauthorized, and error states exist for every core screen.
- [ ] Dark mode is intentional.
- [ ] Dynamic Type does not clip critical text or controls.

## Swift and SwiftUI Quality Checks

- [ ] SwiftUI views are small and focused on rendering state.
- [ ] Business logic lives in view models/use cases/repositories, not directly in views.
- [ ] View models are testable without live network, location, Keychain, APNs, or payment provider.
- [ ] Async operations use async/await or a consistent concurrency model.
- [ ] MainActor boundaries are explicit where UI state changes.
- [ ] No force unwraps in network/auth/payment/reservation paths.
- [ ] Date, money, and enum formatting are centralized.
- [ ] Preview/stub data does not leak into production.
- [ ] UIKit wrappers are isolated and justified.

## Architecture Checks

- [ ] Modules map to app/domain boundaries: Auth, Profile, Locations, Reservations, Payments, Vehicles, Notifications, Support, Operator, Admin, Analytics.
- [ ] Networking is typed and centralized.
- [ ] API paths are not assembled ad hoc inside views.
- [ ] Environment configuration is injected and build-specific.
- [ ] No hardcoded API URLs in Swift source.
- [ ] Dependency injection supports live, preview, and test containers.
- [ ] Secure storage is behind a protocol.
- [ ] Cache/persistence schema is versioned if persistent cache exists.
- [ ] Deep link routing is centralized.
- [ ] Feature flags or role gates hide incomplete admin/operator/support surfaces.

## Backend and API Compatibility Checks

- [ ] iOS endpoints match the current backend contract or documented versioned API.
- [ ] `X-Request-Id` is generated and sent.
- [ ] API errors decode `status`, `code`, `message`, `requestId`, `details`, `timestamp`, and `path`.
- [ ] Pagination decodes `content`, `totalElements`, `totalPages`, `page`, and `size`.
- [ ] Reservation creation sends and persists an idempotency key.
- [ ] Payment intent creation sends and persists an idempotency key.
- [ ] Dates are encoded as ISO-8601 UTC instants.
- [ ] Reservation display uses parking location timezone.
- [ ] Money uses minor units plus currency.
- [ ] Enums match backend raw values.
- [ ] Unknown enum behavior is deliberate.
- [ ] Search sends supported filters only and handles unsupported/ignored filters gracefully.
- [ ] API versioning is handled once backend introduces it.

## Auth and Security Checks

- [ ] Auth model is explicitly documented: cookie/session or mobile token/OIDC.
- [ ] Secrets are stored in Keychain or controlled secure storage.
- [ ] Passwords are never stored.
- [ ] Tokens, cookies, XSRF values, payment secrets, gate codes, and license plates are not logged.
- [ ] Logout clears Keychain/cookies/session cache and sensitive local data.
- [ ] Session expired state returns user to sign-in without data corruption.
- [ ] Push device token is registered only after sign-in and permission.
- [ ] Device token is not logged.
- [ ] Mock payment is impossible in production configuration.
- [ ] ATS is enabled and production uses HTTPS only.
- [ ] Privacy manifest exists.
- [ ] Permission descriptions match actual behavior.

## Performance Checks

- [ ] App launch is not blocked on nonessential network calls.
- [ ] Map pan/zoom remains responsive.
- [ ] Search results rendering does not block main thread.
- [ ] Images, if introduced, use caching and stable dimensions.
- [ ] Large lists use lazy loading/pagination.
- [ ] Reservation/payment operations show progress and prevent duplicate taps.
- [ ] Network retries use backoff and do not retry unsafe mutations without idempotency.
- [ ] Logging/analytics are not on hot UI paths.

## Accessibility Checks

- [ ] All tappable controls meet 44x44 point target.
- [ ] VoiceOver labels are meaningful.
- [ ] VoiceOver order is logical.
- [ ] Map pins have list alternatives.
- [ ] Color is not the only status indicator.
- [ ] Dynamic Type works through accessibility sizes.
- [ ] Reduce Motion is respected.
- [ ] Increase Contrast remains legible.
- [ ] Forms expose validation errors accessibly.
- [ ] Payment and reservation failures are announced.

## Testing Checks

- [ ] Unit tests cover DTO decoding, date/time, money, errors, and idempotency keys.
- [ ] View model tests cover loading, empty, error, offline, and success states.
- [ ] Reservation idempotency behavior is tested.
- [ ] Payment action/failure behavior is tested.
- [ ] Location permission behavior is tested.
- [ ] Push registration/deep-link routing is tested.
- [ ] UI smoke tests cover critical customer path.
- [ ] Backend `mvn verify` remains green.
- [ ] iOS build/test commands are documented and runnable.
- [ ] API contract fixtures exist for key endpoints.

## App Store Readiness Checks

- [ ] Bundle ID and signing are configured.
- [ ] App icon and launch screen are complete.
- [ ] Privacy manifest is complete.
- [ ] Permission descriptions are complete.
- [ ] Push entitlement is configured if notifications are used.
- [ ] Apple Pay entitlement is configured if Apple Pay is offered.
- [ ] Privacy policy, terms, and support URL are linked.
- [ ] Account deletion path exists.
- [ ] Crash reporting is configured with dSYM upload.
- [ ] Analytics consent/ATT decision is documented.
- [ ] Export compliance, age rating, and review notes are prepared.
- [ ] Production backend URL and monitoring are configured.

## Anti-Patterns to Reject

- [ ] WebView-style UI.
- [ ] Web-first navigation.
- [ ] Insecure token storage.
- [ ] Hardcoded API URLs.
- [ ] Missing loading/error states.
- [ ] No idempotency for reservation creation.
- [ ] Poor date/time handling.
- [ ] Untyped networking.
- [ ] Rental-car terminology.
- [ ] Oversized god view models.
- [ ] Business logic embedded directly in SwiftUI views.
- [ ] Mock payment enabled in production.
- [ ] Raw card data collected by SpotLink.
- [ ] Push payloads containing gate codes, license plates, or payment details.
- [ ] Analytics events carrying precise location or support message content.
- [ ] Global mutable singleton services that block tests.
- [ ] Silent failure on payment or reservation creation.
- [ ] Duplicate reservation created after retry or double tap.
- [ ] Admin/operator features visible to unauthorized users.

## Review Output Template

Use this structure when reviewing the implementation:

```text
Summary:
- Native iOS readiness:
- API compatibility:
- Security/privacy:
- QA confidence:

Blocking findings:
- [P0] ...

Required before TestFlight:
- ...

Required before App Store:
- ...

Approved areas:
- ...

Residual risk:
- ...
```

