package com.spotlink.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthLockoutStateRepository extends JpaRepository<AuthLockoutState, UUID> {

    Optional<AuthLockoutState> findByIdentifierHash(String identifierHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from AuthLockoutState state where state.identifierHash = :identifierHash")
    Optional<AuthLockoutState> findByIdentifierHashForUpdate(@Param("identifierHash") String identifierHash);

    void deleteByUserId(UUID userId);

    @Modifying
    @Query("""
            delete from AuthLockoutState state
            where state.lastFailedAt < :cutoff
              and (state.lockedUntil is null or state.lockedUntil < :now)
            """)
    int deleteExpired(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
