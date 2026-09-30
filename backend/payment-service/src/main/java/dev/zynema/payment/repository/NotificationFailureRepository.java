package dev.zynema.payment.repository;

import dev.zynema.payment.domain.NotificationFailure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationFailureRepository extends JpaRepository<NotificationFailure, UUID> {

    Optional<NotificationFailure> findFirstBySubscriptionIdOrderByOccurredAtDesc(UUID subscriptionId);
}
