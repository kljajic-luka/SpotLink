# App Store Readiness Checklist

Date: 2026-04-22

Use this checklist before internal TestFlight, external TestFlight, and App Store submission. Items marked `Required for App Store` should not be deferred if the feature is present in the submitted build.

## Apple Developer Account

- [ ] Apple Developer Program membership is active.
- [ ] Legal entity and banking/tax status are complete if paid features require them.
- [ ] App Store Connect team roles are assigned.
- [ ] Separate access exists for engineering, product, support, and release owners.

Required for App Store: yes.

## Bundle ID

- [ ] Production bundle ID is reserved, for example `com.spotlink.ios`.
- [ ] Staging/internal bundle ID exists if needed, for example `com.spotlink.ios.staging`.
- [ ] Bundle ID matches APNs, Associated Domains, Apple Pay, and Sign in with Apple entitlements if used.
- [ ] Bundle ID is not changed between release candidates.

Required for App Store: yes.

## Signing

- [ ] Automatic or manual signing approach is documented.
- [ ] Distribution certificate is valid.
- [ ] CI has signing access through a secure mechanism.
- [ ] No developer personal certificate is required for release builds.

Required for App Store: yes.

## Provisioning Profiles

- [ ] Development profile supports local device testing.
- [ ] Ad Hoc profile exists if needed outside TestFlight.
- [ ] App Store profile exists for release.
- [ ] Profiles include required entitlements.
- [ ] Profiles are renewed before expiry.

Required for App Store: yes.

## App Icons

- [ ] App icon set is complete in `Assets.xcassets`.
- [ ] Icon works in light and dark contexts.
- [ ] No alpha channel in App Store icon.
- [ ] No Apple trademark or misleading parking authority branding.

Required for App Store: yes.

## Launch Screen

- [ ] Native launch screen exists.
- [ ] Launch screen is fast and static.
- [ ] No network dependency.
- [ ] No marketing content or misleading loading UI.
- [ ] Looks correct in light/dark mode and all supported sizes.

Required for App Store: yes.

## Privacy Manifest

- [ ] `PrivacyInfo.xcprivacy` exists.
- [ ] Required reason APIs are declared if used.
- [ ] Third-party SDK privacy manifests are included.
- [ ] Tracking domains are declared if applicable.
- [ ] Manifest matches App Store privacy answers.

Required for App Store: yes.

## Permission Descriptions

- [ ] `NSLocationWhenInUseUsageDescription` exists and accurately describes parking search use.
- [ ] `NSUserNotificationsUsageDescription` is not an Info.plist key, but in-app copy explains notification value before prompt.
- [ ] Camera/photo/library descriptions exist only if support attachments or profile photos are implemented.
- [ ] Face ID description exists only if biometric app lock is implemented.

Required for App Store: yes for every permission used.

## Location Permission Copy

Recommended copy:

`SpotLink uses your location to show nearby parking and estimate distance.`

Checklist:

- [ ] Copy matches actual behavior.
- [ ] App works without location permission.
- [ ] Permission is requested in context, not on first launch.
- [ ] No background location permission requested for MVP.

Required for App Store: yes if location is used.

## Push Notification Entitlement

- [ ] Push notification entitlement enabled.
- [ ] APNs key/certificate configured.
- [ ] Sandbox and production environments tested.
- [ ] Device token registration endpoint works with platform `IOS`.
- [ ] Token deactivation/logout behavior exists or is tracked as a launch blocker if notifications are in scope.
- [ ] Payloads are privacy-safe.

Required for App Store: yes if push is used.

## Apple Pay Entitlement

- [ ] Apple Pay entitlement configured if Apple Pay is in scope.
- [ ] Merchant ID created.
- [ ] PSP supports Apple Pay for SpotLink merchant account.
- [ ] Apple Pay payment sheet tested on device.
- [ ] Non-Apple Pay fallback exists if required by market/payment strategy.

Required for App Store: yes if Apple Pay is offered.

## TestFlight Internal Testing

- [ ] Internal testers group created.
- [ ] Build uses staging or approved internal backend.
- [ ] Mock payment is clearly limited to internal builds.
- [x] Release/Staging dSYM generation and local diagnostics scaffold validated.
- [ ] Crash reporting provider enabled for signed TestFlight builds.
- [ ] Test accounts created for CUSTOMER, OPERATOR, SUPPORT, ADMIN.
- [ ] Known limitations documented in release notes.

Required before external TestFlight.

## TestFlight External Testing

