# TestFlight Gap Report

Date: 2026-04-22

## Current Build Reality

What can be built now:

- Backend tests pass with `mvn -f apps/backend/pom.xml test`.
- Frontend production build passes with `npm run build`.

What cannot be built now:

- A TestFlight-ready iOS app archive cannot be built from the current checked-in iOS tree.
- No Xcode project/workspace was found.
- `swift test` fails.
- An iOS simulator Swift build could not be attempted in this environment because `xcrun` could not locate the `iphonesimulator` SDK.

Diagnostic results:

```text
xcodebuild -list: not applicable; no Xcode project/workspace found under apps/ios.
swift test: failed with compile errors and an active-file-modified warning.
xcrun --sdk iphonesimulator swift build --triple arm64-apple-ios17.0-simulator: failed, SDK not found.
mvn -f apps/backend/pom.xml test: passed, 4 tests.
npm run build: passed.
```

## Missing Apple Project Settings

Missing:

- Xcode project or workspace.
- App target.
- Test target with real tests.
- Release configuration.
- Debug/staging/production build settings.
- Archive scheme.
- App resources.

Impact: internal TestFlight is blocked.

## Signing and Provisioning Assumptions

No evidence found for:

- Development team.
- Signing style.
- Provisioning profiles.
- Distribution certificate.
- CI signing setup.

Impact: TestFlight upload is blocked until an app target and signing strategy exist.

## Bundle Identifier Readiness

No bundle ID was found because no app target/Info.plist was found.

Required:

- Production bundle ID, for example `com.spotlink.ios`.
- Staging/internal bundle ID if needed.
- Bundle ID alignment with APNs and Apple Pay entitlements if used.

Impact: TestFlight upload is blocked.

## App Icon and Launch Screen Readiness

No asset catalog, app icon, or launch screen was found.

Required:

- `Assets.xcassets` with app icon.
- Native launch screen.
- Light/dark appearance review.

Impact: TestFlight/App Store packaging is blocked.

## Privacy Manifest Readiness

No `PrivacyInfo.xcprivacy` was found.

Required:

- Required reason API declarations if used.
- Third-party SDK privacy manifests once SDKs are added.
- Alignment with App Store privacy answers.

Impact: external TestFlight/App Store readiness blocked.

## Permission Strings Readiness

No Info.plist was found, so permission strings are missing.

Required if features are used:

- `NSLocationWhenInUseUsageDescription`
- Camera/photo usage strings if support attachments/profile photos are added.
- Face ID usage string only if biometric lock is added.

Impact: location prompts cannot ship correctly.

## Push Notification Entitlement Readiness

Current state:

- Push manager code exists.
- No entitlements file found.
- No APNs environment config found.
- No app delegate bridge was found.

Blocking gaps:

- Add `aps-environment` entitlement.
- Add app delegate registration callbacks.
- Inject real `NotificationService` into `PushNotificationManager`.
- Fix device token API contract.

Impact: APNs cannot be verified for TestFlight.

## Location Permission Readiness

Current state:

- CoreLocation manager exists.
- No permission string found.
- No map/search UI exists.

Blocking gaps:

- Add Info.plist permission copy.
- Add in-context permission education.
- Add denied/restricted/manual search states.
- Add MapKit search experience.

Impact: core customer flow is not TestFlight-ready.

## Crash Reporting Readiness

No crash reporting SDK or dSYM upload setup was found.

Required before external TestFlight:

- Crash provider selected.
- dSYM upload automated.
- PII redaction rules.
- Alert owner.

Impact: external TestFlight blocked.

## Analytics and Privacy Consent Readiness

Current state:

- First-party analytics service exists.
- iOS sends the backend batch shape when enabled.
- iOS analytics submission is disabled by default until local consent/policy enables it.
- Backend enforces event/property allowlists, payload limits, and PII/secret rejection.
- No third-party analytics SDK, IDFA, cross-app tracking, or ATT prompt is present.

Required:

- Legal/privacy owner must approve public policy wording and App Store Connect analytics answers.
- Product/legal must decide whether and how to expose user-facing analytics controls before broad external testing.
- Any future third-party analytics, attribution, tracking domains, or IDFA use must trigger privacy manifest/App Store Connect/ATT re-review.

Impact: analytics is technically hardened for first-party pre-staging use, but legal/policy approval remains required before broad external TestFlight.

## Account Deletion Readiness

No in-app account deletion path was found.

Required:

- Profile account deletion request or deletion flow.
- Backend support process or endpoint.
- Privacy policy language.

Impact: App Store submission blocked for account-based app.

## Backend Environment Readiness

Strengths:

- Backend tests pass.
- Request IDs, structured errors, auth, reservations, payments, notifications, support, operator, admin, and analytics foundations exist.

Blockers/gaps:

- Mock payment defaults to enabled.
- JWT has default secret fallback and no refresh/revocation.
- API versioning missing.
- DTO drift between iOS and backend.
- APNs credentials, entitlement, and physical-device delivery validation missing.
- Search/geospatial behavior not production-grade.

## Internal TestFlight Blockers

1. No Xcode app target/project.
2. iOS source does not compile under current SwiftPM test setup.
3. No app icon/launch screen/resources.
4. Auth registration bug and incomplete mobile token lifecycle.
5. Core DTO mismatches for search, reservation, payment, notifications, support, profile, operator, and admin.
6. No iOS tests.
7. No location permission string.
8. No real search/map/reservation flow.

## External TestFlight Blockers

1. All internal TestFlight blockers.
2. No privacy manifest.
3. No crash reporting.
4. No APNs entitlement/provider verification.
5. No production-like payment provider/Apple Pay decision.
6. No account deletion path.
7. Analytics privacy controls exist, but owner-approved user-facing analytics consent/policy wording is still required.
8. No API versioning.
9. No security hardening for JWT refresh/revocation/rate limiting.
10. No accessibility and Dynamic Type validation.
