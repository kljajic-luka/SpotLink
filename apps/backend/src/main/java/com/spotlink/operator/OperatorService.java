package com.spotlink.operator;

import com.spotlink.admin.AuditService;
import com.spotlink.core.ApiPage;
import com.spotlink.core.AppProperties;
import com.spotlink.inventory.AvailabilityOverrideSource;
import com.spotlink.inventory.InventoryPool;
import com.spotlink.inventory.InventoryPoolService;
import com.spotlink.location.ParkingLocation;
import com.spotlink.location.ParkingLocationRepository;
import com.spotlink.location.ParkingResource;
import com.spotlink.location.ParkingResourceRepository;
import com.spotlink.reservation.Reservation;
import com.spotlink.reservation.ReservationDtos;
import com.spotlink.reservation.ReservationRepository;
import com.spotlink.reservation.ReservationService;
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
            ReservationStatus.PENDING_OPERATOR_CONFIRMATION,
            ReservationStatus.CONFIRMED,
            ReservationStatus.ACTIVE,
            ReservationStatus.DISPUTED);

    private final OperatorAccountRepository operators;
    private final ParkingLocationRepository locations;
    private final ParkingResourceRepository resources;
    private final ReservationRepository reservations;
        private final ReservationService reservationService;
    private final SupportTicketRepository supportTickets;
        private final InventoryPoolService inventoryPools;
    private final CurrentUserService currentUser;
    private final AppProperties appProperties;
        private final AuditService auditService;
    private final Clock clock;

    public OperatorService(
            OperatorAccountRepository operators,
            ParkingLocationRepository locations,
            ParkingResourceRepository resources,
            ReservationRepository reservations,
            ReservationService reservationService,
            SupportTicketRepository supportTickets,
            InventoryPoolService inventoryPools,
            CurrentUserService currentUser,
            AppProperties appProperties,
            AuditService auditService,
            Clock clock) {
        this.operators = operators;
        this.locations = locations;
        this.resources = resources;
        this.reservations = reservations;
        this.reservationService = reservationService;
        this.supportTickets = supportTickets;
        this.inventoryPools = inventoryPools;
        this.currentUser = currentUser;
        this.appProperties = appProperties;
        this.auditService = auditService;
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

        @Transactional(readOnly = true)
        public ApiPage<ReservationDtos.ReservationDto> upcomingBookings(int page, int size) {
                return reservationService.operatorUpcoming(page, size);
        }

        @Transactional(readOnly = true)
        public ReservationDtos.BookingDetailDto bookingDetail(UUID reservationId) {
                return reservationService.operatorDetail(reservationId);
        }

        @Transactional
        public ReservationDtos.ReservationDto checkIn(UUID reservationId, String notes) {
                return reservationService.checkIn(reservationId, notes);
        }

        @Transactional
        public ReservationDtos.ReservationDto markNoShow(UUID reservationId, String reason) {
                return reservationService.markNoShow(reservationId, reason);
        }

        @Transactional
        public ReservationDtos.ReservationDto cancelBooking(UUID reservationId, String reason) {
                return reservationService.cancelAsOperator(reservationId, reason);
        }

        @Transactional
        public ReservationDtos.ReservationDto confirmManualBooking(UUID reservationId, String notes) {
                return reservationService.confirmManualAsOperator(reservationId, notes);
        }

        @Transactional
        public ReservationDtos.ReservationDto rejectManualBooking(UUID reservationId, String reason) {
                return reservationService.rejectManualAsOperator(reservationId, reason);
        }

        @Transactional
        public OperatorDtos.InventoryControlDto pauseSales(UUID resourceId, String reason) {
                ParkingResource resource = requireOwnedResource(resourceId);
                InventoryPool pool = inventoryPools.requireByResourceIdForUpdate(resourceId);
                inventoryPools.pause(pool, currentUser.userId(), AvailabilityOverrideSource.OPERATOR, reason);
                auditService.record(currentUser.userId(), "OPERATOR_PAUSED_SALES", "inventory_pool", pool.getId().toString(), reason);
                return new OperatorDtos.InventoryControlDto(resource.getId(), pool.getId(), true, reason, pool.getBaseCapacity());
        }

        @Transactional
        public OperatorDtos.InventoryControlDto unpauseSales(UUID resourceId) {
                ParkingResource resource = requireOwnedResource(resourceId);
                InventoryPool pool = inventoryPools.requireByResourceIdForUpdate(resourceId);
                inventoryPools.unpause(pool);
                auditService.record(currentUser.userId(), "OPERATOR_UNPAUSED_SALES", "inventory_pool", pool.getId().toString(), null);
                return new OperatorDtos.InventoryControlDto(resource.getId(), pool.getId(), false, null, pool.getBaseCapacity());
        }

        @Transactional
        public OperatorDtos.InventoryControlDto adjustSellableCapacity(UUID resourceId, Integer sellableCapacity, String reason) {
                ParkingResource resource = requireOwnedResource(resourceId);
                InventoryPool pool = inventoryPools.requireByResourceIdForUpdate(resourceId);
                inventoryPools.capCapacity(pool, currentUser.userId(), AvailabilityOverrideSource.OPERATOR, sellableCapacity, reason);
                auditService.record(currentUser.userId(), "OPERATOR_ADJUSTED_CAPACITY", "inventory_pool", pool.getId().toString(), reason);
                return new OperatorDtos.InventoryControlDto(resource.getId(), pool.getId(), pool.isPaused(), pool.getPauseReason(), pool.getBaseCapacity());
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

    private ParkingResource requireOwnedResource(UUID resourceId) {
        ParkingResource resource = resources.findById(resourceId)
                .orElseThrow(() -> new AccessDeniedException("Parking resource was not found."));
        ParkingLocation location = locations.findById(resource.getLocationId())
                .orElseThrow(() -> new AccessDeniedException("Parking location was not found."));
        if (!location.getOperatorId().equals(currentOperator().getId())) {
            throw new AccessDeniedException("Parking resource does not belong to the current operator.");
        }
        return resource;
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
