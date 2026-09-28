package dev.zynema.payment.dto;

import java.time.Instant;

/**
 * What another service needs to know about an account's plan, without exposing
 * the subscription model. playback-service enforces {@code maxStreams} as the
 * number of concurrent playback sessions.
 *
 * <p>The free tier is the absence of a subscription: {@code active = false}
 * with the most restrictive limits. It is an <em>answer</em>, not a fallback:
 * a caller that cannot reach payment-service must not assume entitlements at
 * all (playback answers 503 instead of granting the free tier by accident).
 */
public record EntitlementsDto(
    boolean active,
    int maxStreams,
    String maxQuality,
    String planCode,
    Instant validUntil
) {
    public static EntitlementsDto freeTier() {
        return new EntitlementsDto(false, 1, "SD", "free", null);
    }
}
