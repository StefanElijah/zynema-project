package dev.zynema.payment.dto;

import java.time.Instant;

/**
 * What another service needs to know about an account's plan, without exposing
 * the subscription model. playback-service enforces {@code maxStreams} as the
 * number of concurrent playback sessions.
 *
 * <p>The free tier is the absence of a subscription: {@code active = false}
 * with the most restrictive limits. A degraded dependency therefore falls back
 * to <em>fewer</em> rights, never to more.
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
