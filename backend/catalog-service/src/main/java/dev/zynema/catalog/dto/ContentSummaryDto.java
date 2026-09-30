package dev.zynema.catalog.dto;

import dev.zynema.catalog.domain.ContentType;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Lightweight representation used by every list/rail endpoint.
 * Deliberately excludes synopsis and metadata to keep payloads small.
 */
public record ContentSummaryDto(
    UUID id,
    ContentType type,
    String title,
    String slug,
    Integer releaseYear,
    String maturityRating,
    Integer runtimeMinutes,
    String posterUrl,
    String backdropUrl,
    BigDecimal averageRating,
    Integer popularity,
    Set<GenreDto> genres
) {
}
