package dev.zynema.bff.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything the account screen needs in one round trip: who the token says
 * you are, your local profile, and what you are paying for.
 *
 * <p>{@code identity} is built from the token itself — zero downstream calls —
 * and {@code user}/{@code subscription} are merged from user-service and
 * payment-service. The SPA used to make two calls and had no idea about the
 * subscription; now it asks once and gets a consistent picture.
 */
public record AccountView(
    Identity identity,
    User user,
    Subscription subscription,
    Entitlements entitlements,
    List<WebSection> degraded
) {

    public record Identity(
        String subject,
        String username,
        String email,
        Boolean emailVerified,
        String fullName,
        List<String> roles
    ) {
    }

    /**
     * The local account. {@code profiles} is included because every
     * personalized screen needs a profile id, and it saves the SPA a second
     * call before it can ask for "continue watching".
     */
    public record User(
        UUID id,
        String email,
        String displayName,
        String preferredLanguage,
        List<Profile> profiles
    ) {
    }

    public record Profile(UUID id, String name, boolean kids, String language) {
    }

    /**
     * The current plan, or {@code null} when there is none — which is a valid
     * answer, not an error.
     */
    public record Subscription(
        UUID id,
        String planCode,
        String planName,
        String status,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd
    ) {
    }

    public record Entitlements(
        boolean active,
        int maxStreams,
        String maxQuality,
        String planCode,
        Instant validUntil
    ) {
    }
}
