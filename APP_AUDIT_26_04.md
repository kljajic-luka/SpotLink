SECTION 1 — Executive Summary
This is a credible foundation, not a pilot-ready product.

The strongest assets are the Spring Boot backend foundation, the native iOS client, and a surprisingly solid baseline for auth, API error handling, correlation IDs, and test coverage. The weakest areas are the actual reservation state machine, payment authority model, operator/admin tooling, and the fact that several “foundation” modules exist without being wired into real business flows. The web app is mostly a typed shell, not a real product surface. There is no active Swing application in this repository, so any strategy that assumes Swing is part of the current foundation is already working from a false map.

My blunt recommendation is: keep the Java backend, keep the iOS app, repurpose Angular into operator/admin tooling, and stop thinking in terms of a generic multi-client “platform” until the Belgrade pilot workflow is made reliable. Do not rewrite the whole stack. Do refactor the booking, inventory, payment, audit, and operational layers before trusting this in front of paying drivers and live operators.

The single biggest technical risk is not Java. It is that booking and payment are modeled as thin synchronous flows instead of as a resilient reservation machine with explicit inventory holds, webhook-authoritative payment events, reconciliation, operator interventions, and auditability.

The single biggest product/architecture mismatch is that the codebase still behaves like a generic marketplace foundation, while the actual MVP needs one ruthless wedge: dependable iOS-first booking for a narrow operator set in Belgrade, with simple but real operator/admin tooling. The defaults still say generic marketplace and even USD in places such as application.properties:51, which is not aligned with a Serbia-first launch.

SECTION 2 — Current Architecture Map
Area	What Exists	Verdict	Evidence
Backend API	Spring Boot monolith with auth, locations, reservations, payments, support, notifications, admin, operator, Flyway, JPA	Good foundation	pom.xml, ReservationService.java:102, PaymentService.java:46
Persistence	Flyway schema for users, vehicles, operators, locations, resources, reservations, payment intents, support, notifications, audit, analytics	Broad but shallowly operationalized	V1__spotlink_backend_foundation.sql, V3__pilot_partner_capacity_availability.sql, V4__reservation_payment_hold_expiry.sql
Security/Auth	Session-cookie auth for web, JWT/refresh for mobile, CSRF cookie flow, role-based route protection	Stronger than average for an MVP foundation	SecurityConfig.java:57, SecurityConfig.java:74, SecurityConfig.java:105
Error/Request Plumbing	Correlation IDs and consistent API error envelope	Good	RequestCorrelationFilter.java, GlobalExceptionHandler.java
Booking Core	Quote and create exist, availability is checked server-side, holds piggyback on reservation rows	Useful, but not enough for pilot trust	ReservationService.java:102, ReservationRepository.java:44
Payment Core	Payment intents exist, provider abstraction exists, confirmation can confirm reservation	Not production-grade	PaymentService.java:54, PaymentProvider.java:7
Angular Web	One actual route and one showcase dashboard, plus a lot of service/model scaffolding	Foundation only, not a real product client	app.routes.ts:6, foundation-dashboard.component.ts:29
iOS App	Real SwiftUI shell, map search, bookings, reservations, support, vehicles, profile	Strongest end-user surface in repo	SearchMapView.swift:13, ReservationBookingFlowView.swift:264, ReservationsView.swift:65
Operator/Admin	Read-only-ish dashboards and some CRUD for operator-owned locations/resources	Too thin for live operations	OperatorController.java:21, AdminController.java:17
Notifications	Notification storage, device registration, delivery abstraction	Present but not wired into booking/payment flows	NotificationService.java
Audit	Audit table and AuditService exist	Dead infrastructure today	AuditService.java
Deployment/Infra	CI exists; no first-party Docker, compose, Terraform, Bicep, Helm, or Azure artifacts found	Manual deployment story	ci.yml:70, ci.yml:74
Desktop/Swing	No active Swing code found in repo	No current role	Repo-wide inspection
Directory-level reality in plain terms is simple: backend is real, iOS is real, Angular is mostly preparatory, deployment is underbaked, and Swing is absent.

SECTION 3 — Stack Fit Assessment
Is Java a good backend choice for this product?
Yes. Spring Boot is a valid and efficient choice for an iOS-first parking marketplace MVP. Nothing in the code suggests Java is the reason this product is not ready. The current monolith is a better fit than an early microservice split because the business problem still needs sharper domain boundaries, not more distributed infrastructure.

Is Angular the right web surface?
Not for the consumer MVP. Yes for operator/admin.

The current Angular app is not a booking client in any meaningful sense. It has one real route, one foundation dashboard, and lots of typed service wrappers, as shown in app.routes.ts:6 and foundation-dashboard.component.ts:82. That makes Angular a poor primary consumer surface today, but a perfectly sensible place to build operator and internal admin tooling quickly.

