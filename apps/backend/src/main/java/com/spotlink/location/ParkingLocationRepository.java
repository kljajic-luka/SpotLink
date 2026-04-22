package com.spotlink.location;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParkingLocationRepository extends JpaRepository<ParkingLocation, UUID> {

    List<ParkingLocation> findByOperatorIdAndActiveTrueOrderByName(UUID operatorId);

    long countByOperatorIdAndActiveTrue(UUID operatorId);

    long countByActiveTrue();

    @Query("""
            select l from ParkingLocation l
            where l.active = true
              and (:query is null
                   or lower(l.name) like lower(concat('%', :query, '%'))
                   or lower(l.address.city) like lower(concat('%', :query, '%'))
                   or lower(l.address.formattedAddress) like lower(concat('%', :query, '%')))
            """)
    Page<ParkingLocation> search(@Param("query") String query, Pageable pageable);

    /**
     * Tekst + bounding-box filter na DB nivou.
     * Haversine precizno filtriranje se primenjuje naknadno u memoriji.
     * Svi parametri za koordinate mogu biti null – tada se koordinatni filter preskace.
     */
    @Query("""
            select l from ParkingLocation l
            where l.active = true
              and (:query is null
                   or lower(l.name) like lower(concat('%', :query, '%'))
                   or lower(l.address.city) like lower(concat('%', :query, '%'))
                   or lower(l.address.formattedAddress) like lower(concat('%', :query, '%')))
              and (:minLat is null or l.coordinates.latitude >= :minLat)
              and (:maxLat is null or l.coordinates.latitude <= :maxLat)
              and (:minLon is null or l.coordinates.longitude >= :minLon)
              and (:maxLon is null or l.coordinates.longitude <= :maxLon)
            """)
    List<ParkingLocation> searchCandidates(
            @Param("query") String query,
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLon") BigDecimal minLon,
            @Param("maxLon") BigDecimal maxLon);
}
