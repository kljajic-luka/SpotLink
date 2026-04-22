package com.spotlink.operator;

import com.spotlink.core.AppProperties;
import com.spotlink.location.ParkingLocationRepository;
import com.spotlink.location.ParkingResource;
import com.spotlink.location.ParkingResourceRepository;
import com.spotlink.reservation.Reservation;
import com.spotlink.reservation.ReservationRepository;
import com.spotlink.reservation.ReservationStatus;
import com.spotlink.security.CurrentUserService;
import com.spotlink.support.SupportTicketRepository;
import com.spotlink.support.SupportTicketStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorService {

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(
            ReservationStatus.PENDING_PAYMENT,
            ReservationStatus.CONFIRMED,
            ReservationStatus.ACTIVE,
            ReservationStatus.DISPUTED);

    private final OperatorAccountRepository operators;
    private final ParkingLocationRepository locations;
    private final ParkingResourceRepository resources;
    private final ReservationRepository reservations;
    private final SupportTicketRepository supportTickets;
    private final CurrentUserService currentUser;
    private final AppProperties appProperties;
    private final Clock clock;

    public OperatorService(
            OperatorAccountRepository operators,
            ParkingLocationRepository locations,
            ParkingResourceRepository resources,
            ReservationRepository reservations,
            SupportTicketRepository supportTickets,
            CurrentUserService currentUser,
            AppProperties appProperties,
            Clock clock) {
        this.operators = operators;
        this.locations = locations;
        this.resources = resources;
        this.reservations = reservations;
        this.supportTickets = supportTickets;
        this.currentUser = currentUser;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OperatorDtos.OperatorAccountDto me() {
        return toDto(currentOperator());
    }

    @Transactional(readOnly = true)
    public OperatorDtos.OperatorDashboardSummary summary() {
        OperatorAccount operator = currentOperator();
        var operatorLocations = locations.findByOperatorIdAndActiveTrueOrderByName(operator.getId());
        var locationIds = operatorLocations.stream().map(location -> location.getId()).toList();
        long activeResources = locationIds.isEmpty() ? 0 : resources.countByLocationIdInAndActiveTrue(locationIds);
        Instant start = LocalDate.now(clock).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = start.plusSeconds(86400);
        long reservationsToday = reservations.countByOperatorIdAndStartsAtLessThanAndEndsAtGreaterThan(operator.getId(), end, start);
        return new OperatorDtos.OperatorDashboardSummary(
                operatorLocations.size(),
                activeResources,
                reservationsToday,
                activeResources == 0 ? 0 : Math.min(1.0, (double) reservationsToday / activeResources),
                supportTickets.countByStatusIn(List.of(SupportTicketStatus.OPEN, SupportTicketStatus.WAITING_ON_OPERATOR)),
                0,
                appProperties.getDefaultCurrency());
    }

    @Transactional(readOnly = true)
    public List<OperatorDtos.OperatorResourceHealth> resourceHealth() {
        OperatorAccount operator = currentOperator();
        var operatorLocations = locations.findByOperatorIdAndActiveTrueOrderByName(operator.getId());
        var locationIds = operatorLocations.stream().map(location -> location.getId()).toList();
        if (locationIds.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now(clock);
        return resources.findByLocationIdInAndActiveTrue(locationIds).stream()
                .map(resource -> toHealth(resource, now))
                .toList();
    }

    private OperatorDtos.OperatorResourceHealth toHealth(ParkingResource resource, Instant now) {
        UUID currentReservationId = reservations
                .findFirstByResourceIdAndStatusInAndStartsAtLessThanAndEndsAtGreaterThan(
                        resource.getId(), ACTIVE_STATUSES, now, now)
                .map(Reservation::getId)
                .orElse(null);
        Instant nextReservationAt = reservations
                .findFirstByResourceIdAndStatusInAndStartsAtAfterOrderByStartsAtAsc(resource.getId(), ACTIVE_STATUSES, now)
                .map(Reservation::getStartsAt)
                .orElse(null);
        return new OperatorDtos.OperatorResourceHealth(
                resource.getId(),
                resource.getLabel(),
                resource.isActive(),
                currentReservationId,
                nextReservationAt,
                resource.isActive() ? null : "Resource is inactive");
    }

    private OperatorAccount currentOperator() {
        return operators.findByUserId(currentUser.userId())
                .orElseThrow(() -> new AccessDeniedException("Operator account is required."));
    }

    private OperatorDtos.OperatorAccountDto toDto(OperatorAccount operator) {
        return new OperatorDtos.OperatorAccountDto(
                operator.getId(),
                operator.getDisplayName(),
                operator.getLegalName(),
                operator.getSupportEmail(),
                operator.isActive(),
                operator.getCreatedAt());
    }
}
