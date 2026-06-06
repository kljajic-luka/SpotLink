# Pre-Staging Readiness Gate

SpotLink's pre-staging gate is a local hardening layer on top of the normal release gate. It does not deploy infrastructure, send real email, enable APNs entitlements, contact APNs, enable a PSP, or perform Apple signing.

## Command

```bash
make pre-staging-gate
```

This target runs:

- `make release-gate`
- focused mobile API contract checks against backend-generated OpenAPI routes and Swift fixture decoding
- focused push delivery readiness checks for provider configuration, preference enforcement, post-commit delivery semantics, invalid-token handling, metrics, and log redaction
- focused first-party analytics privacy checks for event/property allowlists, PII rejection, iOS consent defaults, and backend batch-shape encoding
- focused backend runtime/security tests for hardened profiles, password-reset delivery, and abuse throttling
- focused SwiftPM privacy-safe logging tests

The contract check is also runnable on its own:

```bash
make validate-mobile-api-contract
```

The push delivery check is also runnable on its own:

```bash
make validate-push-delivery-readiness
```

The notification preference policy slice can be run directly:

```bash
make validate-notification-preferences
```

The analytics privacy slice can be run directly:

```bash
make validate-analytics-privacy
```

## Backend Protections

Public abuse-prone endpoints are protected by a configurable in-process fixed-window rate limiter:

- `POST /auth/login` and `/v1/auth/login`
- `POST /auth/token` and `/v1/auth/token`
- customer/operator registration
- password reset request and completion
- analytics ingestion

When a request is throttled, the API returns the normal error envelope with:

- HTTP `429`
- `code=RATE_LIMITED`
- `X-Request-Id`
- `Retry-After`

The limiter is keyed by operation plus client network address. It does not parse, log, or return email, password, reset token, bearer token, or analytics payload values.

## Password Reset Delivery

Password reset now uses `MailProvider` instead of relying on token-generation logs. The default local provider is `safe-log`; it records only provider name, subject, and a stable recipient hash. It never logs message bodies or raw reset tokens.

Staging/production runtime profiles must either:

- set `PASSWORD_RESET_DELIVERY_ENABLED=false`, or
- provide a future production-ready `MailProvider`

Because the repo intentionally contains no real email provider or secrets, the checked-in staging/prod examples disable delivery:

```env
PASSWORD_RESET_DELIVERY_ENABLED=false
MAIL_PROVIDER=none
```

## Observability

The backend records low-cardinality counters for:

- auth failures
- rate-limit blocks
- password reset request outcomes
- payment authority disabled decisions
- push delivery attempted/succeeded/failed/invalid-token/disabled outcomes
- push delivery preference-skipped outcomes
- account deletion fulfillment outcomes

Metric tags avoid user identifiers, email, phone, device tokens, reset tokens, bearer tokens, license plates, and request payload data.

## Analytics Privacy Readiness

SpotLink analytics is first-party and best-effort only:

- no third-party analytics SDK is included
- no IDFA or cross-app tracking is used
- no ATT prompt is added because current behavior is not tracking under Apple's cross-app tracking definition
- iOS analytics submission is disabled by default unless local analytics consent is explicitly enabled
- iOS submits the backend batch shape: `{ "events": [ { "event", "properties", "timestamp", "sessionId" } ] }`
- iOS strips unsafe properties before building a request and never sends exact location, license plates, email/phone, raw user IDs, tokens, raw payment method/card data, or verbose error descriptions
- backend analytics ingestion rejects unknown events, unknown/unsafe property keys, nested property values, more than 20 events, more than 20 properties per event, long property strings, and obvious PII/secrets
- `url` remains accepted in the DTO for compatibility but is not persisted by the backend

Allowed analytics events currently cover app open, screen view, auth, registration, search, reservation, payment availability/intent, support ticket creation, account deletion request, notification preference update, profile update, and coarse error events.

This is engineering privacy scaffolding. Legal owner-approved analytics disclosure, consent wording, and final App Store Connect answers remain external work.

## Push Delivery Readiness

Backend push delivery is provider-shaped but credential-free by default:

- `PUSH_DELIVERY_ENABLED=false` and `PUSH_PROVIDER=none` are the safe staging/production examples.
- `PUSH_PROVIDER=safe-log` is available only for local/internal validation and logs provider name plus stable token hashes, never raw tokens or payload bodies.
- `PUSH_PROVIDER=apns` creates a Pushy-backed APNs adapter and requires bundle ID, team ID, key ID, and APNs private key material from the runtime secret store.
- Notification persistence publishes delivery work after transaction commit, so APNs/provider failures do not roll back the saved in-app notification.
- Delivery is attempted only for active iOS device tokens.
- APNs permanent token failures deactivate the stored device token; transient failures are counted and logged without raw token or payload data.
- Transactional push delivery respects server-side user preferences before provider calls:
  - reservation notifications (`RESERVATION_CONFIRMED`, `RESERVATION_CANCELLED`, `ACCESS_INSTRUCTIONS_READY`) require `reservationAlerts=true`
  - payment action notifications require `paymentAlerts=true`
  - support replies and current operator-facing alert notifications require `supportAlerts=true`
  - `SYSTEM` remains mandatory for account/safety/security-critical notifications
- `marketingOptIn` is not used for transactional push delivery. It remains available in the profile contract for future marketing channels.
- In-app notification inbox persistence remains unchanged when push delivery is skipped by preference; only the outbound push attempt is suppressed.

Staging/production runtime guards require an explicit push policy. If delivery is enabled in a hardened profile, the only accepted provider is `apns`; production rejects `APNS_ENVIRONMENT=sandbox`.

This is not physical-device APNs verification. Apple Developer capabilities, provisioning, APNs credentials, app entitlements, and real delivery smoke tests remain external.

The backend uses the proven Pushy Java APNs client for the adapter rather than hand-rolling HTTP/2 token authentication and APNs response parsing. Automated tests do not make live APNs network calls.

## iOS Privacy-Safe Logging

`SpotLinkLogger` redacts common token patterns before printing debug logs:

- bearer tokens
- `sl_reset_...` reset tokens
- `token=...` query parameters
- JSON `accessToken`, `refreshToken`, `token`, and `authorization` fields

The iOS UI test suite includes a deterministic unauthenticated registration check for the legal links and disabled initial submit state. Authenticated iOS screenshots and manual smoke checks remain separate from this local hardening gate.

## Remaining External Blockers

- Real staging infrastructure and DNS/TLS
- Real email provider, sender domain, and delivery credentials
- Apple signing/TestFlight upload credentials
- PSP selection, credentials, webhook verification, and reconciliation
- APNs provider credentials, entitlement enablement, physical-device delivery validation, and final payload/privacy approval
- Owner-approved legal/privacy pages and App Store Connect answers
