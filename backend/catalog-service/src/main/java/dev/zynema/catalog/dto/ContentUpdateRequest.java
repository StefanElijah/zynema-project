package dev.zynema.catalog.dto;

import dev.zynema.catalog.domain.ContentStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Payload for updating a catalog entry (admin API).
 * {@code slug} is intentionally immutable: public URLs depend on it.
 */
public record ContentUpdateRequest(

    @NotBlank(message = "title is required")
    @Size(max = 255, message = "title must be at most 255 characters")
    String title,

    @Size(max = 255, message = "originalTitle must be at most 255 characters")
    String originalTitle,

    String synopsis,

    @Size(max = 255, message = "tagline must be at most 255 characters")
    String tagline,

    @Min(value = 1888, message = "releaseYear must be 1888 or later")
    @Max(value = 2200, message = "releaseYear must be 2200 or earlier")
    Integer releaseYear,

    @Size(max = 10, message = "maturityRating must be at most 10 characters")
    String maturityRating,

    @Min(value = 1, message = "runtimeMinutes must be positive")
    @Max(value = 1200, message = "runtimeMinutes must be at most 1200")
    Integer runtimeMinutes,

    @Size(max = 500, message = "posterUrl must be at most 500 characters")
    String posterUrl,

    @Size(max = 500, message = "backdropUrl must be at most 500 characters")
    String backdropUrl,

    @Size(max = 500, message = "trailerUrl must be at most 500 characters")
    String trailerUrl,

    @DecimalMin(value = "0.0", message = "averageRating must be between 0 and 10")
    @DecimalMax(value = "10.0", message = "averageRating must be between 0 and 10")
    BigDecimal averageRating,

    @Min(value = 0, message = "popularity must not be negative")
    Integer popularity,

    ContentStatus status,

    Set<@NotBlank String> genreSlugs,

    Map<String, Object> metadata
) {
}
