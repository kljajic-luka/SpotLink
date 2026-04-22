# SpotLink Frontend API Contract (ocekivanja frontend foundation sloja)

Datum: 2026-04-22

## Svrha

Ovaj dokument opisuje API ocekivanja koja frontend foundation trenutno koristi.
Ovo nije backend implementacioni dokument i ne uvodi backend obaveze van postojecih frontend ugovora.

## Opsta pravila ugovora

- Frontend salje zahteve preko `ApiClient` servisa na `baseApiUrl`.
- Zahtevi koriste cookie sesiju (`withCredentials: true`).
- Za mutacione metode (`POST`, `PUT`, `PATCH`, `DELETE`) frontend pokusava da doda XSRF header `X-XSRF-TOKEN` iz cookie vrednosti `XSRF-TOKEN`.
- Pagination obrazac koristi `page` i `size` query parametre gde je navedeno.
- Idempotency se ocekuje kroz DTO polje `idempotencyKey` na rezervacijama i payment intent kreiranju.

## Auth

| HTTP | Putanja | Request DTO | Response DTO | Auth | Napomena |
| --- | --- | --- | --- | --- | --- |
| POST | `/auth/login` | `LoginRequest` | `AuthResponse` -> `user: UserProfile` | Ne (javno) | Cookie sesija se postavlja na backend strani. |
| POST | `/auth/register/customer` | `RegisterCustomerRequest` | `AuthResponse` -> `user: UserProfile` | Ne (javno) | Registracija customer korisnika. |
| POST | `/auth/register/operator` | `RegisterOperatorRequest` | `AuthResponse` -> `user: UserProfile` | Ne (javno) | Registracija operator korisnika. |
| POST | `/auth/password/reset-request` | `PasswordResetRequest` | `void` | Ne (javno) | Pokretanje reset toka. |
| POST | `/auth/password/reset` | `CompletePasswordResetRequest` | `void` | Ne (javno) | Zavrsni korak reset toka. |
| POST | `/auth/logout` | `{}` | `void` | Da | Frontend gasi lokalnu sesiju i kada backend vrati gresku. |
| GET | `/auth/me` | N/A | `UserProfile` | Da | Inicijalizacija sesije pri startu aplikacije. |

## UserProfile

| HTTP | Putanja | Request DTO | Response DTO | Auth | Napomena |
| --- | --- | --- | --- | --- | --- |
| GET | `/users/me/profile` | N/A | `UserProfileDetails` | Da | Profil ulogovanog korisnika. |
| GET | `/users/{userId}/profile` | N/A | `UserProfileDetails` | Da | `userId` je URL-encoded u frontend-u. |
| PATCH | `/users/me/profile` | `UpdateProfileRequest` | `UserProfileDetails` | Da | Parcijalna izmena profila. |

## Vehicles

| HTTP | Putanja | Request DTO | Response DTO | Auth | Napomena |
| --- | --- | --- | --- | --- | --- |
| GET | `/vehicles/me` | N/A | `VehicleProfile[]` | Da | Lista customer vehicle profile zapisa. |
| POST | `/vehicles` | `VehicleUpsertRequest` | `VehicleProfile` | Da | Kreiranje vehicle profile zapisa. |
| PUT | `/vehicles/{vehicleId}` | `VehicleUpsertRequest` | `VehicleProfile` | Da | Potpuna izmena vehicle profile zapisa. |
| DELETE | `/vehicles/{vehicleId}` | N/A | `void` | Da | Brisanje vehicle profile zapisa. |

## Locations

| HTTP | Putanja | Request DTO | Response DTO | Auth | Napomena |
| --- | --- | --- | --- | --- | --- |
| GET | `/locations/search` | `LocationSearchFilters` (query params) | `ApiPage<LocationSearchResult>` | Da | Pagination preko `page` i `size` kad su prosledjeni. |
| GET | `/locations/geocode` | `{ query: string }` (query params) | `GeocodeSuggestion[]` | Da | Predlozi adrese i koordinata. |
| GET | `/locations/{locationId}` | N/A | `ParkingLocation` | Da | Dohvatanje parking location entiteta. |
| GET | `/locations/{locationId}/resources` | N/A | `ParkingResource[]` | Da | Dohvatanje parking resource entiteta po lokaciji. |

## Reservations

| HTTP | Putanja | Request DTO | Response DTO | Auth | Napomena |
| --- | --- | --- | --- | --- | --- |
| GET | `/reservations/me` | `{ page?: number, size?: number }` (query params) | `ApiPage<Reservation>` | Da | Podrazumevano `page=0`, `size=20` u frontend servisu. |
| GET | `/reservations/{reservationId}` | N/A | `Reservation` | Da | `reservationId` je URL-encoded. |
| POST | `/reservations/quote` | `ReservationQuoteRequest` | `ReservationQuote` | Da | Quote endpoint bez idempotency polja. |
| POST | `/reservations` | `CreateReservationRequest` | `Reservation` | Da | `idempotencyKey` je obavezan deo DTO ugovora. |
| POST | `/reservations/{reservationId}/cancel` | `{ reason?: string }` | `Reservation` | Da | Otkazivanje reservation zapisa. |

