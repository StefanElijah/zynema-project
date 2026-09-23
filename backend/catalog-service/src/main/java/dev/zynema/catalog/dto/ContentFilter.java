package dev.zynema.catalog.dto;

import java.math.BigDecimal;

/**
 * Read-side filter for catalog listings. Every field is optional; an empty
 * filter lists everything of the requested type.
 *
 * @param genre      genre slug
 * @param yearFrom   inclusive lower bound for release year
 * @param yearTo     inclusive upper bound for release year
 * @param minRating  inclusive lower bound for the average rating
 */
public record ContentFilter(
    String genre,
    Integer yearFrom,
    Integer yearTo,
    BigDecimal minRating
) {
    public static ContentFilter empty() {
        return new ContentFilter(null, null, null, null);
    }

    /**
     * Stable, readable cache key for this filter. Built here instead of in
     * SpEL so the key does not depend on the record's {@code toString()}
     * representation.
     */
    public String cacheKey() {
        return "%s|%s|%s|%s".formatted(
            genre == null ? "*" : genre,
            yearFrom == null ? "*" : yearFrom,
            yearTo == null ? "*" : yearTo,
            minRating == null ? "*" : minRating);
    }
}
