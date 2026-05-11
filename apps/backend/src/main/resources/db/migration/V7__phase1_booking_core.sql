update reservations
set booking_code = concat('SL-', upper(substring(replace(cast(id as varchar), '-', ''), 1, 8)))
where booking_code is null;
alter table reservations alter column booking_code set not null;

alter table reservations add column cancellation_policy varchar(64) not null default 'FULL_REFUND_BEFORE_START';
alter table reservations add column cancellable_until timestamp with time zone;
update reservations
set cancellable_until = starts_at
where cancellable_until is null;
alter table reservations alter column cancellable_until set not null;

alter table reservations add column refund_eligible_cents bigint not null default 0;
alter table reservations add column operator_confirmation_expires_at timestamp with time zone;
