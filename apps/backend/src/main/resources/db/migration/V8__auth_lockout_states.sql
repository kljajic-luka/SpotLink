create table auth_lockout_states (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    identifier_hash varchar(128) not null,
    user_id uuid,
    failed_count integer not null,
    first_failed_at timestamp with time zone not null,
    last_failed_at timestamp with time zone not null,
    locked_until timestamp with time zone,
    constraint uk_auth_lockout_states_identifier unique (identifier_hash),
    constraint fk_auth_lockout_states_user foreign key (user_id) references users (id) on delete set null
);

create index idx_auth_lockout_states_user on auth_lockout_states (user_id);
create index idx_auth_lockout_states_expiry on auth_lockout_states (last_failed_at, locked_until);
