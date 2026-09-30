package dev.zynema.catalog.mapper;

import dev.zynema.catalog.domain.Episode;
import dev.zynema.catalog.dto.EpisodeDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EpisodeMapper {

    EpisodeDto toDto(Episode episode);

    List<EpisodeDto> toDtoList(List<Episode> episodes);
}
