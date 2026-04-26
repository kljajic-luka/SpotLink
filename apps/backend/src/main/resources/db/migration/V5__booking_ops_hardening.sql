alter table reservations add column inventory_pool_id uuid;
alter table reservations add column hold_id uuid;
alter table reservations add column payment_mode varchar(32) not null default 'ONLINE';

create table inventory_pools (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    location_id uuid not null,
    source_resource_id uuid,
    label varchar(120) not null,
    allowed_vehicle_types varchar(200),
    ev_only boolean not null,
    max_height_meters numeric(5,2),
    max_length_meters numeric(5,2),
    hourly_rate_cents bigint not null,
    daily_rate_cents bigint,
    currency varchar(3) not null,
    base_capacity int not null,
    confirmation_mode varchar(16) not null,
    pay_on_arrival_enabled boolean not null default true,
    active boolean not null,
    paused boolean not null default false,
    pause_reason varchar(240),
    constraint fk_inventory_pools_location foreign key (location_id) references parking_locations (id) on delete cascade,
    constraint fk_inventory_pools_source_resource foreign key (source_resource_id) references parking_resources (id) on delete set null,
    constraint uk_inventory_pools_source_resource unique (source_resource_id)
);

create table availability_overrides (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    inventory_pool_id uuid not null,
    actor_user_id uuid,
    override_type varchar(32) not null,
    starts_at timestamp with time zone not null,
    ends_at timestamp with time zone not null,
    sellable_capacity int,
    reason varchar(240),
    source varchar(32) not null,
    active boolean not null default true,
    constraint fk_availability_overrides_pool foreign key (inventory_pool_id) references inventory_pools (id) on delete cascade,
    constraint fk_availability_overrides_actor foreign key (actor_user_id) references users (id)
);

create table booking_holds (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    reservation_id uuid,
    inventory_pool_id uuid not null,
    customer_id uuid not null,
    starts_at timestamp with time zone not null,
    ends_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    status varchar(32) not null,
    idempotency_key varchar(160),
    amount_cents bigint not null,
    currency varchar(3) not null,
    payment_mode varchar(32) not null,
    constraint fk_booking_holds_reservation foreign key (reservation_id) references reservations (id) on delete set null,
    constraint fk_booking_holds_pool foreign key (inventory_pool_id) references inventory_pools (id) on delete cascade,
    constraint fk_booking_holds_customer foreign key (customer_id) references users (id),
    constraint uk_booking_holds_idempotency unique (customer_id, idempotency_key)
);

create table booking_events (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    reservation_id uuid not null,
    event_type varchar(48) not null,
    actor_type varchar(32) not null,
    actor_id uuid,
    notes varchar(1000),
    payload varchar(4000),
    occurred_at timestamp with time zone not null,
    constraint fk_booking_events_reservation foreign key (reservation_id) references reservations (id) on delete cascade
);

create table payment_attempts (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    reservation_id uuid not null,
    customer_id uuid not null,
    provider varchar(64) not null,
    status varchar(32) not null,
    payment_mode varchar(32) not null,
    amount_cents bigint not null,
    currency varchar(3) not null,
    provider_reference varchar(160),
    idempotency_key varchar(160),
    failure_code varchar(64),
    failure_message varchar(1000),
    last_transition_at timestamp with time zone not null,
    constraint fk_payment_attempts_reservation foreign key (reservation_id) references reservations (id) on delete cascade,
    constraint fk_payment_attempts_customer foreign key (customer_id) references users (id),
    constraint uk_payment_attempts_customer_idempotency unique (customer_id, idempotency_key)
);

create table payment_provider_events (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    payment_attempt_id uuid,
    provider varchar(64) not null,
    external_event_id varchar(160) not null,
    event_type varchar(120) not null,
    payload varchar(4000),
    status varchar(32) not null,
    processed_at timestamp with time zone,
    constraint fk_payment_provider_events_attempt foreign key (payment_attempt_id) references payment_attempts (id) on delete set null,
    constraint uk_payment_provider_events_provider_event unique (provider, external_event_id)
);

create table refunds (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    reservation_id uuid not null,
    payment_attempt_id uuid,
    amount_cents bigint not null,
    currency varchar(3) not null,
    status varchar(32) not null,
    reason varchar(240),
    provider_reference varchar(160),
    marked_by_user_id uuid,
    marked_at timestamp with time zone not null,
    constraint fk_refunds_reservation foreign key (reservation_id) references reservations (id) on delete cascade,
    constraint fk_refunds_payment_attempt foreign key (payment_attempt_id) references payment_attempts (id) on delete set null,
    constraint fk_refunds_marked_by foreign key (marked_by_user_id) references users (id)
);

create table checkins (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    reservation_id uuid not null,
    operator_user_id uuid not null,
    status varchar(32) not null,
    checkin_at timestamp with time zone not null,
    checkout_at timestamp with time zone,
    notes varchar(1000),
    constraint fk_checkins_reservation foreign key (reservation_id) references reservations (id) on delete cascade,
    constraint fk_checkins_operator_user foreign key (operator_user_id) references users (id),
    constraint uk_checkins_reservation unique (reservation_id)
);

