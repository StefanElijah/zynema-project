package dev.zynema.payment.repository;

import dev.zynema.payment.domain.Subscription;
import dev.zynema.payment.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @EntityGraph(attributePaths = {"plan"})
    Optional<Subscription> findWithPlanByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = {"plan"})
    Optional<Subscription> findWithPlanByUserIdAndStatus(UUID userId, SubscriptionStatus status);

    @EntityGraph(attributePaths = {"plan"})
    List<Subscription> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
