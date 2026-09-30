package dev.zynema.catalog.dto;

import dev.zynema.catalog.domain.ContentStatus;
import dev.zynema.catalog.domain.ContentType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Full representation for the detail page: everything in
 * {@link ContentSummaryDto} plus synopsis, assets, metadata, seasons and
 * credits. {@code seasons} is empty for movies.
 */
@Builder(toBuilder = true)
public record ContentDetailDto(
    UUID id,
    ContentType type,
    String title,
    String originalTitle,
    String slug,
    String synopsis,
    String tagline,
    Integer releaseYear,
    String maturityRating,
    Integer runtimeMinutes,
    String posterUrl,
    String backdropUrl,
    String trailerUrl,
    String hlsPath,
    BigDecimal averageRating,
    Integer popularity,
    ContentStatus status,
    Map<String, Object> metadata,
    Set<GenreDto> genres,
    List<SeasonDto> seasons,
    List<CreditDto> credits,
    Instant createdAt,
    Instant updatedAt
) {
}
