package com.spotlink.user;

import com.spotlink.core.NotFoundException;
import com.spotlink.operator.OperatorAccountRepository;
import com.spotlink.reservation.ReservationRepository;
import com.spotlink.reservation.ReservationStatus;
import com.spotlink.security.CurrentUserService;
import com.spotlink.support.SupportTicketRepository;
import com.spotlink.vehicle.VehicleRepository;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private final UserRepository users;
    private final UserPreferencesRepository preferences;
    private final OperatorAccountRepository operators;
    private final VehicleRepository vehicles;
    private final ReservationRepository reservations;
    private final SupportTicketRepository supportTickets;
    private final CurrentUserService currentUser;
    private final UserMapper mapper;

    public UserProfileService(
            UserRepository users,
            UserPreferencesRepository preferences,
            OperatorAccountRepository operators,
            VehicleRepository vehicles,
            ReservationRepository reservations,
            SupportTicketRepository supportTickets,
            CurrentUserService currentUser,
            UserMapper mapper) {
        this.users = users;
        this.preferences = preferences;
        this.operators = operators;
        this.vehicles = vehicles;
        this.reservations = reservations;
        this.supportTickets = supportTickets;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public UserDtos.UserProfileDetails mine() {
        return details(currentUser.userId(), true);
    }

    @Transactional(readOnly = true)
    public UserDtos.UserProfileDetails details(UUID userId, boolean privateView) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User was not found."));
        if (!privateView && user.getRegistrationStatus() != RegistrationStatus.ACTIVE) {
            throw new NotFoundException("User was not found.");
        }
        UserPreferences prefs = preferences.findByUserId(userId).orElseGet(() -> defaultPreferences(userId));
        return mapper.toDetails(user, prefs, operators.findByUserId(userId), stats(userId));
    }

    @Transactional
    public UserDtos.UserProfileDetails updateMine(UserDtos.UpdateProfileRequest request) {
        User user = currentUser.user();
        if (request.firstName() != null) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        UserPreferences prefs = preferences.findByUserId(user.getId()).orElseGet(() -> defaultPreferences(user.getId()));
        applyPreferences(prefs, request.preferences());
        if (prefs.getId() == null) {
            preferences.save(prefs);
        }
        return mapper.toDetails(user, prefs, operators.findByUserId(user.getId()), stats(user.getId()));
    }

    @Transactional(readOnly = true)
    public UserDtos.UserProfileDetails publicProfile(UUID userId) {
        if (!userId.equals(currentUser.userId())) {
            throw new AccessDeniedException("Only the current user's profile is exposed in the foundation.");
        }
        return details(userId, true);
    }

    private UserDtos.ProfileStats stats(UUID userId) {
        return new UserDtos.ProfileStats(
                reservations.countByCustomerIdAndStatus(userId, ReservationStatus.COMPLETED),
                vehicles.countByUserId(userId),
                0,
                supportTickets.countByRequesterUserId(userId));
    }

    private UserPreferences defaultPreferences(UUID userId) {
        UserPreferences prefs = new UserPreferences();
        prefs.setUserId(userId);
        return prefs;
    }

    private void applyPreferences(UserPreferences prefs, UserDtos.PartialPreferences update) {
        if (update == null) {
            return;
        }
        if (update.locale() != null) {
            prefs.setLocale(update.locale());
        }
        if (update.marketingOptIn() != null) {
            prefs.setMarketingOptIn(update.marketingOptIn());
        }
        if (update.reservationAlerts() != null) {
            prefs.setReservationAlerts(update.reservationAlerts());
        }
        if (update.paymentAlerts() != null) {
            prefs.setPaymentAlerts(update.paymentAlerts());
        }
        if (update.supportAlerts() != null) {
            prefs.setSupportAlerts(update.supportAlerts());
        }
    }
}
