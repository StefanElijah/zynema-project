package dev.zynema.payment.repository;

import dev.zynema.payment.domain.Payment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Payment history of an account. Joins through the subscription, which is
     * the only thing that ties a payment to a user.
     */
    @EntityGraph(attributePaths = {"subscription", "subscription.plan"})
    @Query("""
        SELECT p FROM Payment p
        WHERE p.subscription.userId = :userId
        ORDER BY p.createdAt DESC
        """)
    List<Payment> findHistoryByUserId(@Param("userId") UUID userId, Pageable pageable);
}
