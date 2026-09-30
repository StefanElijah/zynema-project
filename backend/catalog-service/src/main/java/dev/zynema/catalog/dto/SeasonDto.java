package dev.zynema.catalog.dto;

import java.util.UUID;

public record SeasonDto(
    UUID id,
    Integer seasonNumber,
    String title,
    String synopsis,
    Integer releaseYear,
    String posterUrl,
    Integer episodeCount
) {
}
