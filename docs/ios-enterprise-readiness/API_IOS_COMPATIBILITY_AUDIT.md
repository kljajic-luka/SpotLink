# API iOS Compatibility Audit

Date: 2026-04-22

Ratings:

- `Ready`: usable by iOS with normal client work.
- `Minor changes needed`: mostly usable, needs cleanup before TestFlight.
- `Major changes needed`: likely to break iOS flows or security expectations.
- `Missing`: required capability was not found.

## Summary

The backend is broader than the earlier readiness package because it now includes a mobile JWT endpoint. However, the iOS client and backend are not contract-compatible in several core modules. The highest-risk mismatches are location search, payment, notifications, reservation quote, support enums, operator/admin/profile metrics, 204/202 response handling, and mobile token lifecycle.

## Compatibility Matrix

| Area | Rating | Evidence | Notes |
| --- | --- | --- | --- |
| Auth/session strategy | Major changes needed | Backend `AuthController.java:141-157`; iOS `AuthService.swift:21-24`, `SessionManager.swift:120-137` | Mobile bearer token exists, but no refresh, revocation, token rotation, session list, or server logout integration. |
| Token or cookie handling | Major changes needed | `APIClient.swift:95-97`, `SecurityConfig.java:57-75` | iOS uses bearer only. Backend still supports cookie/session plus bearer. Contract needs documented split and token lifecycle. |
| CSRF relevance for native clients | Minor changes needed | `SecurityConfig.java:57-65` | `/auth/token` is CSRF-exempt. Bearer clients can avoid XSRF, but docs/tests should prove native mutations work without XSRF when bearer is present. |
| `/auth/login` | Ready for web, minor for iOS | `AuthController.java:61-74` | Works for cookie/session. iOS currently uses `/auth/token` instead. |
| `/auth/logout` | Major changes needed | `AuthController.java:114-123`, `AuthService.swift:65-67` | Backend only invalidates cookie session. iOS does not call it, and bearer tokens are not revoked. |
| `/auth/me` | Ready | `AuthController.java:108-112` | Should work with bearer filter if token is valid. Add iOS integration test. |
| Registration endpoints | Major changes needed | `AuthController.java:76-106`, `AuthService.swift:30-46` | Backend registers and creates cookie session. iOS calls token before customer registration, then token after registration. Decide final mobile registration response. |
| Profile endpoints | Major changes needed | backend `UserDtos.java:46-50`; iOS `ProfileModels.swift:24-28` | Profile stats field names differ. Update request omits avatarUrl/preferences. |
| Vehicle endpoints | Minor changes needed | backend `VehicleDtos.java`; iOS `VehicleModels.swift:27-34` | Core fields align, but iOS includes `SUV` and `RV`, which backend enum likely does not support. |
| Location/search endpoints | Major changes needed | backend `LocationDtos.java:67-80`; iOS `LocationModels.swift:166-188` | Search/geocode response shapes differ. This blocks primary customer search. |
| Reservation endpoints | Major changes needed | backend `ReservationDtos.java:41-62`; iOS `ReservationModels.swift:103-151` | Quote fields differ, dates are strings, idempotency is not persisted for retry. |
| Payment endpoints | Major changes needed | backend `PaymentDtos.java:13-46`; iOS `PaymentModels.swift:36-81`, `PaymentService.swift:20-22` | Payment methods, intent fields, and confirmation response differ. |
| Support endpoints | Major changes needed | backend `SupportTicketCategory.java:3-9`, `SupportTicketStatus.java:3-7`; iOS `SupportModels.swift:5-27` | Status/category raw values differ; ticket requester field expected by iOS is not in backend DTO. |
| Notification/device-token endpoints | Major changes needed | backend `NotificationController.java:36-43`, `DevicePlatform.java:3-6`; iOS `NotificationService.swift:21-29`, `NotificationModels.swift:15-36` | Wrong HTTP method for mark-read, wrong platform casing, wrong read field, wrong no-content handling. |
| Operator endpoints | Major changes needed | backend `OperatorDtos.java:21-39`; iOS `ProfileModels.swift:66-80` | Dashboard/resource health field names differ materially. |
| Admin endpoints | Major changes needed | backend `AdminDtos.java:15-43`; iOS `ProfileModels.swift:84-110` | Dashboard/user/audit field names differ materially. |
| Analytics endpoint | Major changes needed | backend `AnalyticsDtos.java:16-27`; iOS `Analytics.swift:70-93` | Backend expects `{ events: [...] }` with `event`; iOS sends single payload with `eventName`. Backend returns 202 empty body, iOS expects body. |
| Health/observability | Ready | `/api/health`, request ID filter, Actuator config | iOS does not yet expose diagnostics using request IDs. |
| Error envelope | Minor changes needed | backend `ApiErrorResponse`; iOS `APIError.swift:61-71` | iOS drops `code`, `requestId`, `timestamp`, and `path` from typed errors. |
| Validation errors | Major changes needed | backend returns 400; iOS maps validation only on 422 at `APIClient.swift:151-153` | Field validation errors will surface as unknown 400 instead of form errors. |
| Pagination | Ready | backend `ApiPage`, iOS `APIPage` | Shape aligns. Use `Int64` if totals may exceed `Int` assumptions later. |
| Date/time formats | Minor changes needed | backend uses `Instant`; iOS uses `Date` for some responses and `String` for some requests | Standardize on typed `Date` encode/decode and display in parking timezone. |
| Money/currency formats | Minor changes needed | backend uses cents/currency; iOS formats cents | Core approach aligns, but field names differ in payment/search. |
| Enums | Major changes needed | support, vehicle, device platform mismatches | Add generated enum fixtures or tolerate unknown values intentionally. |
| Idempotency | Major changes needed | backend body idempotency exists; iOS generates one per request init | Persist keys across retries and eventually support `X-Idempotency-Key`. |
| API versioning | Missing | unversioned controller mappings | Add `/api/v1` or version header before external mobile clients. |
| Map-bounds/geospatial search | Major changes needed | backend search accepts center/radius but no viewport contract; iOS has no MapKit UI | Add bounds/viewport support and availability-aware search. |
| Mobile retry behavior | Major changes needed | no iOS retry policy; backend no explicit mobile retry docs | Add retry policy for safe methods and idempotent mutation retries. |

## Endpoint Notes

### Auth

The backend now has a mobile token endpoint:

- `POST /api/auth/token`
- Request: `email`, `password`
- Response: `accessToken`, `expiresIn`, `tokenType`, `user`

This makes the iOS bearer-token direction plausible, but it is not production-ready without refresh/revocation and stricter backend defaults.

### No-Content Endpoints

The iOS API client must treat backend `204` as successful for:

- `POST /auth/logout`
- `POST /auth/password/reset-request`
- `POST /auth/password/reset`
- `DELETE /vehicles/{vehicleId}`
- `POST /notifications/{notificationId}/read`
- `POST /notifications/device-tokens`

It must also handle `202` accepted for analytics without a response body.

### OpenAPI Requirement

The current iOS code should not continue hand-guessing DTOs. The next backend/iOS sync should produce one mobile-supported OpenAPI source and use it for fixture generation or code generation.

