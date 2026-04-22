package com.spotlink.operator;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorAccountRepository extends JpaRepository<OperatorAccount, UUID> {

    Optional<OperatorAccount> findByUserId(UUID userId);

    long countByActiveTrue();
}
