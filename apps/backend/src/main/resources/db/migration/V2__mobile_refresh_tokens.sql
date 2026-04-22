create table refresh_tokens (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    user_id uuid not null,
    token_hash varchar(128) not null,
    device_id varchar(160),
    user_agent varchar(500),
    issued_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    replaced_by_token_id uuid,
    constraint uk_refresh_tokens_hash unique (token_hash),
    constraint fk_refresh_tokens_user foreign key (user_id) references users (id) on delete cascade,
    constraint fk_refresh_tokens_replaced_by foreign key (replaced_by_token_id) references refresh_tokens (id)
);

create index idx_refresh_tokens_user on refresh_tokens (user_id);
create index idx_refresh_tokens_expires on refresh_tokens (expires_at);
create index idx_refresh_tokens_revoked on refresh_tokens (revoked_at);
