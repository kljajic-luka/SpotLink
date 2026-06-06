# SpotLink App Store Privacy Readiness

This is an engineering checklist for App Store privacy metadata and Internal TestFlight readiness. It is not legal advice and does not replace owner/legal review of the public Privacy Policy, Terms, support process, or App Store Connect privacy answers.

## Current iOS Manifest Scope

`apps/ios/Resources/PrivacyInfo.xcprivacy` is no longer a placeholder. It declares:

- `NSPrivacyTracking=false` and no tracking domains.
- App-owned UserDefaults access with required reason `CA92.1`.
- Data types sent to SpotLink backend systems for app functionality, analytics, support, notification lifecycle, and diagnostics.

No APNs entitlement, Apple Pay entitlement, Associated Domains entitlement, tracking entitlement, or third-party crash SDK claim is enabled in this slice. Backend APNs provider code is scaffolded but disabled by default and not physically verified without Apple credentials/provisioning.

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
| Analytics | app/screen/login/reservation/payment/support events, session ID, event properties | internal product/operational analytics |
| Notifications | APNs device token, platform, active/deactivated state, notification preference flags | token lifecycle, server-side preference enforcement, and backend delivery readiness; real APNs delivery is not enabled or physically verified |
| Diagnostics | request IDs, API error references, operational logs | debugging, abuse investigation, support correlation |

## App Store Connect Privacy Checklist

Before signed TestFlight or App Review, the owner should confirm App Store Connect answers against the manifest and actual backend behavior:

- Contact info: name, email address, phone number.
- Location: precise location when the user grants location access.
- User content/other data: vehicle details including license plate when provided, support messages, deletion requests.
- Purchases: reservation/payment-attempt history and parking booking state.
- Identifiers: SpotLink user ID and APNs/device token lifecycle data.
- Usage data: product interaction analytics.
- Diagnostics: request IDs and backend/client diagnostic records.
- Tracking: currently no cross-app tracking and no tracking domains.
- Payment info: do not mark raw card collection unless a future PSP integration changes the data flow.

If real PSP, APNs entitlement/credentials, crash reporting, attribution, marketing, or third-party analytics SDKs are enabled later, update both the manifest and App Store Connect answers before upload.

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
