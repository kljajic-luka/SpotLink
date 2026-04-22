package com.spotlink.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Neblokujuci lookup – koristi se za revoke i read-only operacije. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Blokujuci lookup za rotaciju tokena (PESSIMISTIC_WRITE).
     * Sprjecava race condition kod istovremenih rotate poziva sa istim tokenom:
     * samo jedan poziv moze dobiti zakljucani red, drugi mora cekati i potom
     * naci token vec opozvat (revokedAt != null), sto aktivira theft detection.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, Instant now);
}
