package dev.zynema.playback.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;

/**
 * payment-service: what the account is allowed to do. playback enforces
 * {@code maxStreams} as the number of concurrent open sessions.
 */
@FeignClient(name = "payment-service", path = "/api/v1/payments")
public interface PaymentServiceClient {

    @GetMapping("/subscriptions/me/entitlements")
    Entitlements currentEntitlements();

    record Entitlements(boolean active, int maxStreams, String maxQuality, String planCode, Instant validUntil) {
    }
}
