create table users (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    email varchar(320) not null,
    password_hash varchar(255) not null,
    first_name varchar(100) not null,
    last_name varchar(100) not null,
    phone varchar(50),
    avatar_url varchar(500),
    bio varchar(1000),
    registration_status varchar(32) not null,
    constraint uk_users_email unique (email)
);

create table user_roles (
    user_id uuid not null,
    role varchar(32) not null,
    primary key (user_id, role),
    constraint fk_user_roles_user foreign key (user_id) references users (id) on delete cascade
);

create table user_preferences (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    user_id uuid not null,
    locale varchar(20) not null,
    marketing_opt_in boolean not null,
    reservation_alerts boolean not null,
    payment_alerts boolean not null,
    support_alerts boolean not null,
    constraint uk_user_preferences_user unique (user_id),
    constraint fk_user_preferences_user foreign key (user_id) references users (id) on delete cascade
);

create table operator_accounts (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    user_id uuid not null,
    display_name varchar(160) not null,
    legal_name varchar(200),
    support_email varchar(320),
    active boolean not null,
    constraint uk_operator_accounts_user unique (user_id),
    constraint fk_operator_accounts_user foreign key (user_id) references users (id) on delete cascade
);

create table password_reset_tokens (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    user_id uuid not null,
    token_hash varchar(128) not null,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone,
    constraint uk_password_reset_tokens_hash unique (token_hash),
    constraint fk_password_reset_tokens_user foreign key (user_id) references users (id) on delete cascade
);

create table vehicles (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    user_id uuid not null,
    type varchar(32) not null,
    nickname varchar(100),
    make varchar(100),
    model varchar(100),
    color varchar(60),
    license_plate varchar(40),
    height_meters numeric(5,2),
    length_meters numeric(5,2),
    ev_capable boolean not null,
    verification_status varchar(32) not null,
    constraint fk_vehicles_user foreign key (user_id) references users (id) on delete cascade
);

create table parking_locations (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    operator_id uuid not null,
    name varchar(180) not null,
    line1 varchar(180) not null,
    line2 varchar(180),
    city varchar(100) not null,
    region varchar(100),
    postal_code varchar(30),
    country varchar(2) not null,
    formatted_address varchar(500),
    latitude numeric(9,6) not null,
    longitude numeric(9,6) not null,
    timezone varchar(80) not null,
    access_type varchar(32) not null,
    public_notes varchar(1000),
    active boolean not null,
    constraint fk_parking_locations_operator foreign key (operator_id) references operator_accounts (id)
);

create table parking_resources (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    location_id uuid not null,
    type varchar(32) not null,
    label varchar(100) not null,
    floor varchar(40),
    bay_number varchar(40),
    max_height_meters numeric(5,2),
    max_length_meters numeric(5,2),
    allowed_vehicle_types varchar(200),
    ev_only boolean not null,
    hourly_rate_cents bigint not null,
    daily_rate_cents bigint,
    currency varchar(3) not null,
    instant_reserve boolean not null,
    active boolean not null,
    constraint fk_parking_resources_location foreign key (location_id) references parking_locations (id) on delete cascade
);

create table reservations (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    customer_id uuid not null,
    operator_id uuid not null,
    location_id uuid not null,
    resource_id uuid not null,
    vehicle_id uuid,
    starts_at timestamp with time zone not null,
    ends_at timestamp with time zone not null,
    timezone varchar(80) not null,
    status varchar(32) not null,
    total_amount_cents bigint not null,
    currency varchar(3) not null,
    access_instructions_visible boolean not null,
    idempotency_key varchar(160),
    constraint fk_reservations_customer foreign key (customer_id) references users (id),
    constraint fk_reservations_operator foreign key (operator_id) references operator_accounts (id),
    constraint fk_reservations_location foreign key (location_id) references parking_locations (id),
    constraint fk_reservations_resource foreign key (resource_id) references parking_resources (id),
    constraint fk_reservations_vehicle foreign key (vehicle_id) references vehicles (id)
);

