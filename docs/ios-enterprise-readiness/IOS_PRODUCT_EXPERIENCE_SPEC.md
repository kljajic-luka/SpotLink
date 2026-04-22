# iOS Product Experience Spec

Date: 2026-04-22

## Product Standard

SpotLink for iOS should feel like a professional native parking product, not a web app wrapped in a shell. The app should be fast, location-aware, resilient on mobile networks, accessible, privacy-conscious, and explicit about reservation/payment state.

The customer experience is the primary MVP surface. Operator, support, and admin surfaces can start narrower, but they must still use native iOS navigation and state handling.

## Target User Types

### CUSTOMER

Primary jobs:

- Find nearby or destination parking.
- Compare price, distance, availability, vehicle fit, EV charging, access type, and operator details.
- Reserve a parking resource for a time window.
- Pay or authorize payment.
- Receive access instructions and reservation notifications.
- Manage vehicles and preferences.
- Get support for payment, access, reservation, or location issues.

### OPERATOR

Primary jobs:

- View active locations and resources.
- See current and upcoming reservations.
- Detect resources needing attention.
- Create/update parking locations and resources when enabled.
- Respond to support escalations tied to their resources.

### SUPPORT

Primary jobs:

- Triage ticket queues.
- Review reservation, payment, customer, operator, and location context.
- Reply to tickets and escalate issues.
- Identify access failures, payment problems, and refund candidates.

### ADMIN

Primary jobs:

- Monitor marketplace health.
- Review users, operators, audit events, and support load.
- Investigate suspicious behavior.
- Control production launch operations and escalations.

## Main App Shell

Use a role-aware native shell:

- Authentication state gates the shell.
- After sign-in, select the default role workspace from the user's roles.
- Let multi-role users switch workspaces from Profile or a role switcher in the navigation title menu.
- Keep role-specific tabs stable. Do not dynamically reorder tabs after first load.
- Use native `TabView` for primary navigation and `NavigationStack` per tab.
- Use sheets for short task flows and full-screen covers for authentication, payment authorization, and blocking permission education.

Recommended customer tabs:

| Tab | Purpose |
| --- | --- |
| Search | Map/list search, filters, location details, start reservation. |
| Reservations | Upcoming, active, completed, cancelled reservations. |
| Vehicles | Vehicle profiles and compatibility details. |
| Inbox | Notifications and support entry points. |
| Profile | Account, preferences, role switch, legal, logout. |

Recommended operator tabs:

| Tab | Purpose |
| --- | --- |
| Dashboard | Occupancy, revenue estimate, today's reservations, alerts. |
| Inventory | Locations and parking resources. |
| Reservations | Current/upcoming reservations by resource. |
| Support | Operator-related tickets and access issues. |
| Profile | Operator account and settings. |

Recommended support tabs:

| Tab | Purpose |
| --- | --- |
| Queue | Open tickets and priority filters. |
| Tickets | Search and ticket history. |
| Users | Context lookup, read-only for MVP unless scoped. |
| Alerts | Access, payment, and reservation exceptions. |
| Profile | Support preferences and logout. |

Recommended admin tabs:

| Tab | Purpose |
| --- | --- |
| Dashboard | Marketplace KPIs and launch health. |
| Users | User and role overview. |
| Operators | Operator health and inventory overview. |
| Audit | Audit event search and inspection. |
| Settings | Operational configuration and profile. |

## Navigation Model

Use native navigation patterns:

- Push detail views from list/map results.
- Use bottom sheets for search filters, reservation quote summary, map pin previews, and quick actions.
- Use confirmation dialogs for destructive actions such as cancelling a reservation or deleting a vehicle.
- Use pull-to-refresh for lists where freshness matters.
- Use swipe actions sparingly for low-risk actions such as marking notifications read.
- Avoid hamburger navigation for primary workflows.
- Avoid web-style breadcrumbs.

Deep links should support:

