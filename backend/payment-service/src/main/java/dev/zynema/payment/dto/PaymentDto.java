package dev.zynema.payment.dto;

import dev.zynema.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentDto(
    UUID id,
    UUID subscriptionId,
    String planCode,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    String method,
    String providerReference,
    Instant paidAt,
    Instant createdAt
) {
}