Does Swing have any valid role?
Not based on this repo. There is no active Swing code here. If there is an external Swing app outside this repository, treat it as legacy and only keep it if a real operator workflow already depends on it. Do not invest new product energy into a desktop admin surface unless you have hard proof that operators need it more than a browser.

Is the current structure appropriate for an iOS-first reservation MVP?
Partially.

The structure is appropriate only if you narrow scope hard:

Java monolith as system of record.
iOS as the main customer app.
Angular as operator/admin console.
No meaningful desktop investment.
One clean REST contract optimized for mobile reliability, not generic platform breadth.
Is the backend API clean enough for SwiftUI?
Mostly yes for a first version. It has consistent JSON, clear DTOs, correlation IDs, and predictable errors. The bigger issue is not transport cleanliness. It is domain incompleteness: no real payment webhook model, no operator booking actions, no explicit check-in/no-show flows, and no authoritative booking timeline.

Would Angular slow or help future SwiftUI development?
If used as a second consumer client, it will slow you down.

If repurposed into operator/admin tooling, it will help. The current web code should stop competing with the native app and instead become a faster path to manual ops, support, pause-sales controls, refund tools, and dashboard views.

Recommendation
Choose Option B in substance: keep the Java backend, repurpose Angular into operator/admin tooling, and remove Swing from active strategic planning unless an external business dependency proves otherwise.

SECTION 4 — Domain Model Audit
Domain	Exists Today	MVP Fit	Missing / Weaknesses	What Should Change Before MVP
Users	Yes	Good enough foundation	Generic lifecycle, no stronger trust or verification states	Keep, add verification and abuse/risk fields if needed
Vehicles	Yes	Partial	Plate and fit data exist, but no stronger normalization, no verification workflow	Keep, normalize plates, add vehicle verification if operators care
Operators	Yes	Partial	Very thin account model, no onboarding workflow, no staff roles, no payout identity	Add operator onboarding status, compliance flags, payout config
Parking locations	Yes	Partial	Core location data exists, plus hours and exceptions, but media, richer instructions, zone rules, operator notes, and pricing context are thin	Keep, add richer metadata and customer-facing content
Parking resources / spaces	Yes	Problematic	Resource rows mix “bookable thing” and “inventory pool” through capacity on a resource	Replace with inventory pools or bookable product classes, not physical-slot-first modeling
Availability	Partial	Weak for real ops	Derived from overlap counts plus hours/exceptions; no precomputed buckets, no operational pause reason model	Add inventory overrides and explicit sellability state
Pricing	Partial	Weak	Basic prices exist; no strong rules, taxes, cancellation snapshots, or Serbia-specific payment assumptions	Add pricing rules with snapshots captured on booking
Bookings / reservations	Yes	Incomplete	Thin lifecycle, no explicit hold entity, no check-in, no no-show, no operator cancel, no refund states	Introduce booking_holds and richer booking state machine
Payments	Yes	Incomplete	Intent exists, but model assumes synchronous authorization/confirmation	Add provider-side state, webhook events, capture/refund/reconcile
Refunds	No real model	No	Status enum mentions refunded, but no refund records or workflow	Add refunds table and refund reason/accounting trail
Check-ins	No	No	No explicit operator arrival/check-in/start/end evidence	Add checkins or booking_events with ARRIVED / STARTED / ENDED
Payouts	No	No	No operator settlement model	Add payout batches and payout items later if marketplace settles funds
Support	Yes	Partial	Customer ticketing exists, but no operator/admin queue workflow	Add support case ownership, SLA fields, internal notes
Audit logs	Schema exists	No	Infrastructure exists but is not wired into flows	Emit audit rows on every sensitive action
Is it modeled around individual spots or pooled inventory?
It is neither cleanly individual nor cleanly pooled. It is a hybrid that will become painful.

Current logic treats a ParkingResource as the thing being booked, while also attaching capacity to that same resource. That is visible in the booking path and locking model in ReservationService.java:117 and ParkingResourceRepository.java:14. That can work for a narrow pilot if one resource row represents one sellable lane, zone, or pool. It becomes awkward if operators actually think in terms of “we have 12 general spaces for compact cars between 08:00 and 20:00” rather than “resource A has capacity 12.”

For the MVP, you should model sellable inventory as inventory pools:

Per location
Per vehicle class or product class if needed
With confirmation mode and sellability rules
With overrides for closures, manual pauses, or event-based changes
Physical bays should be optional metadata unless the product truly requires exact spot assignment.

SECTION 5 — Booking Flow Audit
Is the server authoritative?
Mostly yes for final booking creation. Quote and create are server-side. Availability is checked on the backend. The create path locks the resource row before validating availability and writing the reservation, which is materially better than a naive client-side flow. See ReservationService.java:102 and ParkingResourceRepository.java:14.

Can the client create inconsistent bookings?
Yes, in two important ways:

The quote is advisory, not a persisted hold contract.
The create request already exposes quoteId and paymentMethodId in ReservationDtos.java:55, but the server create path does not actually use those fields.
That means the outward contract suggests a stronger quote-to-book pipeline than the backend actually enforces.

Is there a quote step?
Yes. The quote path is real and does vehicle-fit, hours, and pricing checks before returning totals and an expiry timestamp.

Is there a temporary hold step?
Sort of, but it is embedded inside the reservation itself. A created booking becomes PENDING_PAYMENT with paymentExpiresAt in ReservationService.java:138. That is not the same as having a dedicated hold entity.

Are holds short-lived?
Yes by configuration, currently 15 minutes in application.properties:52. But hold expiry is not enforced by a scheduler. It is opportunistically cleaned up when create or payment flows run, as seen in ReservationService.java:105 and PaymentService.java:91.

Can two users book the last available space?
For the same ParkingResource row, the create flow is reasonably protected because it locks that resource row before overlap counting. So this is not a simple “no locking” bug.

The real problem is higher-level:

Quotes can still go stale.
Hold expiry is lazy, not scheduled.
Inventory is modeled against resource rows rather than true sellable pools.
There is no durable, explicit hold ledger or allocation engine.
So the risk is not a trivial same-row double-book race. The risk is that the model will become hard to trust once you add real pooled inventory, real partner operations, and real payment uncertainty.

Are transactions or locks used correctly?
Partially.

Good: resource row is locked pessimistically before final create.
Good: idempotency is present.
Weak: no separate inventory-hold lock or booking hold row.
Weak: payment confirmation is synchronous and app-driven, not provider-event-driven.
Weak: no background expiry or reconciliation job.
Is there a booking state machine?
Only a thin enum and some ad hoc transitions. There is no central rules engine for valid transitions, cancellation windows, operator intervention, no-show, refund resolution, or failed settlement recovery.

Are state transitions validated?
Only minimally. Cancellation and confirmation are handled in service methods, but there is no robust “allowed transitions from X to Y under Y conditions” layer.

Are cancellations, refunds, operator cancellations, no-shows, and check-ins represented?
Customer cancellation: yes, minimally.
Refunds: not as a real domain workflow.
Operator cancellation: no dedicated path.
No-show: no.
Check-in / arrival / active parking: not really, even though ACTIVE exists conceptually.
Pay-on-arrival mode: no real alternate booking completion path.
Is every booking action auditable?
No. Audit infrastructure exists, but no real booking/payment/service flows are emitting audit events.

Main race conditions and consistency risks
Quote can drift from actual bookable state.
Hold expiry depends on user traffic instead of scheduler enforcement.
Payment success is not authoritative because the app flow drives confirmation.
Inventory abstraction will get brittle when one operator wants pooled supply across time and categories.
There is no recovery/reconciliation story when provider state and booking state diverge.
Priority fixes
P0:

Introduce explicit booking_holds or equivalent hold ledger.
Move from resource-row capacity thinking to inventory pools.
Define and enforce a real booking state machine.
Add scheduled expiry and reconciliation jobs.
Add operator/admin booking intervention endpoints.
P1:

Add check-in, no-show, operator cancellation, refund, and pay-on-arrival paths if the business needs them.
Add booking event timeline and audit emission.
SECTION 6 — Payment Readiness Audit
Is a real payment domain integrated, or is it mocked?
Today it is effectively mocked.

There is a provider abstraction and payment intent model, but the live semantics are still mock-like. Payment creation and confirmation in PaymentService.java:46 and PaymentService.java:54 are synchronous and app-driven. The provider abstraction in PaymentProvider.java:7 only supports authorize.

Is payment synchronous, async, or webhook-authoritative?
Synchronous and application-driven. Not webhook-authoritative.

Are webhooks idempotent?
No webhook flow exists, so no.

Does the system tolerate duplicate provider events?
No real provider-event model exists, so no.

Is there a real payment state machine?
Only partially. The enum suggests more depth than the implementation actually supports. There is no robust capture, refund, delayed settlement, dispute, payout, or webhook replay handling.

Are refunds represented?
Not operationally. Client models mention refunded states, but backend domain behavior does not provide a real refund process.

Is there clean correlation between quote, hold, booking, and payment?
Not enough. The outward contract hints at it, but the actual persistence model does not maintain a strong chain from quote to hold to payment attempt to provider event to booking confirmation.

Is the backend tightly coupled to Stripe?
No. That part is good. The provider interface is abstract.

The problem is not Stripe-coupling. The problem is under-modeling. A different Serbian PSP will still require redirect handling, webhook verification, idempotency, payment attempt records, refunds, and reconciliation. The current abstraction is too thin to absorb that cleanly.

Is there wallet logic or pre-auth logic?
No wallet logic. Very shallow pre-auth logic.

Are payment failures and delayed confirmations handled safely?
Simple failures, yes. Delayed or out-of-band confirmations, no.