- [ ] Beta App Review submission prepared.
- [ ] External tester groups defined.
- [ ] Public beta instructions avoid exposing secrets.
- [ ] Payment test instructions documented.
- [ ] Support intake process ready.
- [ ] Privacy policy URL live.
- [ ] App uses production-like auth, APNs, and payment configuration.

Required before broad user testing.

## App Store Screenshots

- [ ] Required device sizes captured.
- [ ] Screenshots show real native app screens, not marketing-only art.
- [ ] Customer search/map screen included.
- [ ] Reservation flow or confirmed reservation included.
- [ ] Vehicle/profile/support screen included if part of MVP.
- [ ] Operator/admin screens shown only if available to target App Store users.
- [ ] No private data in screenshots.

Required for App Store: yes.

## App Store Description

- [ ] Description clearly states SpotLink helps users find and reserve parking.
- [ ] Features described match shipped app behavior.
- [ ] No claims about unavailable cities, payment methods, or guarantees.
- [ ] Operator/admin capabilities described only if public users can access them.

Required for App Store: yes.

## Privacy Policy

- [ ] Public URL available.
- [ ] Covers account data, vehicles/license plates, location, reservations, payments, support, notifications, analytics, crash reports, and deletion.
- [ ] Lists third-party processors.
- [ ] Explains contact/support channel.

Required for App Store: yes.

## Terms of Service

- [ ] Public URL available.
- [ ] Covers reservation rules, cancellations, payment authorization, operator responsibilities, support limits, and account restrictions.
- [ ] Linked in app.

Required for App Store: strongly recommended and required if business/legal needs it.

## Support URL

- [ ] Public support URL exists.
- [ ] App Store Connect support URL configured.
- [ ] In-app support path works.
- [ ] Support team can handle TestFlight/App Store issues.

Required for App Store: yes.

## Account Deletion Requirement

- [ ] In-app account deletion is available or a compliant request flow exists.
- [ ] Deletion flow is discoverable from Profile.
- [ ] Backend process handles deletion/anonymization requirements.
- [ ] User is told what data may be retained for legal/payment/fraud reasons.

Required for App Store: yes for account-based apps.

## Crash Reporting

- [x] Provider-neutral iOS diagnostics abstraction exists.
- [x] Nonfatal API diagnostics capture only `code`, `requestId`, HTTP status, environment, version/build.
- [x] Release/Staging build settings generate dSYMs.
- [x] No third-party crash SDK, DSN, or dSYM upload hook is committed.
- [ ] Crash reporting provider selected.
- [ ] dSYM upload automated.
- [ ] Provider PII redaction/breadcrumb policy approved.
- [ ] Crash-free sessions/users monitored.
- [ ] Release owner receives alerts.
- [ ] Physical-device/TestFlight test crash and nonfatal event verified with the selected provider.

Required before external TestFlight.

## Analytics Consent

- [ ] Analytics behavior documented.
- [ ] User opt-in/opt-out implemented if required by policy/market.
- [ ] ATT prompt used only if tracking under Apple's definition occurs.
- [ ] No IDFA use unless explicitly approved.
- [ ] Analytics payload excludes PII and precise location.

Required for App Store: yes.

## Export Compliance

- [ ] App Store encryption questions answered.
- [ ] Standard HTTPS-only encryption use documented.
- [ ] No custom cryptography unless reviewed.

Required for App Store: yes.

## Age Rating

- [ ] Age rating questionnaire completed.
- [ ] App does not include inappropriate content.
- [ ] User-generated support content is not publicly visible.

Required for App Store: yes.

## Review Notes

- [ ] Test account credentials provided.
- [ ] Roles available for review if necessary.
- [ ] Payment test details provided.
- [ ] Location testing guidance provided.
- [ ] Any feature flags or staged markets explained.
- [ ] Contact information for review questions included.

Required for App Store: yes.

## Production Backend Environment

- [ ] Production API base URL configured.
- [ ] Database migrations applied and verified.
- [ ] Production secrets are not in source.
- [ ] TLS certificate valid.
- [ ] CORS/cookie/token settings correct for production.
- [ ] Mock payment disabled.
- [ ] APNs production configured.
- [ ] Rate limiting enabled.
- [ ] Backups configured.
- [ ] Monitoring and alerting configured.

Required for App Store: yes.

## Monitoring After Release

- [ ] Crash-free rate monitored.
- [ ] Login failures monitored.
- [ ] Search latency/errors monitored.
- [ ] Reservation create success/conflict/errors monitored.
- [ ] Payment authorization/failure monitored.
- [ ] Push delivery/token errors monitored.
- [ ] Support ticket volume monitored.
- [ ] App Store reviews monitored.
- [ ] Rollback/feature flag plan documented.

Required for launch day.
