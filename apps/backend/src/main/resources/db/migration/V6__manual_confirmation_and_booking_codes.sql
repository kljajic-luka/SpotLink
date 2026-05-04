alter table reservations add column booking_code varchar(16);
alter table reservations add constraint uk_reservations_booking_code unique (booking_code);
