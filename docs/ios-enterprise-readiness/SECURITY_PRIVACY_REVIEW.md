# Security and Privacy Review

Date: 2026-04-22

## Review Scope

This review focuses on mobile security and privacy readiness for a native iOS SpotLink app. It uses the audited backend foundation and inferred product direction. It does not claim that concurrent implementation work is complete or secure.

Severity labels:

- `Critical`: must be fixed before any production or external user release.
- `High`: must be fixed before App Store MVP or broad TestFlight.
- `Medium`: should be fixed before scale or sensitive feature expansion.
- `Low`: hardening or policy cleanup.

## Executive Security Findings

| Severity | Finding | Current Evidence | Required Action |
| --- | --- | --- | --- |
| Critical | Mock payment cannot ship as production payment path. | Backend has `MockPaymentProvider` and `spotlink.mock-payment.enabled` defaults to true. | Release builds and production backend must use a real PSP or disable paid reservations. Add config guard. |
| High | Production mobile auth model is not finalized. | Backend is cookie/session and XSRF oriented. | Decide hardened native cookie session or mobile token model before external TestFlight. |
| High | Password reset delivery is foundation-only. | Reset token is generated and token prefix is logged; no email provider integration observed. | Add secure email delivery, remove token material from logs, rate limit reset requests. |
| High | Rate limiting and abuse prevention are not visible. | Auth, analytics, support, geocode, and reservation endpoints do not show rate-limit controls. | Add API rate limits, account lockout, IP/device throttles, and analytics abuse controls. |
| High | APNs delivery and token lifecycle are incomplete. | Device token registration exists, but provider is mock and unregister endpoint is missing. | Implement APNs, token deactivation, privacy-safe payloads, and preference enforcement. |
| Medium | API versioning is missing. | Endpoints are unversioned under `/api`. | Add `/api/v1` or version header before mobile clients stabilize. |
| Medium | Location and license plate data require explicit privacy handling. | Vehicle and location models include license plate, coordinates, address, and reservation data. | Classify PII, limit logs/caches, update privacy policy and privacy manifest. |
| Medium | Analytics endpoint is public and consent model is undefined. | `/analytics/events` is public and CSRF-exempt. | Add consent controls, rate limiting, schema validation, and PII stripping. |
| Low | Certificate pinning should be a staged decision. | TLS is assumed but pinning is not specified. | Use ATS and strong TLS first; consider pinning only with an operational rotation plan. |

## Auth Model Recommendations

Recommended production direction:

- Use OIDC/OAuth authorization code with PKCE if an identity provider is selected.
- If local auth remains, provide backend-issued short-lived access tokens and rotating refresh tokens for native clients.
- Store refresh tokens in Keychain.
- Keep access tokens in memory where feasible.
- Rotate refresh tokens on use and revoke token families on reuse detection.
- Support server-side revocation on logout.
- Support account lockout, email verification, and suspicious login monitoring.

Acceptable foundation/testing direction:

- Native iOS can integrate with current cookie/session auth using `URLSession` cookie storage and XSRF token handling.
- This path should be explicitly treated as interim until production mobile security requirements are approved.

## Cookie/Session Versus Bearer/Mobile Token

### Cookie/session advantages

- Aligns with current Spring Security foundation.
- Reuses existing XSRF and session invalidation behavior.
- Lower backend implementation cost for early testing.

### Cookie/session risks for native iOS

- XSRF is less relevant for native clients but still required by current backend for mutations.
- Cookie persistence and expiration behavior need careful control in `URLSession`.
- Mobile refresh/rotation/session metadata is harder to reason about.
- Device-level revocation and session listing are not first-class.

### Bearer/mobile token advantages

- Better native app fit.
- Works naturally with Keychain.
- Supports short-lived access tokens and rotating refresh tokens.
- Easier to implement session/device management.
- Avoids XSRF complexity for native clients.

### Bearer/mobile token risks

- Requires more backend work.
- Requires refresh token replay detection and revocation logic.
- Token leakage has high impact if logging/storage rules are weak.