## Payments

| HTTP | Putanja | Request DTO | Response DTO | Auth | Napomena |
| --- | --- | --- | --- | --- | --- |
| GET | `/payments/methods` | N/A | `PaymentMethod[]` | Da | Lista payment metoda korisnika. |
| POST | `/payments/intents` | `CreatePaymentIntentRequest` | `PaymentIntent` | Da | `idempotencyKey` je obavezan deo DTO ugovora. |
| POST | `/payments/intents/{paymentIntentId}/confirm` | `{}` | `PaymentProviderResult` | Da | Potvrda payment intent zapisa. |

## Support

| HTTP | Putanja | Request DTO | Response DTO | Auth | Napomena |
| --- | --- | --- | --- | --- | --- |
| GET | `/support/tickets` | `{ page?: number, size?: number }` (query params) | `ApiPage<SupportTicket>` | Da | Podrazumevano `page=0`, `size=20` u frontend servisu. |
| POST | `/support/tickets` | `CreateSupportTicketRequest` | `SupportTicket` | Da | Kreiranje support ticketa. |
| GET | `/support/tickets/{ticketId}/messages` | N/A | `SupportMessage[]` | Da | Thread poruka za ticket. |
| POST | `/support/tickets/{ticketId}/messages` | `{ body: string }` | `SupportMessage` | Da | Slanje poruke u thread-u. |

## Notifications

| HTTP | Putanja | Request DTO | Response DTO | Auth | Napomena |
| --- | --- | --- | --- | --- | --- |
| GET | `/notifications` | `{ page?: number, size?: number }` (query params) | `ApiPage<NotificationItem>` | Da | Podrazumevano `page=0`, `size=20` u frontend servisu. |
| GET | `/notifications/unread-count` | N/A | `UnreadNotificationCount` | Da | Brzi brojac neprocitanih notifikacija. |
| POST | `/notifications/{notificationId}/read` | `{}` | `void` | Da | Obelezavanje notifikacije kao procitane. |
| POST | `/notifications/device-tokens` | `RegisterDeviceTokenRequest` | `void` | Da | Registracija push tokena uredjaja. |

## Operator

| HTTP | Putanja | Request DTO | Response DTO | Auth | Napomena |
| --- | --- | --- | --- | --- | --- |
| GET | `/operator/me` | N/A | `OperatorAccount` | Da (ocekivano OPERATOR) | Podaci o trenutnom operator nalogu. |
| GET | `/operator/dashboard/summary` | N/A | `OperatorDashboardSummary` | Da (ocekivano OPERATOR) | KPI pregled za operator dashboard. |
| GET | `/operator/resources/health` | N/A | `OperatorResourceHealth[]` | Da (ocekivano OPERATOR) | Stanje parking resource jedinica. |

## Admin

| HTTP | Putanja | Request DTO | Response DTO | Auth | Napomena |
| --- | --- | --- | --- | --- | --- |
| GET | `/admin/dashboard/summary` | N/A | `AdminDashboardSummary` | Da (ocekivano ADMIN) | KPI pregled za admin dashboard. |
| GET | `/admin/users` | `{ page?: number, size?: number }` (query params) | `ApiPage<AdminUserSummary>` | Da (ocekivano ADMIN) | Podrazumevano `page=0`, `size=25` u frontend servisu. |
| GET | `/admin/audit-events` | `{ page?: number, size?: number }` (query params) | `ApiPage<AdminAuditEvent>` | Da (ocekivano ADMIN) | Podrazumevano `page=0`, `size=25` u frontend servisu. |

## Analytics

| HTTP | Putanja | Request DTO | Response DTO | Auth | Napomena |
| --- | --- | --- | --- | --- | --- |
| POST | `/analytics/events` | `{ events: AnalyticsEvent[] }` | `void` | Cookie sesija po potrebi backend-a | Frontend pokusava `navigator.sendBeacon`, fallback je `fetch` sa `credentials: include` i `keepalive: true`. |

## Napomene za backend uskladjivanje

- Frontend ne menja endpoint putanje iz ovog dokumenta bez jasnog razloga.
- DTO nazivi i polja su preuzeti iz postojeceg foundation sloja i predstavljaju trenutni ugovor.
- Za role-level autorizaciju (CUSTOMER/OPERATOR/ADMIN/SUPPORT) frontend trenutno pretpostavlja backend enforcement.
- Retry logika u frontend-u je ukljucena samo za bezbedne HTTP metode i iskljucena je za auth/payment/reservation putanje.
