package dev.zynema.catalog.dto;

import dev.zynema.catalog.domain.CreditRole;

public record CreditDto(
    String personName,
    String personSlug,
    CreditRole role,
    String characterName
) {
}