Recommendation: use cookie/session for internal foundation testing only if needed; define a production mobile token or OIDC contract before external TestFlight.

## Keychain Storage

Store only:

- Refresh token or session secret.
- Device installation identifier if needed.
- Minimal session metadata needed to clean up logout.

Do not store:

- Passwords.
- Raw card data.
- Gate codes or access instructions unless a reviewed encrypted storage plan exists.
- Full profile JSON.
- License plate data unless encrypted local cache is required.

Keychain requirements:

- Use `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` or stricter based on background needs.
- Prefer device-only accessibility for tokens.
- Clear credentials on logout and account deletion.
- Do not sync auth secrets through iCloud Keychain.

## Refresh Token Handling

If backend adopts refresh tokens:

- Use rotating refresh tokens.
- Keep access tokens short-lived.
- Revoke token family on refresh reuse detection.
- Add endpoint for logout/revocation.
- Include device ID and app version metadata in session records.
- Never return refresh tokens to JavaScript or web contexts.
- Never log tokens or token hashes in app or backend logs.

## Logout and Session Revocation

Current state:

- `POST /auth/logout` invalidates HTTP session and clears XSRF token.

Required for native release:

- Logout must clear Keychain, cookies, cached profile, cached notifications, and pending analytics.
- Backend must revoke refresh token/session.
- Device token should be deactivated or disassociated when backend supports it.
- The app must handle logout while offline by clearing local secrets and attempting server revocation later only if safe.

## CSRF Relevance for Native iOS

CSRF protects browser-cookie flows. Native apps are not exposed to the same browser cross-site request mechanics, but current backend requires XSRF for state-changing authenticated endpoints.

If cookie/session auth remains:

- iOS must read the `XSRF-TOKEN` cookie and send `X-XSRF-TOKEN` for mutations.
- The backend must ensure cookie attributes are secure in production: `Secure`, appropriate `SameSite`, no broad domain leakage.

If bearer/mobile tokens are adopted:

- CSRF can be disabled for native bearer-token endpoints.
- Keep CSRF for browser/web endpoints.
- Separate web and mobile security chains if needed.

## TLS and Transport Security

Required:

- HTTPS only in staging and production.
- App Transport Security enabled.
- TLS 1.2+ minimum, TLS 1.3 preferred.
- No production trust exceptions.
- No self-signed production certificates.
- HSTS for web/backend domain where applicable.

TestFlight:

- Internal builds may target local HTTP only behind debug configuration gates.
- External TestFlight should use HTTPS staging.

## Certificate Pinning Recommendation

Do not add certificate pinning as a default MVP requirement unless the team has:

- Certificate rotation runbook.
- Backup pins.
- Emergency app update plan.
- Monitoring for pin failures.

Recommended MVP posture:

- Enforce ATS.
- Use reputable CA.
- Monitor TLS and certificate expiry.
- Consider pinning later for high-risk threat model or regulated deployments.

## PII Classification

High sensitivity:

- Email.
- Phone.
- License plate.
- Precise location and search history.
- Reservation history.
- Payment method display data.
- Support messages and attachments.
- Device tokens.

Medium sensitivity:

- Name.
- Vehicle make/model/color.
- Operator support email.
- Coarse city/region.
- Analytics session ID.

Low sensitivity:

- Public parking location name.
- Public notes.
- Resource type and public price.

Rules:

- Do not log high-sensitivity data.
- Do not include high-sensitivity data in analytics.
- Do not include high-sensitivity data in push payloads.
- Limit local caching of high-sensitivity data.
- Add deletion/export strategy for user data before App Store release.

## Location Privacy

Requirements:

- Request location permission only in context.
- Use When In Use permission for MVP.
- Do not request Always permission unless a future feature requires it and privacy copy is updated.
- Provide manual search when permission is denied.
- Avoid storing precise location history unless necessary.
- Avoid sending continuous location updates.
- Explain location use in `NSLocationWhenInUseUsageDescription`.

Recommended permission copy:

`SpotLink uses your location to show nearby parking and estimate distance.`

## Payment Data Boundaries

Rules:

