package com.spotlink.reservation;

import com.spotlink.core.ConflictException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ReservationStateMachine {

    private final Map<ReservationStatus, Set<ReservationStatus>> transitions = new EnumMap<>(ReservationStatus.class);

    public ReservationStateMachine() {
        transitions.put(ReservationStatus.DRAFT, EnumSet.of(
                ReservationStatus.PENDING_PAYMENT,
                ReservationStatus.PENDING_OPERATOR_CONFIRMATION,
                ReservationStatus.CONFIRMED,
                ReservationStatus.CANCELLED));
        transitions.put(ReservationStatus.PENDING_PAYMENT, EnumSet.of(
                ReservationStatus.CONFIRMED,
                ReservationStatus.PENDING_OPERATOR_CONFIRMATION,
                ReservationStatus.CANCELLED,
                ReservationStatus.EXPIRED));
        transitions.put(ReservationStatus.PENDING_OPERATOR_CONFIRMATION, EnumSet.of(
                ReservationStatus.CONFIRMED,
                ReservationStatus.CANCELLED,
                ReservationStatus.REJECTED));
        transitions.put(ReservationStatus.CONFIRMED, EnumSet.of(
                ReservationStatus.ACTIVE,
                ReservationStatus.CANCELLED,
                ReservationStatus.COMPLETED,
                ReservationStatus.DISPUTED,
                ReservationStatus.NO_SHOW));
        transitions.put(ReservationStatus.ACTIVE, EnumSet.of(
                ReservationStatus.COMPLETED,
                ReservationStatus.CANCELLED,
                ReservationStatus.DISPUTED,
                ReservationStatus.NO_SHOW));
        transitions.put(ReservationStatus.COMPLETED, EnumSet.of(ReservationStatus.DISPUTED));
        transitions.put(ReservationStatus.CANCELLED, EnumSet.noneOf(ReservationStatus.class));
        transitions.put(ReservationStatus.REJECTED, EnumSet.noneOf(ReservationStatus.class));
        transitions.put(ReservationStatus.EXPIRED, EnumSet.noneOf(ReservationStatus.class));
        transitions.put(ReservationStatus.DISPUTED, EnumSet.of(
                ReservationStatus.CANCELLED,
                ReservationStatus.COMPLETED));
        transitions.put(ReservationStatus.NO_SHOW, EnumSet.of(ReservationStatus.COMPLETED));
    }

    public void assertTransitionAllowed(ReservationStatus current, ReservationStatus target) {
        if (current == target) {
            return;
        }
        Set<ReservationStatus> allowedTargets = transitions.getOrDefault(current, Set.of());
        if (!allowedTargets.contains(target)) {
            throw new ConflictException(
                    "INVALID_RESERVATION_TRANSITION",
                    "Reservation cannot move from " + current + " to " + target + ".");
        }
    }
}