Recommended payment architecture for MVP
Keep it simple:

One payment_attempts table for every provider attempt.
One payment_provider_events table for inbound webhooks and delivery logs.
Booking stays pending until provider-confirmed success or explicit pay-on-arrival rule.
Provider adapter must support create payment session, verify webhook, capture or authorize semantics, cancel, refund, and map provider statuses to internal statuses.
All inbound events must be idempotent and stored before side effects.
Booking confirmation should be driven by authoritative provider outcome, not the mobile app’s hope that payment succeeded.
SECTION 7 — Operator Portal Audit
What operators can do today
Authenticate and own locations/resources.
Create and update locations and resources.
Configure hours and availability exceptions through backend-managed domain services.
See a summary dashboard and a resource health view.
Relevant entry points exist in OperatorController.java:21.

What operators cannot really do yet
View and manage upcoming bookings in an operational way.
Check in a driver.
Mark no-show.
Cancel or override a booking as an operator.
Pause sales with explicit operational reason flows.
Export bookings.
See real revenue or settlement information.
Run support queue workflows.
The existing summary even hardcodes zero revenue in OperatorService.java:74.

MVP-critical operator features missing
Capability	Exists?	MVP Critical?	Recommendation
Login/auth	Yes	Yes	Keep
Location/resource management	Partial	Yes	Keep and tighten
Opening hours and blackout control	Partial	Yes	Keep and expose clearly in Angular
Sellable capacity control	Partial	Yes	Re-model around inventory pools
Upcoming bookings list	Not really operationalized	Yes	Build now
Booking detail and timeline	No	Yes	Build now
Check-in / arrival action	No	Yes if operator-attended parking	Build now
Pause sales / emergency close	Weak	Yes	Build now
Refund / cancel from operator side	No	Yes	Build now
Export / reports	No	No for day one	Later
Revenue / payout reporting	No real implementation	No for day one	Later
Should Angular become operator portal, admin portal, or both?
Both, with role-based separation.

That is the fastest path that aligns with the actual repo. Angular already has typed service layers for admin, operator, support, reservations, and payments. It should stop pretending to be a consumer app and become the control plane.

SECTION 8 — Internal Admin Audit
What exists today
Admin has:

A dashboard summary endpoint
A user list endpoint
An audit-events listing endpoint
See AdminController.java:17.

What is missing for real internal operations
Admin cannot currently do the things a founder or ops lead will actually need during a pilot:

Search and inspect bookings across all operators
Manually cancel, refund, or override a booking
Pause inventory or operator sales globally
Manage support queues with ownership and notes
Review real payment attempts and provider events
Inspect a trustworthy audit timeline
Export operator payout or booking reports
Handle operational incidents from one place
What should be built first
Build these first:

Global booking search and detail page
Operator/location quick actions
Pause sales and emergency closure tools
Booking timeline with payment and support events
Manual cancel/refund tooling with audit logging
Internal support queue
The current admin surface is a foundation, not a control room.

SECTION 9 — iOS Readiness Audit
Is the backend exposed as a clean REST/JSON API for iOS?
Yes, mostly. The transport layer is clean enough for SwiftUI. The iOS app container is sensibly organized in SpotLinkAppContainer.swift, and the real product-facing UI exists in files like SearchMapView.swift:13 and ReservationBookingFlowView.swift:264.

Is the backend mobile-friendly?
Partially.

Good: JWT/refresh flow exists.
Good: predictable error envelope.
Good: search, reservation, payment, support, profile, notification modules exist.
Weak: some DTO contracts drift from backend reality.
Weak: booking/payment model is not robust enough for real-world mobile failure modes.
Are auth flows mobile-compatible?
Yes. This is one of the better parts of the backend.

Are error responses standardized?
Yes. This is a real strength.

Do OpenAPI docs exist?
Yes. Runtime springdoc exists, and there are static contract docs under docs as well.

Are booking and payment flows safe enough for mobile clients?
Not yet for a real launch. The iOS flow is real, but it sits on top of a payment/booking backend that is still too synchronous and too thin for provider uncertainty, delayed settlement, and operational recovery.

Are image URLs and map/location fields ready?
Map and geo basics are good enough. Media strategy is not. There is no mature image upload/signing story visible in the backend.

Is localization considered?
Partially. The iOS tests and strings show Serbian-language intent, while backend errors remain English and the system default currency still points to USD in application.properties:51. That is not fatal, but it is inconsistent.

Are push notification hooks possible?
Possible, yes. Ready, no. Device registration exists, but notification flows are not visibly wired from booking and payment events.

iOS verdict
The iOS client is the best user-facing asset in this repo. It is worth continuing as the primary customer surface. The blocker is not the iOS code. The blocker is backend domain depth and operational trust.

