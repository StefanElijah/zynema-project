package dev.zynema.catalog.mapper;

import dev.zynema.catalog.domain.Season;
import dev.zynema.catalog.dto.SeasonDto;
import dev.zynema.catalog.repository.SeasonRepository.SeasonSummaryProjection;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SeasonMapper {

    SeasonDto toDto(SeasonSummaryProjection projection);

    default SeasonDto toDto(Season season, int episodeCount) {
        return new SeasonDto(
            season.getId(),
            season.getSeasonNumber(),
            season.getTitle(),
            season.getSynopsis(),
            season.getReleaseYear(),
            season.getPosterUrl(),
            episodeCount
        );
    }
}
