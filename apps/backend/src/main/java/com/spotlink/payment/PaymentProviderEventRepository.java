package com.spotlink.payment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProviderEventRepository extends JpaRepository<PaymentProviderEvent, UUID> {

    List<PaymentProviderEvent> findByPaymentAttemptIdOrderByCreatedAtDesc(UUID paymentAttemptId);

    boolean existsByProviderAndExternalEventId(String provider, String externalEventId);
}