SECTION 10 — Security, Privacy, and Trust Audit
What is already good
Route-level authorization is defined and reasonable in SecurityConfig.java:74.
CSRF handling for cookie-based web sessions is present in SecurityConfig.java:57.
Request IDs are generated and propagated.
API errors consistently include request context.
Production blocks the default JWT secret, as enforced in JwtService.java:42.
What is not acceptable before launch
No rate limiting or abuse controls.
No real audit trail on booking, payment, support, or admin-sensitive actions.
No real payment provider event verification because there is no webhook model.
Mock payment is enabled by default in application.properties:53.
Cookie secure defaults to false in application.properties:47.
PII such as vehicle plates and support content appears to be stored plainly, with no stronger privacy handling visible.
Notification and audit infrastructure exist but are not actually driving trust workflows.
Privacy posture
This is not obviously reckless, but it is not yet privacy-mature. For a Serbia-first pilot, that may be acceptable if scope is controlled, but you still need:

Data retention rules
Internal access logging
PII minimization
Operational playbooks for support access
A real answer on what customer data support and operators can see
Trust verdict
The codebase has good security plumbing but weak trust operations. That means it can reject unauthorized requests better than it can explain, reconcile, or prove what happened during an incident.

SECTION 11 — Reliability and Observability Audit
What exists
Health endpoint in HealthController.java
Request correlation IDs in RequestCorrelationFilter.java
Actuator exposure
CI for backend, frontend, and iOS in ci.yml:47 and ci.yml:70
What does not exist in a usable production sense
No scheduler or worker layer for hold expiry or reconciliation
No real provider-event ingestion
No structured business event timeline
No alerting hooks visible
No event replay tools
No dead-letter pattern
No operator-facing incident tooling
No first-party deployment artifacts
The repo-wide search only found hold expiry being called inside request paths, not on a scheduled basis, via ReservationService.java:105 and PaymentService.java:91.

Minimum observability baseline needed before pilot
booking_events table
payment_provider_events table
audit_events actually emitted
alerts on payment failures, webhook failures, and booking state drift
dashboard counters for active holds, expired holds, failed confirmations, and operator pause status
simple runbooks for manual recovery
Reliability verdict
You can demo this. You should not trust it under real operator pressure without adding jobs, events, and recovery visibility.

SECTION 12 — Testing Audit
What was validated
Backend tests pass: 23 tests
Frontend tests pass: 13 tests
Swift package build passes
Swift tests pass: 82 tests in the package-based Swift Testing suites
Root iOS test script is broken because it points at a non-existent executable
Evidence of the broken repo-level iOS script is in package.json:15 and the stale documentation is in README.md:177. CI itself uses the correct Swift test command in ci.yml:70.

Backend test quality
Decent foundation coverage, but not the coverage that matters most for a parking marketplace. What is still missing:

Reservation concurrency against Postgres locking behavior
Payment webhook idempotency
Refund and cancellation policy paths
Operator/admin intervention flows
Scheduled hold expiry
Notification emission from business flows
Audit event emission
Frontend test quality
The Angular tests are fine for a shell app. That is the problem: they mostly validate infrastructure and component behavior inside a shell app, not meaningful end-user flows.

iOS test quality
This is stronger than expected. There are real Swift Testing suites for DTO contracts, search view-model behavior, reservation flow DTO/idempotency, validators, session logic, and API client behavior, such as ReservationFlowTests.swift:149 and SearchMapViewModelTests.swift:93.

Biggest testing gaps
End-to-end booking with real state transitions
Payment provider simulation and webhook replay
Operator incident workflows
Admin override workflows
Contract drift tests between backend DTOs and iOS models
Postgres-specific locking and transaction behavior
SECTION 13 — Code Quality Audit
Overall code quality is better than the product readiness level. Naming is mostly coherent. The backend is modular. DTO separation exists. Error handling is clean. The main problem is not messy code. It is honest-but-dangerous incompleteness hidden behind polished foundations.

Top 10 code smells
Smell	Why It Matters	Evidence
Inventory modeled through resource rows with capacity	This will get brittle as soon as operators think in pools, not spots	ReservationService.java:117, ParkingResourceRepository.java:16
Hold is embedded in reservation, not modeled explicitly	Makes expiry, reconciliation, and payment coordination harder	ReservationService.java:138, V4__reservation_payment_hold_expiry.sql
Payment abstraction is too thin	Real PSPs need redirects, webhooks, refunds, disputes, and idempotency	PaymentProvider.java:7
Booking contract is ahead of implementation	create request exposes quoteId and paymentMethodId but backend does not honor them	ReservationDtos.java:55
Audit infrastructure is dead code	You have the table and service, but not the trust story	AuditService.java
Notification infrastructure is mostly unwired	Delivery exists, but business events are not visibly creating notifications	NotificationService.java
Angular surface overstates readiness	One real page, many wrappers, almost no workflows	app.routes.ts:6, foundation-dashboard.component.ts:29
Cross-client DTO drift exists	iOS profile/admin/operator models do not cleanly match backend DTO shapes	ProfileModels.swift:71, OperatorDtos.java, admin.models.ts:1
Operator/admin numbers can be placeholder-ish	Example: gross revenue is hardcoded zero in dashboard summary	OperatorService.java:74
Root iOS developer workflow is broken	Root script and README claim a runner that CI does not use and that does not exist	package.json:15, README.md:101, ci.yml:70
Maintainability verdict
Maintainable, if scope is narrowed. Dangerous, if you keep layering new features on top of the current half-generic, half-product domain model.

