package dev.zynema.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Payload to start a subscription. Idempotency is handled by the header. */
public record SubscribeRequest(

    @NotNull(message = "planId is required")
    UUID planId,

    @Size(max = 40, message = "paymentMethod must be at most 40 characters")
    String paymentMethod
) {
}
