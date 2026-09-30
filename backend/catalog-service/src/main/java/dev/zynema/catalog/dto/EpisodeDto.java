package dev.zynema.catalog.dto;

import java.time.LocalDate;
import java.util.UUID;

public record EpisodeDto(
    UUID id,
    Integer episodeNumber,
    String title,
    String synopsis,
    Integer runtimeMinutes,
    LocalDate releaseDate,
    String stillUrl,
    String hlsPath
) {
}
