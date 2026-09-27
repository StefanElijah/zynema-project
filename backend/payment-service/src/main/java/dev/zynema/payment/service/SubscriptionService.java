package dev.zynema.payment.service;

import dev.zynema.common.exception.ConflictException;
import dev.zynema.common.exception.ResourceNotFoundException;
import dev.zynema.payment.domain.Payment;
import dev.zynema.payment.domain.PaymentStatus;
import dev.zynema.payment.domain.Plan;
import dev.zynema.payment.domain.Subscription;
import dev.zynema.payment.domain.SubscriptionStatus;
import dev.zynema.payment.repository.PaymentRepository;
import dev.zynema.payment.repository.PlanRepository;
import dev.zynema.payment.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Transactional write operations of the billing domain.
 *
 * <p>Kept apart from the orchestrating command service so the transaction
 * boundary is explicit: the idempotency reservation and the completion of the
 * key are committed independently of the business transaction (ADR-0017).
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /** Simulated PSP: the charge always succeeds. */
    private static final String SIMULATED_METHOD = "simulated_card";

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public Subscription createSubscription(UUID userId, UUID planId, String requestedMethod) {
        Plan plan = planRepository.findById(planId)
            .filter(Plan::isActive)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));

        if (subscriptionRepository.findWithPlanByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE).isPresent()) {
            throw new ConflictException("This account already has an active subscription");
        }

        Instant now = Instant.now();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(periodEnd(now, plan));
        Subscription saved = subscriptionRepository.save(subscription);

        Payment payment = new Payment();
        payment.setSubscription(saved);
        payment.setAmount(plan.getPrice());
        payment.setCurrency(plan.getCurrency());
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setMethod(requestedMethod == null || requestedMethod.isBlank() ? SIMULATED_METHOD : requestedMethod);
        payment.setProviderReference("sim-" + UUID.randomUUID());
        payment.setPaidAt(now);
        paymentRepository.save(payment);

        return saved;
    }

    /**
     * Cancellation keeps access until the paid period ends: the subscription
     * stays ACTIVE with {@code cancel_at_period_end}, which is what users
     * expect and what the entitlements endpoint reflects.
     */
    @Transactional
    public Subscription cancel(UUID userId, UUID subscriptionId) {
        Subscription subscription = subscriptionRepository.findWithPlanByIdAndUserId(subscriptionId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription", subscriptionId));

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new ConflictException("Only an active subscription can be canceled");
        }
        if (subscription.isCancelAtPeriodEnd()) {
            throw new ConflictException("This subscription is already scheduled for cancellation");
        }

        subscription.setCancelAtPeriodEnd(true);
        subscription.setCanceledAt(Instant.now());
        return subscriptionRepository.save(subscription);
    }

    private Instant periodEnd(Instant start, Plan plan) {
        return switch (plan.getBillingPeriod()) {
            case MONTHLY -> start.plus(30, ChronoUnit.DAYS);
            case YEARLY -> start.plus(365, ChronoUnit.DAYS);
        };
    }
}
