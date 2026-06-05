# Pre-Staging Readiness Gate

SpotLink's pre-staging gate is a local hardening layer on top of the normal release gate. It does not deploy infrastructure, send real email, enable APNs, enable a PSP, or perform Apple signing.

## Command

```bash
make pre-staging-gate
```

This target runs:

- `make release-gate`
- focused backend runtime/security tests for hardened profiles, password-reset delivery, and abuse throttling
- focused SwiftPM privacy-safe logging tests

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
- account deletion fulfillment outcomes

Metric tags avoid user identifiers, email, phone, device tokens, reset tokens, bearer tokens, license plates, and request payload data.

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
- APNs provider credentials and entitlement enablement
- Owner-approved legal/privacy pages and App Store Connect answers