- Reservation detail.
- Payment return/confirmation.
- Support ticket detail.
- Notification-related entity.
- Location/resource detail.

## Onboarding and Auth Flow

First launch:

1. Show a concise native welcome/auth screen with sign in, create customer account, and create operator account.
2. Request location permission only when the user enters Search or taps "near me". Do not request location on first launch.
3. Explain push notification value after the first confirmed reservation or when enabling alerts, not during first launch.

Auth screens:

- Sign in with email/password for foundation parity.
- Customer registration with first name, last name, email, optional phone, password, and terms acceptance.
- Operator registration with company name, operator type, operator agreement, and customer role retained when backend returns both roles.
- Password reset request and completion.
- Session expired state with a clear sign-in action.

Required auth states:

- Initial unknown session.
- Signed out.
- Signing in.
- Signed in with loaded profile.
- Session expired.
- Account suspended/deleted.
- Offline with cached, non-sensitive shell state.

Native iOS expectations:

- Use secure text entry and password manager/autofill.
- Use `ASAuthorization` only if Apple sign-in is introduced later and backend supports it.
- Never store passwords.
- Use Keychain for mobile tokens or session secrets if the backend adds a token model.

## Search and Map Experience

Customer Search should be the highest-quality native surface.

Core layout:

- Map-first view with a synchronized results drawer.
- Search field for address, venue, or neighborhood.
- "Near me" action after location permission is granted.
- List mode toggle for dense comparison.
- Filter sheet for time window, vehicle, resource type, EV charging, price, distance, access type, and instant reserve.
- Sort by recommended, distance, price, and availability where backend supports it.

Map behavior:

- Use MapKit.
- Show parking locations as pins or clusters.
- Selecting a pin opens a compact preview with name, starting price, distance, available count, and primary action.
- Search this area action appears after significant map movement.
- Show permission-denied state with manual search still available.
- When location is unavailable, default to last searched area or a configured market center.

API expectations:

- `GET /locations/search` currently supports query, latitude, longitude, radius, resource types, EV required, startsAt, endsAt, page, and size.
- iOS should send ISO-8601 UTC timestamps for `startsAt` and `endsAt`.
- Backend should eventually apply radius, availability, sorting, and ranking server-side.

## Parking Location and Resource Detail

Location detail should include:

- Name, address, map position, distance, timezone, and operator identity where safe.
- Public notes.
- Access type, displayed as customer-friendly copy.
- Available resources for the selected time window.
- Starting price and detailed price terms.
- Vehicle fit information.
- EV charging availability.
- Cancellation/support policy link.
- Report issue or contact support action.

Resource detail should include:

- Resource type, label if customer-safe, floor/bay if useful before arrival.
- Max height, max length, vehicle types, EV-only flag.
- Hourly/daily rates.
- Instant reserve status.
- Access instruction availability rules. Do not reveal sensitive access instructions before payment confirmation.

Do not expose:

- Gate codes before confirmed payment.
- Internal operator notes.
- Exact bay labels when the operator marks them private.

## Reservation Flow

Recommended flow:

1. Select location/resource.
2. Select start and end time using native date/time controls.
3. Select vehicle or continue without vehicle when allowed.
4. Request quote.
5. Review quote: time window, location, resource type, vehicle fit, subtotal, fees, discounts, total, currency, expiration.
6. Confirm reservation with an idempotency key.
7. Transition to payment.
8. Show confirmed reservation with access instructions only when backend says `accessInstructionsVisible`.

Reservation states:

- Draft
- Quoting
- Quote ready
- Quote expired
- Creating
- Pending payment
- Confirmed
- Active
- Completed
- Cancelled
- Expired
- Disputed
- Failed/retryable

Product rules:

- Generate one idempotency key per create attempt and persist it until the server returns a terminal response.
- Never create a second reservation because the user double-tapped or the app retried after a timeout.
- Show clear overlap/unavailable errors.
- Display all reservation times in the parking location timezone, with a concise local timezone indicator.
- Store UTC instants internally.

