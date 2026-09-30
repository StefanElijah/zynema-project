package dev.zynema.payment.client;

import java.util.UUID;

/**
 * The slice of user-service's response that payment needs.
 *
 * <p>A minimal consumer-side contract: unknown fields in the response are
 * ignored, so user-service can grow without breaking this client, while a
 * change to {@code id} fails fast.
 */
public record UserAccountDto(UUID id, String email, String displayName) {
}
