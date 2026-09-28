package dev.zynema.bff.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One poster in a rail. The frontend never has to know which service the
 * fields came from.
 */
public record TitleCard(
    UUID id,
    ContentKind type,
    String slug,
    String title,
    String posterUrl,
    String backdropUrl,
    Integer releaseYear,
    String maturityRating,
    BigDecimal averageRating,
    List<GenreRef> genres
) {
}
