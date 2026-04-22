package com.spotlink.user;

import com.spotlink.operator.OperatorAccount;
import java.util.ArrayList;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDtos.UserProfile toProfile(User user, Optional<OperatorAccount> operatorAccount) {
        return new UserDtos.UserProfile(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getBio(),
                new ArrayList<>(user.getRoles()),
                operatorAccount.map(OperatorAccount::getId).orElse(null),
                user.getRegistrationStatus(),
                user.getCreatedAt());
    }

    public UserDtos.UserProfileDetails toDetails(
            User user,
            UserPreferences preferences,
            Optional<OperatorAccount> operatorAccount,
            UserDtos.ProfileStats stats) {
        return new UserDtos.UserProfileDetails(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getBio(),
                new ArrayList<>(user.getRoles()),
                operatorAccount.map(OperatorAccount::getId).orElse(null),
                user.getRegistrationStatus(),
                user.getCreatedAt(),
                stats,
                toPreferences(preferences));
    }

    public UserDtos.UserPreferencesDto toPreferences(UserPreferences preferences) {
        return new UserDtos.UserPreferencesDto(
                preferences.getLocale(),
                preferences.isMarketingOptIn(),
                preferences.isReservationAlerts(),
                preferences.isPaymentAlerts(),
                preferences.isSupportAlerts());
    }
}
