# Pre-Staging Readiness Gate

SpotLink's pre-staging gate is a local hardening layer on top of the normal release gate. It does not deploy infrastructure, send real email, enable APNs entitlements, contact APNs, enable a PSP, or perform Apple signing.

## Command

```bash
make pre-staging-gate
```

This target runs:

- `make release-gate`
- focused mobile API contract checks against backend-generated OpenAPI routes and Swift fixture decoding
- focused push delivery readiness checks for provider configuration, post-commit delivery semantics, invalid-token handling, metrics, and log redaction
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
- account deletion fulfillment outcomes

Metric tags avoid user identifiers, email, phone, device tokens, reset tokens, bearer tokens, license plates, and request payload data.

## Push Delivery Readiness

Backend push delivery is provider-shaped but credential-free by default:

- `PUSH_DELIVERY_ENABLED=false` and `PUSH_PROVIDER=none` are the safe staging/production examples.
- `PUSH_PROVIDER=safe-log` is available only for local/internal validation and logs provider name plus stable token hashes, never raw tokens or payload bodies.
- `PUSH_PROVIDER=apns` creates a Pushy-backed APNs adapter and requires bundle ID, team ID, key ID, and APNs private key material from the runtime secret store.
- Notification persistence publishes delivery work after transaction commit, so APNs/provider failures do not roll back the saved in-app notification.
- Delivery is attempted only for active iOS device tokens.
- APNs permanent token failures deactivate the stored device token; transient failures are counted and logged without raw token or payload data.

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
- APNs provider credentials, entitlement enablement, physical-device delivery validation, and notification preference policy
- Owner-approved legal/privacy pages and App Store Connect answers