create table payment_intents (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    reservation_id uuid not null,
    customer_id uuid not null,
    amount_cents bigint not null,
    currency varchar(3) not null,
    status varchar(32) not null,
    redirect_url varchar(1000),
    client_secret varchar(255),
    provider_reference varchar(160),
    idempotency_key varchar(160),
    constraint fk_payment_intents_reservation foreign key (reservation_id) references reservations (id),
    constraint fk_payment_intents_customer foreign key (customer_id) references users (id)
);

create table support_tickets (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    requester_user_id uuid not null,
    category varchar(32) not null,
    status varchar(32) not null,
    subject varchar(180) not null,
    reservation_id uuid,
    location_id uuid,
    constraint fk_support_tickets_user foreign key (requester_user_id) references users (id),
    constraint fk_support_tickets_reservation foreign key (reservation_id) references reservations (id),
    constraint fk_support_tickets_location foreign key (location_id) references parking_locations (id)
);

create table support_messages (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    ticket_id uuid not null,
    sender_user_id uuid not null,
    sender_name varchar(200),
    body varchar(4000) not null,
    attachment_url varchar(1000),
    constraint fk_support_messages_ticket foreign key (ticket_id) references support_tickets (id) on delete cascade,
    constraint fk_support_messages_sender foreign key (sender_user_id) references users (id)
);

create table notifications (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    user_id uuid not null,
    type varchar(48) not null,
    title varchar(180) not null,
    body varchar(1000) not null,
    related_entity_id uuid,
    read_flag boolean not null,
    constraint fk_notifications_user foreign key (user_id) references users (id) on delete cascade
);

create table device_tokens (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    user_id uuid not null,
    device_token varchar(500) not null,
    platform varchar(20) not null,
    active boolean not null,
    constraint uk_device_tokens_token unique (device_token),
    constraint fk_device_tokens_user foreign key (user_id) references users (id) on delete cascade
);

create table audit_events (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    actor_user_id uuid not null,
    action varchar(120) not null,
    resource_type varchar(120) not null,
    resource_id varchar(120) not null,
    metadata varchar(4000),
    constraint fk_audit_events_actor foreign key (actor_user_id) references users (id)
);

create table idempotency_keys (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    idempotency_key varchar(160) not null,
    user_id uuid not null,
    scope varchar(80) not null,
    status varchar(32) not null,
    response_status integer,
    response_body varchar(4000),
    error_message varchar(1000),
    expires_at timestamp with time zone not null,
    constraint uk_idempotency_user_scope_key unique (user_id, scope, idempotency_key),
    constraint fk_idempotency_keys_user foreign key (user_id) references users (id) on delete cascade
);

create table analytics_events (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    event_name varchar(160) not null,
    properties varchar(4000),
    occurred_at timestamp with time zone not null,
    url varchar(1000),
    session_id varchar(120) not null
);

create index idx_vehicles_user on vehicles (user_id);
create index idx_locations_operator on parking_locations (operator_id);
create index idx_locations_active_city on parking_locations (active, city);
create index idx_resources_location_active on parking_resources (location_id, active);
create index idx_reservations_customer_starts on reservations (customer_id, starts_at);
create index idx_reservations_operator_starts on reservations (operator_id, starts_at);
create index idx_reservations_resource_window on reservations (resource_id, starts_at, ends_at, status);
create index idx_payment_intents_reservation on payment_intents (reservation_id);
create index idx_support_tickets_requester on support_tickets (requester_user_id, updated_at);
create index idx_support_messages_ticket on support_messages (ticket_id, created_at);
create index idx_notifications_user_read on notifications (user_id, read_flag, created_at);
create index idx_audit_events_created on audit_events (created_at);
create index idx_analytics_events_session on analytics_events (session_id, occurred_at);
