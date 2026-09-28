package dev.zynema.bff.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Subscription(
        UUID id,
        Plan plan,
        String status,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Plan(String code, String name) {
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