SECTION 14 — Data Model and Database Recommendations
For the MVP, I would simplify around a few hard truths: one operator can have multiple locations, each location sells inventory pools, every booking is linked to one hold and one pricing snapshot, every provider interaction is stored, and every operationally sensitive action emits an audit event.

Entity	Purpose	Key Fields	Constraints / Indexes
users	Identity	email, phone, password_hash or auth method, status, locale	unique email, index status
user_roles	Roles	user_id, role	unique user_id + role
vehicles	Customer vehicles	user_id, plate_raw, plate_normalized, type, dimensions	unique user_id + plate_normalized, index user_id
operators	Operator account	display_name, legal_name, support_email, onboarding_status, active	unique user mapping, index onboarding_status
locations	Parking venue	operator_id, name, address, lat, lon, timezone, access_type, instructions	index operator_id, geo index if needed
inventory_pools	Sellable capacity	location_id, pool_name, vehicle_type, confirmation_mode, base_capacity, active	unique logical pool key, index location_id + active
availability_overrides	Manual closures / exceptions	pool_id, starts_at, ends_at, reason, sellable_capacity_override, source	index pool_id + starts_at
pricing_rules	Sell price model	pool_id, rule_type, amount, min_duration, valid_from, valid_to	index pool_id + valid window
booking_holds	Short-lived reservation claims	booking_id nullable, pool_id, user_id, starts_at, ends_at, expires_at, status	index pool_id + time window, index expires_at + status
bookings	Customer reservation	hold_id, user_id, operator_id, location_id, pool_id, vehicle_id, status, price_snapshot_json, payment_mode	index user_id + starts_at, operator_id + starts_at, status + starts_at
booking_events	Timeline	booking_id, event_type, actor_type, actor_id, payload_json, occurred_at	index booking_id + occurred_at
payment_attempts	Payment lifecycle	booking_id, provider, provider_ref, amount, currency, status, idempotency_key	unique provider + provider_ref, index booking_id
payment_provider_events	Webhook/event log	provider, event_id, event_type, payload_json, processed_at, status	unique provider + event_id
refunds	Refund records	payment_attempt_id, booking_id, amount, reason, status, provider_ref	index booking_id
checkins	Arrival / usage evidence	booking_id, operator_user_id, checkin_at, checkout_at, evidence	unique active checkin per booking
payouts	Operator settlement	operator_id, period_start, period_end, amount, status	index operator_id + period
support_cases	Customer and ops support	booking_id nullable, location_id nullable, user_id, owner_user_id, status, priority, subject	index status + owner_user_id
support_messages	Thread messages	case_id, author_user_id, author_role, body, attachment_url	index case_id + created_at
audit_logs	Sensitive action trail	actor_user_id, action, resource_type, resource_id, metadata_json	index resource_type + resource_id, actor_user_id + created_at
What not to model yet
Do not model these before pilot unless a paying operator already requires them:

Exact physical bay assignment
Wallet balances
Loyalty points
Marketplace payout splitting beyond a simple settlement report
Reviews and ratings
Subscription plans
ANPR hardware event streams
Multi-country tax complexity
SECTION 15 — MVP Gap Analysis
Capability	Exists Today?	Quality	MVP Critical?	Recommended Action
Auth/login	Yes	Good	Yes	Keep
Driver profile	Yes	Good enough	Yes	Keep
Vehicle management	Yes	Good enough	Yes	Keep
Search locations	Yes	Good foundation	Yes	Keep and tighten
Availability calculation	Partial	Risky under real ops complexity	Yes	Refactor
Quote generation	Yes	Good start	Yes	Keep, persist stronger quote or fold into hold
Temporary hold	Partial	Too implicit	Yes	Rebuild as explicit hold
Booking create	Yes	Good start	Yes	Keep and refactor
Booking list/detail	Yes for customer	Good enough	Yes	Keep
Booking cancellation	Partial	Too thin	Yes	Expand
Check-in / arrival	No	Missing	Yes if attended parking	Build
No-show handling	No	Missing	Maybe	Build if operators need it
Payment collection	Partial	Not launch-safe	Yes	Rebuild around provider events
Refunds	No real flow	Missing	Yes	Build
Operator dashboard	Partial	Thin	Yes	Expand in Angular
Operator booking management	No real flow	Missing	Yes	Build
Admin dashboard	Partial	Thin	Yes	Expand in Angular
Support ticketing	Partial	Basic customer-only shape	Yes	Add internal queue workflow
Notifications	Partial	Infrastructure only	Useful	Wire from business events
Audit logs	Partial	Unwired	Yes	Wire now
Monitoring / alerting	Partial	Too light	Yes	Build minimum baseline
Deployment story	Weak	Manual	Yes	Add first-party deployment path
Consumer web app	Mostly no	Not needed for pilot	No	Deprioritize
Swing desktop app	No active code	Not relevant	No	Remove from plan
SECTION 16 — Recommended Target Architecture
Backend
Keep a single Spring Boot monolith.

