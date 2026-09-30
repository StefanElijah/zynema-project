package dev.zynema.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.payment.domain.Subscription;
import dev.zynema.payment.dto.SubscribeRequest;
import dev.zynema.payment.dto.SubscriptionDto;
import dev.zynema.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates subscription writes behind an idempotency key (ADR-0017).
 *
 * <p>Deliberately <strong>not</strong> transactional: the key is reserved,
 * then the business transaction commits, and only then is the key completed
 * with the response. Wrapping all three in one transaction would let a
 * concurrent duplicate read a completed key whose business data had not
 * committed yet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionCommandService {

    private final CurrentAccountService currentAccountService;
    private final SubscriptionService subscriptionService;
    private final IdempotencyService idempotencyService;
    private final PaymentMapper mapper;
    private final ObjectMapper objectMapper;

    /** Status and body to return: identical for a replay and for the first execution. */
    public record IdempotentResponse(int status, SubscriptionDto body) {
    }

    public IdempotentResponse subscribe(String idempotencyKey, SubscribeRequest request, Jwt jwt) {
        UUID userId = currentAccountService.resolveUserId(jwt);
        String requestHash = idempotencyService.hash(request);

        IdempotencyService.Reservation reservation = idempotencyService.reserve(idempotencyKey, requestHash);
        if (reservation.replay().isPresent()) {
            IdempotencyService.CompletedResponse replay = reservation.replay().get();
            log.info("Replaying idempotent response for key '{}'", idempotencyKey);
            return new IdempotentResponse(replay.status(),
                objectMapper.convertValue(replay.body(), SubscriptionDto.class));
        }

        try {
            Subscription created = subscriptionService.createSubscription(userId, request.planId(),
                request.paymentMethod());
            SubscriptionDto body = mapper.toDto(created);
            idempotencyService.complete(idempotencyKey, 201, body);
            return new IdempotentResponse(201, body);
        } catch (RuntimeException ex) {
            // The attempt had no effect: free the key so a corrected retry works.
            idempotencyService.release(idempotencyKey);
            throw ex;
        }
    }

    public SubscriptionDto cancel(UUID subscriptionId, Jwt jwt) {
        UUID userId = currentAccountService.resolveUserId(jwt);
        return mapper.toDto(subscriptionService.cancel(userId, subscriptionId));
    }
}
