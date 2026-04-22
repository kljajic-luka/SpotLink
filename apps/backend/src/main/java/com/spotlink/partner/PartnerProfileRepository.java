package com.spotlink.partner;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerProfileRepository extends JpaRepository<PartnerProfile, UUID> {

    Optional<PartnerProfile> findByOperatorId(UUID operatorId);
}