Inside that monolith, create clearer bounded modules:

Identity and auth
Operator and location management
Inventory and availability
Booking and booking events
Payments and provider events
Support
Notifications
Admin and audit
Use Postgres for real environments. Keep Flyway. Keep JPA if the team is productive with it.

API
Expose one clean mobile-first JSON API under the current versioned path strategy. Keep the transport simple. Tighten DTO consistency and stop exposing fields that are not actually enforced.

Angular
Use Angular only for operator and internal admin tooling. Do not spend cycles turning it into a polished consumer booking client before the pilot works.

Swing
No active role. Remove it from planning unless there is an external, business-critical Swing app not present in this repo.

iOS
Continue with SwiftUI as the primary customer surface. It is already the most substantive product client.

Payments
Abstract by provider, but model around real provider behavior:

payment_attempts
provider events
idempotent webhook ingestion
booking confirmation from provider-authoritative state
Notifications
Keep the current abstraction, but emit notifications from booking/payment/support events instead of leaving it as a side module.

Background jobs
Add a small internal jobs layer for:

hold expiry
payment reconciliation
notification retries
support/admin alerts if needed
Observability
Add:

booking event timeline
provider event log
emitted audit events
alerts on stuck holds, failed confirmations, and provider failures
SECTION 17 — Refactor vs Rewrite Decision
Recommendation: keep and refactor.

Do not do a full rewrite. That would burn time and destroy real assets you already have:

A decent backend foundation
A real iOS client
Good auth and API hygiene
Real tests
Do not keep the current shape untouched either. That would lead to a fragile pilot.

The right move is a focused refactor:

Keep the Java monolith
Keep the iOS client
Repurpose Angular to ops/admin
Replace the current inventory/hold/payment core with a more explicit and trustworthy domain model
Remove Swing from the active roadmap unless it exists elsewhere and matters commercially
If you insist on using the label “partial rewrite,” the only thing I would partially rewrite is the booking and payment core inside the backend. But at the repo level, this is still best described as keep and refactor.

