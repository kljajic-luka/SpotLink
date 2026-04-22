# SpotLink Mobile API Contract Package

Date: 2026-04-22

## Purpose

This package is the authoritative mobile API contract for the current SpotLink backend snapshot. It exists so the native iOS implementation can align DTOs, endpoint paths, authentication behavior, no-content handling, idempotency, error decoding, pagination, and contract tests without guessing from incomplete Swift models.

This is a documentation and fixture package only. It does not modify backend, frontend, or iOS implementation files.

## Source Files Inspected

Primary sources:

- `SPOTLINK_BACKEND_FOUNDATION_MIGRATION.md`
- `docs/ios-enterprise-readiness/API_IOS_COMPATIBILITY_AUDIT.md`
- `docs/ios-enterprise-readiness/IMPLEMENTATION_ISSUE_BACKLOG.md`
- `apps/backend/src/main/java/com/spotlink`
- `apps/backend/src/main/resources/application.properties`
- `apps/frontend/src/app/foundation`
- `apps/ios/SpotLink/Sources/SpotLink`

Backend authority files:

- Controllers under `auth`, `user`, `vehicle`, `location`, `reservation`, `payment`, `support`, `notification`, `operator`, `admin`, `analytics`, and `core`.
- DTO records and enum classes in each backend module.
- `SecurityConfig`, `JwtService`, `ApiErrorResponse`, `ApiPage`, and `GlobalExceptionHandler`.

## Current Confidence Level

Confidence: high for the current local backend snapshot after the mobile auth lifecycle hardening pass.

The contract is based on source code, not a running OpenAPI export. The repository is actively changing, and several backend and iOS files are untracked or modified. If another agent changes endpoint paths or DTOs after this package is written, regenerate or update the contract before using it as a release gate.

## How iOS Agents Should Use This Package

iOS agents should:

- Treat `SPOTLINK_MOBILE_API_CONTRACT.md` as the source of truth for endpoint behavior.
- Use `json-fixtures/` for `Codable` decoding tests.
- Use `SWIFT_DTO_ALIGNMENT_GUIDE.md` when naming Swift DTOs and mapping backend enum raw values.
- Update the iOS API client so it handles `204 No Content`, `202 Accepted` with no body, `400 VALIDATION_ERROR`, `401`, `403`, `404`, `409`, and `5xx`.
- Decode backend shapes exactly, especially nested location search, payment confirmation, notification read fields, support enums, and profile/operator/admin metrics.
- Preserve idempotency keys across mobile retry boundaries for reservation and payment creation.

## How Backend Agents Should Use This Package

Backend agents should:

- Treat `openapi-mobile-v1.yaml` as the draft contract to make real through generated OpenAPI or tested examples.
- Use `API_CONTRACT_GAPS_FOR_BACKEND.md` as the prioritized backend backlog for mobile hardening.
- Avoid changing enum raw values or DTO field names without versioning the API.
- Add missing lifecycle endpoints deliberately rather than forcing iOS to rely on local-only behavior.
- Add contract tests that assert these fixtures remain valid.

## Known Limitations

- The backend preserves existing `/api/...` routes and now also exposes `/api/v1/...` aliases for the mobile-critical API surface.
- `/auth/token` now returns access and refresh tokens. Refresh-token rotation and revocation endpoints are implemented.
- Payment provider behavior is still foundation/mock-grade.
- APNs provider and device token deactivation are missing.
- Search is not yet map-grade for viewport/radius/availability ranking.
- Account deletion/privacy endpoints are missing.
- The OpenAPI file is a hand-authored draft aligned to current code, not generated from backend annotations.
