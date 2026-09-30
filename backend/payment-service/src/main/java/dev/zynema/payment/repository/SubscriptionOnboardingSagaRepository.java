package dev.zynema.payment.repository;

import dev.zynema.payment.domain.SubscriptionOnboardingSaga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionOnboardingSagaRepository extends JpaRepository<SubscriptionOnboardingSaga, UUID> {

    Optional<SubscriptionOnboardingSaga> findByNotificationId(UUID notificationId);
}
