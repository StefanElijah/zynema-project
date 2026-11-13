package dev.zynema.catalog.dto;

import java.util.List;
import java.util.UUID;

/**
 * A saga and its titles, already ordered by {@code franchise_contents.position}.
 */
public record FranchiseDetailDto(
    UUID id,
    String slug,
    String name,
    String description,
    String coverUrl,
    List<ContentSummaryDto> items
) {
}
