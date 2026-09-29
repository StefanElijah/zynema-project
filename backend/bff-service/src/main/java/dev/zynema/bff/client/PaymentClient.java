package dev.zynema.bff.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * payment-service reads: the current plan and what it entitles the account to.
 *
 * <p>Both are optional context for the BFF: a visitor without a subscription
 * gets a 404 (a valid answer, not a failure) and an unavailable payment-service
 * degrades the section instead of failing the page.
 */
@Component
public class PaymentClient {

    private final WebClient webClient;

    public PaymentClient(@Qualifier("paymentWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<Subscription> currentSubscription() {
        return webClient.get()
            .uri("/api/v1/payments/subscriptions/me")
            .retrieve()
            .bodyToMono(Subscription.class);
    }

    public Mono<Entitlements> entitlements() {
        return webClient.get()
            .uri("/api/v1/payments/subscriptions/me/entitlements")
            .retrieve()
            .bodyToMono(Entitlements.class);
    }

    // ───────────────────────── checkout (Fase 8) ────────────────────────────

    /** The pricing page is public; the plan list is the shop window's price tag. */
    public Mono<List<Plan>> plans() {
        return webClient.get()
            .uri("/api/v1/payments/plans")
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<Plan>>() {
            });
    }

    /**
     * Subscribing is not idempotent by itself: the SPA's key travels unchanged
     * so a retry after a timeout returns the original 201 instead of charging
     * twice (ADR-0017).
     */
    public Mono<ResponseEntity<Subscription>> subscribe(
        String idempotencyKey, UUID planId, String paymentMethod) {
        return webClient.post()
            .uri("/api/v1/payments/subscriptions")
            .header("Idempotency-Key", idempotencyKey)
            .bodyValue(new SubscribeBody(planId, paymentMethod))
            .retrieve()
            // 201 on a new subscription, 200 when the key is replayed: the
            // status is part of the answer the client is entitled to.
            .toEntity(Subscription.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SubscribeBody(UUID planId, String paymentMethod) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Subscription(
        UUID id,
        Plan plan,
        String status,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd
    ) {
    }

    /**
     * The plan as the pricing page needs it. {@code code}/{@code name} were the
     * only fields the account view needed; the checkout screen added the rest.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Plan(
        UUID id,
        String code,
        String name,
        String description,
        java.math.BigDecimal price,
        String currency,
        String billingPeriod,
        Integer maxStreams,
        String maxQuality
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entitlements(
        boolean active,
        int maxStreams,
        String maxQuality,
        String planCode,
        Instant validUntil
    ) {
    }
}