## Payment Flow

Foundation flow:

- List payment methods.
- Create payment intent for reservation.
- Confirm payment intent.
- Handle `AUTHORIZED`, `REQUIRES_ACTION`, `FAILED`, `CANCELLED`, `CAPTURED`, and `REFUNDED`.

Native MVP expectations:

- Use mock payment only in debug/internal testing.
- Use a production PSP SDK or redirect flow for TestFlight external and App Store if payments are in scope.
- Use Apple Pay if the business and PSP support it.
- Use `ASWebAuthenticationSession` or `SFSafariViewController` for external payment authentication where required.
- Return from payment deep link to reservation detail and refresh from backend.
- Never collect raw PAN/card data unless the app is fully designed for PCI scope through an approved PSP SDK.

Payment error states:

- Card declined.
- Additional authentication required.
- Payment timeout.
- Payment created but confirmation unknown.
- Reservation expired before payment.
- Duplicate payment attempt.

## Vehicle Management

Customer vehicle screens should support:

- List vehicles.
- Add vehicle.
- Edit vehicle.
- Delete vehicle with confirmation.
- Optional default vehicle once backend supports it.
- EV capability.
- Type, nickname, make, model, color, license plate, height, and length.
- Compatibility warnings during reservation.

Privacy:

- Treat license plate as personal data.
- Do not show license plate in notifications.
- Avoid caching license plate beyond the encrypted local cache if caching is needed.

## Support Flow

Customer support should be available from:

- Reservation detail.
- Payment error.
- Location detail.
- Profile.
- Inbox.

Ticket creation should prefill:

- Category.
- Reservation ID when started from a reservation.
- Location ID when started from a location.
- Payment context when started from payment failure, if backend exposes it.

Ticket detail:

- Chronological message thread.
- Attachments once backend supports them.
- Clear status.
- Operator/support response identity appropriate to role.
- Pull-to-refresh and optimistic send state.

Support and admin workspaces need queues before production support operations are real.

## Notifications Flow

Notification types should map to product destinations:

- Reservation confirmed: reservation detail.
- Reservation reminder: active/upcoming reservation.
- Payment required or failed: payment flow.
- Support reply: ticket detail.
- Operator alert: resource/reservation detail.
- Admin alert: dashboard/audit detail.

Native behavior:

- Register APNs token only after the user is signed in and has consented/enabled notifications.
- Send token with platform `IOS`.
- Refresh token registration on app launch and APNs token change.
- Unregister or deactivate device token on logout when backend supports it.
- Respect notification preferences.
- Keep push payloads privacy-preserving. Avoid gate codes, license plates, full addresses, or payment details.

## Profile and Preferences

Profile should include:

- Identity fields.
- Avatar URL only if image storage is supported.
- Phone.
- Bio only if product needs it; otherwise hide until profile richness matters.
- Preferences: locale, marketing opt-in, reservation alerts, payment alerts, support alerts.
- Legal links: privacy policy, terms, licenses.
- Account deletion request path.
- Logout.
- Role switcher for multi-role accounts.

Preference updates:

- Use optimistic UI only when rollback is straightforward.
- Re-fetch profile after significant account changes.

## Operator Views

MVP operator workspace:

- Dashboard summary: active locations, active resources, reservations today, occupancy estimate, pending support tickets, revenue estimate.
- Resource health list: online/inactive, current reservation, next reservation, attention required.
- Location/resource list and detail.
- Create/update location/resource if backend authorization and validation are complete.
- Reservation list by date/resource once backend supports it.

Operator UX should be dense and operational. It should prioritize current status, next action, and exceptions over decorative UI.

## Admin Views

MVP admin workspace:

- Dashboard summary.
- User list and user detail.
- Operator list/detail once backend supports it.
- Audit event list/detail.
- Open support metrics.
- Health/monitoring links if exposed safely.