create table audit_logs (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    actor_user_id uuid not null,
    action varchar(120) not null,
    resource_type varchar(120) not null,
    resource_id varchar(120) not null,
    metadata varchar(4000),
    occurred_at timestamp with time zone not null,
    constraint fk_audit_logs_actor foreign key (actor_user_id) references users (id)
);

insert into inventory_pools (
    id,
    created_at,
    updated_at,
    version,
    location_id,
    source_resource_id,
    label,
    allowed_vehicle_types,
    ev_only,
    max_height_meters,
    max_length_meters,
    hourly_rate_cents,
    daily_rate_cents,
    currency,
    base_capacity,
    confirmation_mode,
    pay_on_arrival_enabled,
    active,
    paused,
    pause_reason
)
select
    id,
    created_at,
    updated_at,
    version,
    location_id,
    id,
    label,
    allowed_vehicle_types,
    ev_only,
    max_height_meters,
    max_length_meters,
    hourly_rate_cents,
    daily_rate_cents,
    currency,
    capacity,
    confirmation_mode,
    true,
    active,
    false,
    null
from parking_resources;

update reservations
set inventory_pool_id = resource_id
where inventory_pool_id is null;

insert into booking_holds (
    id,
    created_at,
    updated_at,
    version,
    reservation_id,
    inventory_pool_id,
    customer_id,
    starts_at,
    ends_at,
    expires_at,
    status,
    idempotency_key,
    amount_cents,
    currency,
    payment_mode
)
select
    id,
    created_at,
    updated_at,
    version,
    id,
    resource_id,
    customer_id,
    starts_at,
    ends_at,
    coalesce(payment_expires_at, updated_at),
    case
        when status = 'PENDING_PAYMENT' then 'ACTIVE'
        when status = 'EXPIRED' then 'EXPIRED'
        else 'CONSUMED'
    end,
    idempotency_key,
    total_amount_cents,
    currency,
    payment_mode
from reservations;

update reservations
set hold_id = id
where hold_id is null;

insert into booking_events (
    id,
    created_at,
    updated_at,
    version,
    reservation_id,
    event_type,
    actor_type,
    actor_id,
    notes,
    payload,
    occurred_at
)
select
    id,
    created_at,
    updated_at,
    version,
    id,
    'LEGACY_IMPORTED',
    'SYSTEM',
    null,
    null,
    null,
    created_at
from reservations;

insert into payment_attempts (
    id,
    created_at,
    updated_at,
    version,
    reservation_id,
    customer_id,
    provider,
    status,
    payment_mode,
    amount_cents,
    currency,
    provider_reference,
    idempotency_key,
    failure_code,
    failure_message,
    last_transition_at
)
select
    id,
    created_at,
    updated_at,
    version,
    reservation_id,
    customer_id,
    'LEGACY_PAYMENT_INTENT',
    status,
    'ONLINE',
    amount_cents,
    currency,
    provider_reference,
    idempotency_key,
    null,
    null,
    updated_at
from payment_intents;

insert into payment_provider_events (
    id,
    created_at,
    updated_at,
    version,
    payment_attempt_id,
    provider,
    external_event_id,
    event_type,
    payload,
    status,
    processed_at
)
select
    id,
    created_at,
    updated_at,
    version,
    id,
    'LEGACY_PAYMENT_INTENT',
    cast(id as varchar(160)),
    'LEGACY_IMPORTED',
    null,
    'PROCESSED',
    updated_at
from payment_intents;

insert into audit_logs (
    id,
    created_at,
    updated_at,
    version,
    actor_user_id,
    action,
    resource_type,
    resource_id,
    metadata,
    occurred_at
)
select
    id,
    created_at,
    updated_at,
    version,
    actor_user_id,
    action,
    resource_type,
    resource_id,
    metadata,
    created_at
from audit_events;

alter table reservations add constraint fk_reservations_inventory_pool foreign key (inventory_pool_id) references inventory_pools (id);
alter table reservations add constraint fk_reservations_hold foreign key (hold_id) references booking_holds (id);

create index idx_inventory_pools_location_active on inventory_pools (location_id, active);
create index idx_availability_overrides_pool_window on availability_overrides (inventory_pool_id, starts_at, ends_at, active);
create index idx_booking_holds_pool_window_status on booking_holds (inventory_pool_id, starts_at, ends_at, status);
create index idx_booking_holds_expires_status on booking_holds (expires_at, status);
create index idx_booking_events_reservation_occurred on booking_events (reservation_id, occurred_at);
create index idx_payment_attempts_reservation on payment_attempts (reservation_id, last_transition_at);
create index idx_payment_provider_events_attempt on payment_provider_events (payment_attempt_id, processed_at);
create index idx_refunds_reservation on refunds (reservation_id, marked_at);
create index idx_audit_logs_created on audit_logs (created_at);