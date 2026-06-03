package com.spotlink.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByDeviceToken(String deviceToken);

    Optional<DeviceToken> findByDeviceTokenAndUserIdAndPlatform(String deviceToken, UUID userId, DevicePlatform platform);

    List<DeviceToken> findByUserId(UUID userId);

    List<DeviceToken> findByUserIdAndPlatformAndActiveTrue(UUID userId, DevicePlatform platform);
}
