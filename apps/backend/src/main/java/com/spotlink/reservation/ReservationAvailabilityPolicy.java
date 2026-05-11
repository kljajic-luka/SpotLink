package com.spotlink.reservation;

import com.spotlink.core.AppProperties;
import com.spotlink.core.ConflictException;
import com.spotlink.inventory.InventoryPool;
import com.spotlink.inventory.InventoryPoolService;
import com.spotlink.location.AvailabilityExceptionRepository;
import com.spotlink.location.LocationHours;
import com.spotlink.location.LocationHoursRepository;
import com.spotlink.location.ParkingLocation;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReservationAvailabilityPolicy {

    private static final List<ReservationStatus> BLOCKING_STATUSES = List.of(
            ReservationStatus.CONFIRMED,
            ReservationStatus.ACTIVE,
            ReservationStatus.DISPUTED,
            ReservationStatus.NO_SHOW);

    private final ReservationRepository reservations;
    private final BookingHoldRepository bookingHolds;
    private final InventoryPoolService inventoryPools;
    private final LocationHoursRepository locationHours;
    private final AvailabilityExceptionRepository availabilityExceptions;
    private final AppProperties appProperties;
    private final Clock clock;

    public ReservationAvailabilityPolicy(
            ReservationRepository reservations,
            BookingHoldRepository bookingHolds,
            InventoryPoolService inventoryPools,
            LocationHoursRepository locationHours,
            AvailabilityExceptionRepository availabilityExceptions,
            AppProperties appProperties,
            Clock clock) {
        this.reservations = reservations;
        this.bookingHolds = bookingHolds;
        this.inventoryPools = inventoryPools;
        this.locationHours = locationHours;
        this.availabilityExceptions = availabilityExceptions;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    public AvailabilityAssessment assess(
            ParkingLocation location,
            InventoryPool pool,
            Instant startsAt,
            Instant endsAt,
            Instant now) {
        AvailabilityAssessment windowAssessment = validateWindow(startsAt, endsAt, now);
        if (!windowAssessment.bookable()) {
            return windowAssessment;
        }

        if (!location.isActive() || !pool.isActive()) {
            return unavailable("RESOURCE_UNAVAILABLE", "Parking inventory is not currently active.");
        }

        if (availabilityExceptions.countOverlapping(location.getId(), startsAt, endsAt) > 0) {
            return unavailable("RESOURCE_UNAVAILABLE", "Location is closed for this time window.");
        }

        HoursAssessment hoursAssessment = validateHours(location, startsAt, endsAt);
        if (!hoursAssessment.valid()) {
            return unavailable("RESOURCE_UNAVAILABLE", "Location availability configuration is invalid.");
        }
        if (!hoursAssessment.open()) {
            return unavailable("RESOURCE_UNAVAILABLE", "Location is closed for this time window.");
        }

        InventoryPoolService.AvailabilityDecision poolDecision = inventoryPools.availabilityForWindow(pool, startsAt, endsAt);
        if (poolDecision.paused()) {
            return unavailable(
                    "RESOURCE_PAUSED",
                    poolDecision.reason() == null || poolDecision.reason().isBlank()
                            ? "Parking inventory is currently paused."
                            : poolDecision.reason());
        }
        if (poolDecision.sellableCapacity() <= 0) {
            return unavailable("RESOURCE_UNAVAILABLE", "Parking inventory is not available for this time window.");
        }

        long overlappingReservations = reservations.countPoolOverlaps(pool.getId(), startsAt, endsAt, BLOCKING_STATUSES);
        long overlappingHolds = bookingHolds.countActiveOverlaps(pool.getId(), startsAt, endsAt, now == null ? Instant.now(clock) : now);
        long availableCapacity = (long) poolDecision.sellableCapacity() - overlappingReservations - overlappingHolds;
        if (availableCapacity <= 0) {
            return unavailable("RESOURCE_UNAVAILABLE", "Parking inventory is not available for this time window.");
        }

        return new AvailabilityAssessment(true, null, null, (int) Math.min(Integer.MAX_VALUE, availableCapacity));
    }

    public void assertBookable(
            ParkingLocation location,
            InventoryPool pool,
            Instant startsAt,
            Instant endsAt,
            Instant now) {
        AvailabilityAssessment assessment = assess(location, pool, startsAt, endsAt, now);
        if (!assessment.bookable()) {
            throw new ConflictException(assessment.code(), assessment.message());
        }
    }

    private AvailabilityAssessment validateWindow(Instant startsAt, Instant endsAt, Instant now) {
        if (startsAt == null || endsAt == null) {
            return unavailable("INVALID_RESERVATION_WINDOW", "Reservation window is required.");
        }
        if (!startsAt.isBefore(endsAt)) {
            return unavailable("INVALID_RESERVATION_WINDOW", "Reservation end time must be after start time.");
        }

        long durationMinutes = Duration.between(startsAt, endsAt).toMinutes();
        if (durationMinutes < appProperties.getMinReservationMinutes()) {
            return unavailable(
                    "INVALID_RESERVATION_WINDOW",
                    "Reservation must be at least %d minutes long.".formatted(appProperties.getMinReservationMinutes()));
        }
        if (durationMinutes > (long) appProperties.getMaxReservationDays() * 24 * 60) {
            return unavailable(
                    "INVALID_RESERVATION_WINDOW",
                    "Reservation cannot exceed %d days.".formatted(appProperties.getMaxReservationDays()));
        }
        if (!isSlotAligned(startsAt) || !isSlotAligned(endsAt) || durationMinutes % appProperties.getBookingSlotMinutes() != 0) {
            return unavailable(
                    "INVALID_RESERVATION_WINDOW",
                    "Reservation window must align to %d-minute slots.".formatted(appProperties.getBookingSlotMinutes()));
        }
        if (startsAt.isBefore(now == null ? Instant.now(clock) : now)) {
            return unavailable("INVALID_RESERVATION_WINDOW", "Reservation cannot start in the past.");
        }
        return new AvailabilityAssessment(true, null, null, 0);
    }

    private boolean isSlotAligned(Instant instant) {
        if (instant.getNano() != 0) {
            return false;
        }
        long epochMinutes = Math.floorDiv(instant.getEpochSecond(), 60);
        return Math.floorMod(epochMinutes, appProperties.getBookingSlotMinutes()) == 0;
    }

    private HoursAssessment validateHours(ParkingLocation location, Instant startsAt, Instant endsAt) {
        List<LocationHours> configuredHours = locationHours.findByLocationIdOrderByDayOfWeek(location.getId());
        if (configuredHours.isEmpty()) {
            return HoursAssessment.OPEN;
        }

        final ZoneId zone;
        try {
            zone = ZoneId.of(location.getTimezone());
        } catch (DateTimeException ex) {
            return HoursAssessment.INVALID;
        }

        Map<DayOfWeek, HoursWindow> windowsByDay = new EnumMap<>(DayOfWeek.class);
        for (LocationHours entry : configuredHours) {
            final DayOfWeek dayOfWeek;
            final LocalTime openTime;
            final LocalTime closeTime;
            try {
                dayOfWeek = DayOfWeek.valueOf(entry.getDayOfWeek().toUpperCase(Locale.ROOT));
                openTime = LocalTime.parse(entry.getOpenTime());
                closeTime = LocalTime.parse(entry.getCloseTime());
            } catch (RuntimeException ex) {
                return HoursAssessment.INVALID;
            }
            if (!closeTime.isAfter(openTime) || windowsByDay.put(dayOfWeek, new HoursWindow(openTime, closeTime)) != null) {
                return HoursAssessment.INVALID;
            }
        }

        ZonedDateTime localStart = startsAt.atZone(zone);
        ZonedDateTime localEnd = endsAt.atZone(zone);
        LocalDate cursor = localStart.toLocalDate();
        LocalDate lastDate = localEnd.minusNanos(1).toLocalDate();
        while (!cursor.isAfter(lastDate)) {
            HoursWindow window = windowsByDay.get(cursor.getDayOfWeek());
            if (window == null) {
                return HoursAssessment.CLOSED;
            }

            ZonedDateTime segmentStart = max(localStart, cursor.atStartOfDay(zone));
            ZonedDateTime segmentEnd = min(localEnd, cursor.plusDays(1).atStartOfDay(zone));
            ZonedDateTime openAt = ZonedDateTime.of(cursor, window.openTime(), zone);
            ZonedDateTime closeAt = ZonedDateTime.of(cursor, window.closeTime(), zone);
            if (segmentStart.isBefore(openAt) || segmentEnd.isAfter(closeAt)) {
                return HoursAssessment.CLOSED;
            }

            cursor = cursor.plusDays(1);
        }

        return HoursAssessment.OPEN;
    }

    private ZonedDateTime max(ZonedDateTime left, ZonedDateTime right) {
        return left.isAfter(right) ? left : right;
    }

    private ZonedDateTime min(ZonedDateTime left, ZonedDateTime right) {
        return left.isBefore(right) ? left : right;
    }

    private AvailabilityAssessment unavailable(String code, String message) {
        return new AvailabilityAssessment(false, code, message, 0);
    }

    public record AvailabilityAssessment(boolean bookable, String code, String message, int availableCapacity) {
    }

    private record HoursWindow(LocalTime openTime, LocalTime closeTime) {
    }

    private record HoursAssessment(boolean valid, boolean open) {
        private static final HoursAssessment OPEN = new HoursAssessment(true, true);
        private static final HoursAssessment CLOSED = new HoursAssessment(true, false);
        private static final HoursAssessment INVALID = new HoursAssessment(false, false);
    }
}
