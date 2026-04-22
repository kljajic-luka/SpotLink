# Swift DTO Alignment Guide

Date: 2026-04-22

## Goal

This guide tells iOS agents how to name and decode Swift DTOs from the current SpotLink backend contract. It should be used with `SPOTLINK_MOBILE_API_CONTRACT.md`, `openapi-mobile-v1.yaml`, and the JSON fixtures in `json-fixtures/`.

Do not copy these snippets directly into `apps/ios` without adapting them to the app architecture. They are contract examples.

## Core Codable Strategy

Use:

```swift
let decoder = JSONDecoder()
decoder.dateDecodingStrategy = .iso8601
decoder.keyDecodingStrategy = .useDefaultKeys

let encoder = JSONEncoder()
encoder.dateEncodingStrategy = .iso8601
encoder.keyEncodingStrategy = .useDefaultKeys
```

Backend field names are already camelCase. Do not use `.convertFromSnakeCase`.

## UUID Handling

Use `UUID` for all backend UUID fields unless there is a strong UI reason to keep a string.

Recommended:

```swift
public let id: UUID
public let reservationId: UUID
```

Avoid:

```swift
public let id: String
```

String IDs hide invalid UUID bugs and make test fixtures less useful.

## Date Handling

Use `Date` for all backend `Instant` fields:

- `createdAt`
- `updatedAt`
- `startsAt`
- `endsAt`
- `expiresAt`
- `nextReservationAt`
- `timestamp`

When creating reservation quote/create requests, encode `Date` as ISO-8601 UTC. Display reservation windows using the reservation or location `timezone`.

## Money Handling

Backend amounts are integer minor units:

- `subtotalCents`
- `feesCents`
- `discountCents`
- `totalAmountCents`
- `amountCents`
- `grossRevenueCents`
- `grossMarketplaceVolumeCents`

Use `Int64` in Swift for money values:

```swift
public let totalAmountCents: Int64
public let currency: String
```

Format with `NumberFormatter` or `FormatStyle.Currency` by converting minor units only at the display boundary. Do not store money as `Double`.

## Pagination

Use a generic page:

```swift
public struct ApiPage<Element: Decodable & Sendable>: Decodable, Sendable {
    public let content: [Element]
    public let totalElements: Int64
    public let totalPages: Int
    public let page: Int
    public let size: Int

    public var hasMore: Bool { page + 1 < totalPages }
}
```

## Error Envelope

Decode the complete backend error:

```swift
public struct ApiErrorEnvelope: Decodable, Sendable {
    public let status: Int
    public let code: String
    public let message: String
    public let requestId: String?
    public let details: [String: String]?
    public let timestamp: Date
    public let path: String
}
```

Validation errors are HTTP `400` with `code == "VALIDATION_ERROR"` and field errors in `details`.

Do not treat only HTTP `422` as validation.

## Optional Versus Required Fields

Backend omits null fields because Jackson is configured with `spring.jackson.default-property-inclusion=non_null`. Swift optional fields must be optional and not require explicit null.

Examples:

```swift
public let phone: String?
public let avatarUrl: String?
public let publicNotes: String?
public let dailyRateCents: Int64?
public let redirectUrl: URL?
```

Required fields must be non-optional and tested through fixtures.

## Enum Raw Values

Use exact backend raw values:

```swift
public enum UserRole: String, Codable, Sendable {
    case customer = "CUSTOMER"
    case operatorRole = "OPERATOR"
    case support = "SUPPORT"
    case admin = "ADMIN"
}
```

Important enum corrections for current iOS code:

- `VehicleType` must not include `SUV` or `RV` unless backend adds them.
- `DevicePlatform` must send `"IOS"`, not `"iOS"`.
- Support categories are `RESERVATION`, `PAYMENT`, `LOCATION_ACCESS`, `SAFETY`, `ACCOUNT`, `OTHER`.
- Support statuses are `OPEN`, `WAITING_ON_CUSTOMER`, `WAITING_ON_OPERATOR`, `RESOLVED`.

## Backend DTO to Swift DTO Mapping

| Backend DTO | Recommended Swift DTO |
| --- | --- |
| `AuthDtos.AuthResponse` | `AuthResponseDTO` |
| `AuthDtos.MobileTokenResponse` | `AuthSessionResponseDTO` |
| `UserDtos.UserProfile` | `UserProfileDTO` |
| `UserDtos.UserProfileDetails` | `UserProfileDetailsDTO` |
| `VehicleDtos.VehicleProfileDto` | `VehicleProfileDTO` |
| `LocationDtos.ParkingLocationDto` | `ParkingLocationDTO` |
| `LocationDtos.ParkingResourceDto` | `ParkingResourceDTO` |
| `LocationDtos.LocationSearchResult` | `LocationSearchResultDTO` |
| `LocationDtos.GeocodeSuggestion` | `GeocodeSuggestionDTO` |
| `ReservationDtos.ReservationDto` | `ReservationDTO` |
| `ReservationDtos.ReservationQuote` | `ReservationQuoteDTO` |
| `PaymentDtos.PaymentIntentDto` | `PaymentIntentDTO` |
| `PaymentDtos.PaymentProviderResult` | `PaymentProviderResultDTO` |
| `SupportDtos.SupportTicketDto` | `SupportTicketDTO` |
| `SupportDtos.SupportMessageDto` | `SupportMessageDTO` |
| `NotificationDtos.NotificationItem` | `NotificationItemDTO` |
| `OperatorDtos.OperatorDashboardSummary` | `OperatorDashboardSummaryDTO` |
| `AdminDtos.AdminDashboardSummary` | `AdminDashboardSummaryDTO` |

