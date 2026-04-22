package com.spotlink.location;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailabilityExceptionRepository extends JpaRepository<AvailabilityException, UUID> {

    List<AvailabilityException> findByLocationIdOrderByStartsAt(UUID locationId);

    @Query("""
            select count(e) from AvailabilityException e
            where e.locationId = :locationId
              and e.startsAt < :endsAt
              and e.endsAt > :startsAt
            """)
    long countOverlapping(
            @Param("locationId") UUID locationId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt);
}
