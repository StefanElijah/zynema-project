package dev.zynema.catalog.dto;

import java.util.UUID;

/**
 * A saga card: the display metadata plus how many titles it groups.
 */
public record FranchiseSummaryDto(
    UUID id,
    String slug,
    String name,
    String description,
    String coverUrl,
    long itemCount
) {
}
