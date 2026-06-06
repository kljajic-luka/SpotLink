# SpotLink App Store Privacy Readiness

This is an engineering checklist for App Store privacy metadata and Internal TestFlight readiness. It is not legal advice and does not replace owner/legal review of the public Privacy Policy, Terms, support process, or App Store Connect privacy answers.

## Current iOS Manifest Scope

`apps/ios/Resources/PrivacyInfo.xcprivacy` is no longer a placeholder. It declares:

- `NSPrivacyTracking=false` and no tracking domains.
- App-owned UserDefaults access with required reason `CA92.1`.
- Data types sent to SpotLink backend systems for app functionality, analytics, support, notification lifecycle, and diagnostics.

No APNs entitlement, Apple Pay entitlement, Associated Domains entitlement, tracking entitlement, or third-party crash SDK claim is enabled in this slice. Backend APNs provider code is scaffolded but disabled by default and not physically verified without Apple credentials/provisioning. iOS diagnostics are first-party and local/provider-neutral: DEBUG builds can retain sanitized API failure summaries, while Release/Staging default to no-op until a real crash provider is selected.

Validate plist syntax:

```bash
make validate-ios-privacy-config
```

## Data Collection Audit

The current app/backend/frontend surfaces process the following data classes:

| Area | Examples | Current purpose |
| --- | --- | --- |
| Account/contact | first name, last name, email, optional phone, user ID, roles | account creation, auth/session, profile, support, operator/admin access |
| Location/search | user-granted coordinates, search query/filters, parking location/resource IDs | nearby parking search, availability, reservation flow |
| Vehicle | vehicle type, dimensions, EV/accessibility flags, license plate when provided | fit checks, reservation context |
| Reservations | reservation IDs, booking codes, start/end time, location/resource, status, cancellation/no-show/refund markers | booking lifecycle and support/admin/operator workflows |
| Payments | provider intent/attempt IDs, amount/currency/status, mock method metadata in allowed non-production modes | payment authority and future PSP reconciliation; raw card details are not collected or stored |
| Support/account deletion | ticket subject/category/message/status and deletion-request tickets | support intake and human-reviewed account deletion workflow |
| Analytics | first-party app/screen/login/reservation/payment/support events, session ID, allowlisted low-sensitivity event properties | internal product/operational analytics; iOS submission is disabled by default until local analytics consent is enabled |
| Notifications | APNs device token, platform, active/deactivated state, notification preference flags | token lifecycle, server-side preference enforcement, and backend delivery readiness; real APNs delivery is not enabled or physically verified |
| Diagnostics | request IDs, backend error codes, HTTP status, app environment/version/build, operational logs | debugging, abuse investigation, support correlation; no request/response bodies, tokens, contact data, plates, precise coordinates, addresses, payment method data, or support-message content |

## App Store Connect Privacy Checklist

Before signed TestFlight or App Review, the owner should confirm App Store Connect answers against the manifest and actual backend behavior:

- Contact info: name, email address, phone number.
- Location: precise location when the user grants location access.
- User content/other data: vehicle details including license plate when provided, support messages, deletion requests.
- Purchases: reservation/payment-attempt history and parking booking state.
- Identifiers: SpotLink user ID and APNs/device token lifecycle data.
- Usage data: first-party product interaction analytics, only when analytics consent/local policy enables submission.
- Diagnostics: request IDs, backend error codes, HTTP status, app environment/version/build, and backend/client diagnostic records.
- Tracking: currently no cross-app tracking and no tracking domains.
- Payment info: do not mark raw card collection unless a future PSP integration changes the data flow.

Current analytics and diagnostics do not use IDFA, tracking domains, cross-app tracking, ATT, third-party analytics SDKs, or third-party crash SDKs. If real PSP, APNs entitlement/credentials, crash reporting, attribution, marketing, or third-party analytics SDKs are enabled later, update both the manifest and App Store Connect answers before upload.

## Diagnostics And Crash Reporting Status

The iOS app now has a provider-neutral `DiagnosticsReporter` abstraction and a DEBUG-only Profile diagnostics surface. It captures only privacy-safe support metadata from central API failures:

- backend error `code`
- backend `requestId`
- HTTP status
- app environment
- app version/build

It intentionally does not capture request/response bodies, bearer or refresh tokens, APNs tokens, password-reset tokens, emails, phone numbers, license plates, precise coordinates, addresses, payment method data, or support message content. `make validate-ios-diagnostics-readiness` verifies privacy plist state, Release/Staging dSYM build settings, absence of checked-in crash SDK/upload hooks, and focused diagnostics tests. Real crash reporting still requires provider selection, credentials/DSN, dSYM upload configuration, privacy review, alert ownership, and TestFlight/device proof.

## Legal And Support URLs

The app and frontend now use owner-owned URLs instead of `.local` placeholders:

- Privacy Policy: `https://spotlink.app/privacy`
- Terms: `https://spotlink.app/terms`
- Support: `https://spotlink.app/support`
- Support email: `support@spotlink.app`
- Account deletion information: `https://spotlink.app/account-deletion`

These URLs must serve real owner-approved content before external TestFlight/App Review. The repo only wires the destinations.

## Account Deletion Status

The product supports request intake and a human-reviewed admin fulfillment action:

- `POST /api/users/me/deletion-request`
- `POST /api/v1/users/me/deletion-request`
- `POST /api/admin/support-cases/{ticketId}/process-account-deletion`
- `POST /api/v1/admin/support-cases/{ticketId}/process-account-deletion`

Requests create or return an unresolved support ticket in category `ACCOUNT` with subject `Account deletion request`. Admin fulfillment is idempotent and only closes the ticket when processing succeeds. It marks the user `DELETED`, anonymizes direct profile PII, revokes refresh/password-reset/device-token artifacts, clears preferences/idempotency state, anonymizes vehicle/support-message owner fields, and preserves reservation/payment/audit/support referential history. Active/future reservations, disputed reservations, and unresolved payment state block fulfillment with explicit reason codes. Legal retention decisions, payment/fraud retention policy, and public process wording remain human/provider-owned.

## Remaining External Blockers

- Published legal policy/terms/support/account-deletion pages.
- Apple Developer signing, provisioning, App Store Connect app records, and human-controlled TestFlight upload.
- Real staging deployment and production-like domain/runtime monitoring.
- Real PSP provider, credentials, SCA/deep-link return, webhook signature verification, capture/refund reconciliation, and settlement reporting.
- APNs credentials, Push Notifications entitlement, physical-device delivery validation, and final privacy-reviewed payload policy.
- Crash reporting provider, DSN/credentials, dSYM upload automation, alert owner, and device/TestFlight proof.
