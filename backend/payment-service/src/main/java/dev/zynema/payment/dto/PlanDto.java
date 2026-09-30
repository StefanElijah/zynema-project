package dev.zynema.payment.dto;

import dev.zynema.payment.domain.BillingPeriod;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record PlanDto(
    UUID id,
    String code,
    String name,
    String description,
    BigDecimal price,
    String currency,
    BillingPeriod billingPeriod,
    Integer maxStreams,
    String maxQuality,
    Map<String, Object> features
) {
}
