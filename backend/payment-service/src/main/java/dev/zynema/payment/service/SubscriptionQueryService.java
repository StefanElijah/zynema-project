package dev.zynema.payment.service;

import dev.zynema.common.exception.ResourceNotFoundException;
import dev.zynema.payment.domain.Plan;
import dev.zynema.payment.domain.Subscription;
import dev.zynema.payment.domain.SubscriptionStatus;
import dev.zynema.payment.dto.EntitlementsDto;
import dev.zynema.payment.dto.PaymentDto;
import dev.zynema.payment.dto.SubscriptionDto;
import dev.zynema.payment.mapper.PaymentMapper;
import dev.zynema.payment.repository.PaymentRepository;
import dev.zynema.payment.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read side of the billing domain, always scoped to the caller's account. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final CurrentAccountService currentAccountService;
    private final PaymentMapper mapper;

    public SubscriptionDto currentSubscription(Jwt jwt) {
        UUID userId = currentAccountService.resolveUserId(jwt);
        Subscription subscription = activeSubscription(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Active subscription for account", userId));
        return mapper.toDto(subscription);
    }

    public List<SubscriptionDto> subscriptionHistory(Jwt jwt) {
        UUID userId = currentAccountService.resolveUserId(jwt);
        return mapper.toSubscriptionDtoList(subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    /**
     * What other services need: playback enforces {@code maxStreams} as the
     * number of concurrent sessions. Without a subscription the answer is the
     * free tier, never an escalation.
     */
    public EntitlementsDto entitlements(Jwt jwt) {
        UUID userId = currentAccountService.resolveUserId(jwt);
        Instant now = Instant.now();
        return activeSubscription(userId)
            .filter(subscription -> subscription.grantsAccessAt(now))
            .map(subscription -> {
                Plan plan = subscription.getPlan();
                return new EntitlementsDto(true, plan.getMaxStreams(), plan.getMaxQuality(),
                    plan.getCode(), subscription.getCurrentPeriodEnd());
            })
            .orElseGet(EntitlementsDto::freeTier);
    }

    public List<PaymentDto> paymentHistory(Jwt jwt, int limit) {
        UUID userId = currentAccountService.resolveUserId(jwt);
        int size = limit < 1 ? 20 : Math.min(limit, MAX_PAGE_SIZE);
        return mapper.toPaymentDtoList(
            paymentRepository.findHistoryByUserId(userId, PageRequest.of(0, size)));
    }

    private Optional<Subscription> activeSubscription(UUID userId) {
        return subscriptionRepository.findWithPlanByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
    }
}
