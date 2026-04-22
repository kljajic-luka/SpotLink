package com.spotlink.reservation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.core.ApiPage;
import com.spotlink.core.AppProperties;
import com.spotlink.core.ConflictException;
import com.spotlink.core.IdempotencyRecord;
import com.spotlink.core.IdempotencyService;
import com.spotlink.core.IdempotencyStatus;
import com.spotlink.core.NotFoundException;
import com.spotlink.location.LocationService;
import com.spotlink.location.ParkingLocation;
import com.spotlink.location.ParkingResource;
import com.spotlink.security.CurrentUserService;
import com.spotlink.vehicle.VehicleProfile;
import com.spotlink.vehicle.VehicleService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private static final List<ReservationStatus> BLOCKING_STATUSES = List.of(
            ReservationStatus.PENDING_PAYMENT,
            ReservationStatus.CONFIRMED,
            ReservationStatus.ACTIVE,
            ReservationStatus.DISPUTED);

    private final ReservationRepository reservations;
    private final LocationService locationService;
    private final VehicleService vehicleService;
    private final CurrentUserService currentUser;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final Clock clock;

    public ReservationService(
            ReservationRepository reservations,
            LocationService locationService,
            VehicleService vehicleService,
            CurrentUserService currentUser,
            IdempotencyService idempotency,
            ObjectMapper objectMapper,
            AppProperties appProperties,
            Clock clock) {
        this.reservations = reservations;
        this.locationService = locationService;
        this.vehicleService = vehicleService;
        this.currentUser = currentUser;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ApiPage<ReservationDtos.ReservationDto> mine(int page, int size) {
        UUID userId = currentUser.userId();
        return ApiPage.from(reservations.findByCustomerIdOrderByStartsAtDesc(userId, PageRequest.of(page, Math.min(size, 100)))
                .map(this::toDto));
    }

    @Transactional(readOnly = true)
    public ReservationDtos.ReservationDto get(UUID reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        requireParticipant(reservation);
        return toDto(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationDtos.ReservationQuote quote(ReservationDtos.ReservationQuoteRequest request) {
        ParkingResource resource = locationService.requireResource(request.resourceId());
        validateWindow(request.startsAt(), request.endsAt());
        validateAvailability(resource.getId(), request.startsAt(), request.endsAt());
        validateVehicleFit(resource, request.vehicleId());

        long subtotal = calculateSubtotal(resource, request.startsAt(), request.endsAt());
        long fees = Math.max(50, Math.round(subtotal * 0.08));
        long discount = request.promoCode() == null || request.promoCode().isBlank() ? 0 : Math.round(subtotal * 0.05);
        return new ReservationDtos.ReservationQuote(
                resource.getId(),
                request.startsAt(),
                request.endsAt(),
                subtotal,
                fees,
                discount,
                subtotal + fees - discount,
                resource.getCurrency(),
                Instant.now(clock).plus(Duration.ofMinutes(appProperties.getQuoteTtlMinutes())));
    }

    @Transactional
    public ReservationDtos.ReservationDto create(ReservationDtos.CreateReservationRequest request) {
        UUID userId = currentUser.userId();
        IdempotencyRecord idempotencyRecord = idempotency.begin(userId, "reservation:create", request.idempotencyKey());
        if (idempotencyRecord.getStatus() == IdempotencyStatus.COMPLETED) {
            return reservations.findByCustomerIdAndIdempotencyKey(userId, request.idempotencyKey())
                    .map(this::toDto)
                    .orElseGet(() -> readCachedReservation(idempotencyRecord));
        }
        if (idempotencyRecord.getStatus() == IdempotencyStatus.PROCESSING && idempotencyRecord.getResponseStatus() != null) {
            throw new ConflictException("IDEMPOTENCY_IN_PROGRESS", "This reservation request is already being processed.");
        }

        try {
            ParkingResource resource = locationService.requireResource(request.resourceId());
            ParkingLocation location = locationService.requireLocation(resource.getLocationId());
            validateWindow(request.startsAt(), request.endsAt());
            validateAvailability(resource.getId(), request.startsAt(), request.endsAt());
            validateVehicleFit(resource, request.vehicleId());
            ReservationDtos.ReservationQuote quote = quote(new ReservationDtos.ReservationQuoteRequest(
                    request.resourceId(),
                    request.vehicleId(),
                    request.startsAt(),
                    request.endsAt(),
                    request.promoCode()));

            Reservation reservation = new Reservation();
            reservation.setCustomerId(userId);
            reservation.setOperatorId(location.getOperatorId());
            reservation.setLocationId(location.getId());
            reservation.setResourceId(resource.getId());
            reservation.setVehicleId(request.vehicleId());
            reservation.setStartsAt(request.startsAt());
            reservation.setEndsAt(request.endsAt());
            reservation.setTimezone(location.getTimezone());
            reservation.setStatus(ReservationStatus.PENDING_PAYMENT);
            reservation.setTotalAmountCents(quote.totalAmountCents());
            reservation.setCurrency(quote.currency());
            reservation.setAccessInstructionsVisible(false);
            reservation.setIdempotencyKey(request.idempotencyKey());
            Reservation saved = reservations.save(reservation);
            ReservationDtos.ReservationDto dto = toDto(saved);
            idempotency.complete(idempotencyRecord, 201, objectMapper.writeValueAsString(dto));
            return dto;
        } catch (RuntimeException | JsonProcessingException ex) {
            idempotency.fail(idempotencyRecord, 409, ex.getMessage());
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Could not serialize idempotency response.", ex);
        }
    }

    @Transactional
    public ReservationDtos.ReservationDto cancel(UUID reservationId, ReservationDtos.CancelReservationRequest request) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        requireParticipant(reservation);
        if (reservation.getStatus() == ReservationStatus.CANCELLED || reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new ConflictException("RESERVATION_NOT_CANCELLABLE", "Reservation cannot be cancelled in its current state.");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setAccessInstructionsVisible(false);
        return toDto(reservation);
    }

    public ReservationDtos.ReservationDto toDto(Reservation reservation) {
        return new ReservationDtos.ReservationDto(
                reservation.getId(),
                reservation.getCustomerId(),
                reservation.getOperatorId(),
                reservation.getLocationId(),
                reservation.getResourceId(),
                reservation.getVehicleId(),
                reservation.getStartsAt(),
                reservation.getEndsAt(),
                reservation.getTimezone(),
                reservation.getStatus(),
                reservation.getTotalAmountCents(),
                reservation.getCurrency(),
                reservation.isAccessInstructionsVisible(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }

    private void validateWindow(Instant startsAt, Instant endsAt) {
        if (!startsAt.isBefore(endsAt)) {
            throw new ConflictException("INVALID_RESERVATION_WINDOW", "Reservation end time must be after start time.");
        }
        if (startsAt.isBefore(Instant.now(clock).minus(Duration.ofMinutes(1)))) {
            throw new ConflictException("INVALID_RESERVATION_WINDOW", "Reservation cannot start in the past.");
        }
    }

    private void validateAvailability(UUID resourceId, Instant startsAt, Instant endsAt) {
        long overlaps = reservations.countOverlaps(resourceId, startsAt, endsAt, BLOCKING_STATUSES);
        if (overlaps > 0) {
            throw new ConflictException("RESOURCE_UNAVAILABLE", "Parking resource is not available for this time window.");
        }
    }

    private void validateVehicleFit(ParkingResource resource, UUID vehicleId) {
        if (vehicleId == null) {
            return;
        }
        VehicleProfile vehicle = vehicleService.requireOwnedEntity(vehicleId, currentUser.userId());
        if (!resource.allowsVehicleType(vehicle.getType())) {
            throw new ConflictException("VEHICLE_NOT_COMPATIBLE", "Vehicle type is not compatible with this parking resource.");
        }
        if (resource.isEvOnly() && !vehicle.isEvCapable()) {
            throw new ConflictException("VEHICLE_NOT_COMPATIBLE", "This resource requires an EV-capable vehicle.");
        }
        if (resource.getMaxHeightMeters() != null && vehicle.getHeightMeters() != null
                && vehicle.getHeightMeters().compareTo(resource.getMaxHeightMeters()) > 0) {
            throw new ConflictException("VEHICLE_NOT_COMPATIBLE", "Vehicle height exceeds this resource limit.");
        }
        if (resource.getMaxLengthMeters() != null && vehicle.getLengthMeters() != null
                && vehicle.getLengthMeters().compareTo(resource.getMaxLengthMeters()) > 0) {
            throw new ConflictException("VEHICLE_NOT_COMPATIBLE", "Vehicle length exceeds this resource limit.");
        }
    }

    private long calculateSubtotal(ParkingResource resource, Instant startsAt, Instant endsAt) {
        long minutes = Duration.between(startsAt, endsAt).toMinutes();
        long hours = Math.max(1, (long) Math.ceil(minutes / 60.0));
        if (resource.getDailyRateCents() != null && hours >= 8) {
            long days = (long) Math.ceil(hours / 24.0);
            return days * resource.getDailyRateCents();
        }
        return hours * resource.getHourlyRateCents();
    }

    private void requireParticipant(Reservation reservation) {
        UUID userId = currentUser.userId();
        if (reservation.getCustomerId().equals(userId)) {
            return;
        }
        throw new AccessDeniedException("Reservation does not belong to the current user.");
    }

    private ReservationDtos.ReservationDto readCachedReservation(IdempotencyRecord record) {
        try {
            return objectMapper.readValue(record.getResponseBody(), ReservationDtos.ReservationDto.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read cached idempotency response.", ex);
        }
    }
}