Admin actions should be limited until backend authorization, audit logging, and confirmation flows are complete.

## Offline and Degraded States

Required states:

- No network.
- Slow network.
- Server unavailable.
- Session expired while offline.
- Stale cached data.
- Location permission denied.
- Location temporarily unavailable.
- Push permission denied.
- Payment provider unavailable.
- Search service degraded.

Behavior:

- Cache low-risk read data such as recent searches, last visible reservations, and non-sensitive profile preferences.
- Do not allow new reservation creation while offline.
- Do not mark payment successful without backend confirmation.
- Surface stale data labels when data is cached.
- Queue analytics only with consent and size limits.

## Loading, Empty, and Error States

Every screen must define:

- Initial loading.
- Pull-to-refresh loading.
- Empty state.
- Recoverable error with retry.
- Non-recoverable error with next step.
- Unauthorized/session expired.
- Permission denied where applicable.

Examples:

- Search empty: no parking matches filters; offer to clear filters or change area.
- Reservations empty: no upcoming reservations; primary action opens Search.
- Vehicles empty: add vehicle.
- Notifications empty: no notifications.
- Operator resources empty: create first location/resource or contact admin depending on permissions.
- Support queue empty: no open tickets.

## Accessibility Standards

Minimum standard:

- VoiceOver labels for all interactive controls.
- VoiceOver order matches visual order.
- Buttons have 44x44 point minimum hit targets.
- Do not rely on color alone for status.
- Support Reduce Motion.
- Support Increase Contrast.
- Use semantic headings where SwiftUI supports them.
- All map pins must have accessible alternatives in list mode.
- Payment and reservation errors must be announced.
- Forms must expose validation messages to VoiceOver.

Target:

- WCAG 2.2 AA aligned behavior for color contrast, scalable text, focus, and error identification.

## Dynamic Type Expectations

Support Dynamic Type through at least accessibility sizes:

- Text must wrap instead of clipping.
- Primary action buttons must support multi-line labels where needed.
- Reservation cards must preserve status and time information at large sizes.
- Map/list toggle controls must remain tappable.
- Dense operator/admin screens may provide adaptive layouts, but cannot become unreadable.

Avoid:

- Fixed-height cards that clip content.
- Viewport-scaled font sizes.
- Negative letter spacing.

## Dark Mode Expectations

Dark mode is required before external TestFlight:

- Use semantic colors.
- Map overlays and pins must remain visible in dark map style.
- Status colors must meet contrast requirements.
- Payment, reservation, and error states must be readable.
- App icon and launch screen must look intentional in dark mode.

## Native iOS Interaction Standards

Use:

- Native sheet detents for filters and quick previews.
- Native date/time pickers with validation.
- Native swipe-to-dismiss only for non-critical sheets.
- Haptics for successful reservation confirmation and destructive confirmation, used sparingly.
- Pull-to-refresh for lists.
- Search suggestions with native search field behavior.
- Share sheet for reservation details only if privacy-safe.

Avoid:

- Web-style modals for every step.
- Breadcrumbs.
- Tiny custom controls that ignore iOS hit targets.
- Toast-only error handling for critical failures.

## SwiftUI Is Enough For

- App shell and tab navigation.
- Authentication forms.
- Lists and cards.
- Reservation flow.
- Vehicle management.
- Profile/preferences.
- Support ticket list/detail.
- Notifications.
- Operator/admin dashboard surfaces.
- Most loading/empty/error states.
- Basic MapKit through SwiftUI `Map` if clustering needs are modest.

## UIKit May Be Needed For

- Advanced MapKit clustering or annotation customization beyond SwiftUI `Map`.
- PSP SDK surfaces that require `UIViewController`.
- Apple Pay authorization controllers.
- `SFSafariViewController` or web authentication handoff wrappers.
- Photo/document pickers for future support attachments.
- Fine-grained keyboard/input accessory behavior.
- Low-level scroll coordination for complex map + drawer interactions.