SECTION 18 — 30-Day Execution Plan
Week 1
Freeze the pilot scope: Belgrade, curated operator set, parking type, payment mode, check-in expectations.
Decide inventory model: inventory pools, not resource-row capacity guessing.
Define booking state machine and payment state machine.
Fix repo hygiene drift around iOS test commands and contract mismatches.
Add contract tests for backend to iOS DTO compatibility.
Choose the Serbian PSP or decide on pay-on-arrival fallback.
Week 2
Implement inventory pools and explicit booking holds.
Add scheduled hold expiry and booking event timeline.
Build operator booking list and booking detail endpoints.
Build admin global booking search and pause-sales controls.
Add Postgres locking and concurrency tests for booking creation.
Week 3
Implement payment_attempts and provider event ingestion.
Wire booking confirmation to provider-authoritative success.
Add refund and cancellation flows.
Emit audit logs and notifications from booking/payment/admin actions.
Start Angular operator/admin screens on top of real endpoints.
Week 4
Add minimum observability and alerts.
Add support queue ownership and internal notes.
Seed pilot operators and test real end-to-end flows.
Write incident runbooks and manual recovery steps.
Run dry-run pilot drills: booking, cancellation, payment failure, operator closure, refund.
SECTION 19 — Priority Backlog
P0
Build inventory pools and explicit booking holds. Why: current resource-plus-capacity model will not scale into trustworthy operations. Likely files: ReservationService.java:102, ParkingResourceRepository.java:16, Flyway migrations. Acceptance: last-slot booking behaves correctly under concurrency, hold expiry works automatically, operator can pause or override supply. Effort: high. Risk: high.
Add real booking and payment state machines. Why: current transitions are too ad hoc. Likely files: ReservationService.java:138, PaymentService.java:54. Acceptance: clear allowed transitions, explicit failure and cancellation paths, auditable event timeline. Effort: high. Risk: high.
Add payment attempts and provider events. Why: synchronous app-driven payment is not trustworthy. Likely files: PaymentService.java:46, PaymentProvider.java:7. Acceptance: webhook-authoritative confirmation, duplicate event safety, refund path. Effort: high. Risk: high.
Build operator/admin booking ops screens in Angular. Why: no pilot survives without manual control tools. Likely files: app.routes.ts:6, admin and operator service modules. Acceptance: operator sees upcoming bookings and can act; admin can search, pause, and inspect. Effort: medium. Risk: medium.
Wire audit logs and notifications from real events. Why: infrastructure exists but trust is missing. Likely files: AuditService.java, NotificationService.java. Acceptance: every booking/payment/admin action emits audit and optional notification events. Effort: medium. Risk: medium.
Fix repo and contract drift. Why: broken scripts and DTO mismatch slow delivery and hide risk. Likely files: package.json:15, README.md:177, ProfileModels.swift:71. Acceptance: root iOS tests work, docs match CI, iOS and backend contracts decode consistently. Effort: low to medium. Risk: medium.
P1
Add check-in, arrival, and no-show flow if operators need attended parking. Why: live operators need occupancy truth. Likely files: reservation and operator modules. Acceptance: operator can mark arrival and no-show with audit trail. Effort: medium. Risk: medium.
Add refund workflow and cancellation policy snapshots. Why: pilot disputes will happen. Likely files: payment and reservation modules. Acceptance: refund records exist, reasons are stored, operator/admin can trace outcomes. Effort: medium. Risk: medium.
Build internal support queue. Why: customer ticketing exists, ops workflow does not. Likely files: SupportController.java:25, support UI in Angular. Acceptance: support/admin can own, respond, and resolve cases with internal notes. Effort: medium. Risk: medium.
Add monitoring and alerts. Why: current health endpoint is too shallow. Likely files: HealthController.java, observability config. Acceptance: alert on stuck holds, provider failures, and abnormal booking errors. Effort: medium. Risk: medium.
Localize and Serbia-fit the platform defaults. Why: USD and generic market defaults are wrong for this launch. Likely files: application.properties:51, pricing models, iOS strings. Acceptance: RSD, local PSP assumptions, local operator copy, local support flows. Effort: medium. Risk: low.
Harden permissions and abuse controls. Why: security baseline exists, trust controls do not. Likely files: SecurityConfig.java:74. Acceptance: rate limiting, abuse throttling, clearer internal access logging. Effort: medium. Risk: medium.
P2
Add richer pricing rules and promo logic. Why: today’s pricing is too static. Likely files: location/pricing modules. Acceptance: time windows, events, partner-specific rules. Effort: medium. Risk: low.
Add operator reporting and exports. Why: useful for adoption, not day-one critical. Likely files: operator/admin modules. Acceptance: daily booking export and simple revenue views. Effort: medium. Risk: low.
Add media and richer location merchandising. Why: customer confidence matters. Likely files: location domain and iOS UI. Acceptance: locations have photos, richer instructions, and clearer arrival context. Effort: medium. Risk: low.
Add notification templates and delivery preferences. Why: current notification module is underused. Likely files: notification modules and iOS settings. Acceptance: booking confirmation, reminder, cancellation, and support notifications work. Effort: medium. Risk: low.
P3
Add consumer web booking experience. Why: not needed until the native and ops flows are proven. Acceptance: working web booking flow only after pilot proof. Effort: high. Risk: medium.
Add Android. Why: expansion step, not MVP blocker. Acceptance: stable Android client on same API contract. Effort: high. Risk: medium.
Add complex marketplace settlement and payouts. Why: premature unless business model requires platform-collected funds. Acceptance: payout batches and operator settlement reporting. Effort: high. Risk: high.
Add hardware, ANPR, or gate integrations. Why: can explode scope before product-market proof. Acceptance: only after core booking/payment/ops loop is stable. Effort: high. Risk: high.
SECTION 20 — Questions for the Founder
For the Belgrade pilot, is the product selling guaranteed reservations, simple lead capture, or reserve-now-pay-later access?
Will customers pay online in the first pilot, or is pay-on-arrival acceptable for some operators?
Which Serbian PSP are you willing to commit to now, and what payment UX does it require: redirect, embedded form, bank transfer, wallet, QR, or something else?
Do operators think in exact spots, zones, lanes, or just pooled capacity by vehicle type?
Is check-in by operator staff part of the real-world workflow, or is the booking itself enough?
Do operators need to manually pause availability during the day, and how often does that happen in reality?
Will support be handled by founders manually at first, or do you need a real internal queue from day one?
Is there any external Swing or desktop tool actually used by operators today, or is that just a legacy assumption?
Are refunds and cancellations expected to be common enough that manual admin handling is required in the first month?
What is the actual pilot promise to the customer: cheapest space, guaranteed space, premium trusted partner parking, or fastest booking experience?
The short version is this: keep the backend, keep iOS, repurpose Angular, drop Swing from active planning, and spend the next month turning booking and payment into something operationally trustworthy. The stack is not the problem. The domain depth is.