- Native app must not collect raw card numbers unless using a PSP-approved SDK that keeps SpotLink out of raw PAN handling.
- Backend should not store raw card data.
- Display only PSP-safe card metadata such as brand, last4, expiration, and default status.
- Use Apple Pay only with correct entitlement, merchant ID, PSP support, and business approval.
- Webhooks must be authenticated and idempotent.
- Mock payment must be impossible in production release config.

## Push Notification Privacy

Push payloads must not include:

- Gate codes.
- License plates.
- Full support message body.
- Payment details.
- Full precise address when not necessary.

Payloads should include:

- Notification type.
- User-visible title/body with minimal sensitive content.
- Deep-link target type and ID.
- Badge update where appropriate.

Device token rules:

- Token is personal data.
- Token must be deactivated on logout where possible.
- Token environment must be tracked.
- Token must not be logged.

## Analytics Privacy

Required:

- Consent or legitimate-interest decision documented.
- User preference respected.
- No precise coordinates, license plate, raw address, payment data, gate code, or support body.
- Public analytics endpoint protected by rate limit and schema validation.
- Event properties allowlist.
- Data retention policy.

App Tracking Transparency:

- If analytics are first-party and not used for cross-app tracking, ATT may not be required.
- If any third-party SDK tracks users across apps/sites or uses IDFA, ATT and explicit consent are required.
- Avoid IDFA for MVP.

## GDPR and Privacy Policy Readiness

Before App Store release:

- Publish privacy policy URL.
- Publish terms of service URL.
- Provide account deletion path in app.
- Define data retention periods.
- Define data export process.
- Document processors: hosting, payment provider, push provider, analytics/crash provider, email provider.
- Document legal basis for location, payment, support, and analytics processing.
- Provide support contact.

## Audit Logging

Current state:

- Audit event table and admin audit event listing exist.
- Audit service exists, but broad use across mutations is not evident from the audited files.

Required:

- Audit auth-sensitive events: login failures/lockouts, password resets, logout, token revocation.
- Audit payment state changes.
- Audit operator inventory changes.
- Audit admin role/user changes.
- Audit support escalations and sensitive user lookups.
- Include actor, action, resource type, resource ID, request ID, timestamp, and safe metadata.

## Rate Limiting and Abuse Prevention

Add rate limits for:

- Login.
- Registration.
- Password reset request and completion.
- Geocode/search.
- Reservation quote.
- Reservation creation.
- Payment intent creation/confirmation.
- Support ticket/message creation.
- Device token registration.
- Analytics ingestion.

Add abuse controls:

- Account lockout or progressive delay.
- Device/IP reputation if needed.
- CAPTCHA only where acceptable for web; avoid degrading native UX unless abuse requires it.
- Alerting on repeated payment failures, reservation spam, and support spam.

## Secure Defaults Before TestFlight

Internal TestFlight minimum:

- HTTPS staging backend.
- No production secrets in app bundle.
- Keychain or controlled cookie storage for session.
- Logout clears local credentials.
- Crash reporting enabled in internal mode.
- Analytics off by default or clearly controlled.
- Push payloads privacy-reviewed.
- Mock payment clearly labeled and limited to internal builds.
- Request IDs visible in diagnostics.

External TestFlight minimum:

- Production-like auth and session behavior.
- Rate limiting on auth and analytics.
- APNs configured.
- PSP test mode or production provider path configured.
- Privacy policy and support URL available.
- Account deletion path defined.
- App privacy manifest present.

## Secure Defaults Before App Store Release

Required:

- Production auth/session model approved.
- Mock payment disabled in production.
- Production APNs provider enabled.
- Token/session revocation works.
- Account deletion works or has compliant in-app request flow.
- Privacy policy, terms, support URL live.
- Production secrets managed outside source.
- Rate limits and abuse alerts enabled.
- Monitoring for crashes, failed payments, reservation conflicts, login failures, and push failures.
- Data retention and deletion process documented.
- OpenAPI contract versioned.
- No sensitive data in logs, analytics, crash breadcrumbs, or push payloads.

