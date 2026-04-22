-- V3: Pilot partner profili, kapacitet resursa, radno vreme lokacija, izuzeci dostupnosti

-- Kapacitet i nacin potvrde za parking resurse (odvojene ALTER TABLE naredbe zbog H2 kompatibilnosti)
alter table parking_resources add column capacity int not null default 1;
alter table parking_resources add column confirmation_mode varchar(16) not null default 'INSTANT';

-- Partner profil (1-1 sa operator_accounts)
create table partner_profiles (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    operator_id uuid not null,
    partner_type varchar(32) not null default 'PILOT',
    onboarding_status varchar(32) not null default 'PENDING',
    pilot_fit_score int,
    contact_name varchar(160),
    contact_email varchar(320),
    contact_phone varchar(50),
    default_confirmation_mode varchar(16) not null default 'INSTANT',
    notes varchar(2000),
    active boolean not null default true,
    constraint uk_partner_profiles_operator unique (operator_id),
    constraint fk_partner_profiles_operator foreign key (operator_id) references operator_accounts (id) on delete cascade
);

-- Nedeljno radno vreme lokacije (UTC windowsi, tumaci se u timezone lokacije)
create table location_hours (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    location_id uuid not null,
    day_of_week varchar(9) not null,
    open_time varchar(5) not null,
    close_time varchar(5) not null,
    constraint uk_location_hours_location_day unique (location_id, day_of_week),
    constraint fk_location_hours_location foreign key (location_id) references parking_locations (id) on delete cascade
);

-- Izuzeci dostupnosti / blokade za lokaciju
create table availability_exceptions (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    location_id uuid not null,
    label varchar(180),
    starts_at timestamp with time zone not null,
    ends_at timestamp with time zone not null,
    constraint fk_availability_exceptions_location foreign key (location_id) references parking_locations (id) on delete cascade
);