## Naming Differences to Avoid

Avoid these known incorrect names:

- `minHourlyRateCents`; backend uses `startingPriceCents`.
- `durationHours`; backend quote does not return it.
- `readFlag`; backend DTO returns `read`.
- `expiryMonth`/`expiryYear`; backend uses `expMonth`/`expYear`.
- `displayName` for payment method; backend does not return it.
- `openSupportTickets` in profile stats; backend uses `supportTickets`.
- `totalResources`; backend operator summary uses `activeResources`.
- `activeReservations` in operator summary; backend uses `reservationsToday`.
- `totalUsers`/`totalOperators`; backend admin summary uses `users`/`operators`.

## Important DTO Examples

### AuthSession

```swift
public struct AuthSessionResponseDTO: Decodable, Sendable {
    public let accessToken: String
    public let refreshToken: String
    public let tokenType: String
    public let expiresIn: Int64
    public let expiresInSeconds: Int64
    public let refreshExpiresInSeconds: Int64
    public let issuedAt: Date
    public let expiresAt: Date
    public let refreshExpiresAt: Date
    public let user: UserProfileDTO
    public let roles: [UserRole]
}
```

Use `expiresInSeconds` for new code. `expiresIn` is present only as a backward-compatible alias.

### UserProfile

```swift
public struct UserProfileDTO: Codable, Identifiable, Sendable {
    public let id: UUID
    public let email: String
    public let firstName: String
    public let lastName: String
    public let phone: String?
    public let avatarUrl: URL?
    public let bio: String?
    public let roles: [UserRole]
    public let operatorId: UUID?
    public let registrationStatus: RegistrationStatus
    public let createdAt: Date
}
```

### VehicleProfile

```swift
public struct VehicleProfileDTO: Codable, Identifiable, Sendable {
    public let id: UUID
    public let userId: UUID
    public let type: VehicleType
    public let nickname: String?
    public let make: String?
    public let model: String?
    public let color: String?
    public let licensePlate: String?
    public let heightMeters: Decimal?
    public let lengthMeters: Decimal?
    public let evCapable: Bool
    public let verificationStatus: VehicleVerificationStatus
    public let createdAt: Date
    public let updatedAt: Date
}
```

### ParkingLocation

```swift
public struct ParkingLocationDTO: Codable, Identifiable, Sendable {
    public let id: UUID
    public let operatorId: UUID
    public let name: String
    public let address: AddressDTO
    public let coordinates: GeoCoordinatesDTO
    public let timezone: String
    public let accessType: ParkingAccessType
    public let publicNotes: String?
    public let active: Bool
}
```

### ParkingResource

```swift
public struct ParkingResourceDTO: Codable, Identifiable, Sendable {
    public let id: UUID
    public let locationId: UUID
    public let type: ParkingResourceType
    public let label: String
    public let floor: String?
    public let bayNumber: String?
    public let fitRule: VehicleFitRuleDTO?
    public let hourlyRateCents: Int64
    public let dailyRateCents: Int64?
    public let currency: String
    public let instantReserve: Bool
    public let active: Bool
}
```

### Reservation

```swift
public struct ReservationDTO: Codable, Identifiable, Sendable {
    public let id: UUID
    public let customerId: UUID
    public let operatorId: UUID
    public let locationId: UUID
    public let resourceId: UUID
    public let vehicleId: UUID?
    public let startsAt: Date
    public let endsAt: Date
    public let timezone: String
    public let status: ReservationStatus
    public let totalAmountCents: Int64
    public let currency: String
    public let accessInstructionsVisible: Bool
    public let createdAt: Date
    public let updatedAt: Date
}
```

### PaymentIntent

```swift
public struct PaymentIntentDTO: Codable, Identifiable, Sendable {
    public let id: UUID
    public let reservationId: UUID
    public let amountCents: Int64
    public let currency: String
    public let status: PaymentStatus
    public let redirectUrl: URL?
    public let clientSecret: String?
}
```

### SupportTicket

```swift
public struct SupportTicketDTO: Codable, Identifiable, Sendable {
    public let id: UUID
    public let category: SupportTicketCategory
    public let status: SupportTicketStatus
    public let subject: String
    public let reservationId: UUID?
    public let locationId: UUID?
    public let createdAt: Date
    public let updatedAt: Date
}
```

### NotificationItem

```swift
public struct NotificationItemDTO: Codable, Identifiable, Sendable {
    public let id: UUID
    public let type: NotificationType
    public let title: String
    public let body: String
    public let relatedEntityId: UUID?
    public let read: Bool
    public let createdAt: Date
}
```

### ApiError

```swift
public enum ApiClientError: Error, Sendable {
    case unauthorized(ApiErrorEnvelope?)
    case forbidden(ApiErrorEnvelope?)
    case validation(ApiErrorEnvelope)
    case conflict(ApiErrorEnvelope)
    case notFound(ApiErrorEnvelope)
    case server(ApiErrorEnvelope?)
    case offline
    case decoding(Error)
    case unexpectedStatus(Int, ApiErrorEnvelope?)
}
```

## Contract Test Rule

Every Swift DTO above should decode one or more JSON files from `docs/mobile-api-contract/json-fixtures`. iOS agents should not proceed with feature UI work until these fixture tests pass.
