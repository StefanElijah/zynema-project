package dev.zynema.catalog.mapper;

import dev.zynema.catalog.domain.Content;
import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.dto.ContentSummaryDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Entity → DTO mapping. {@code seasons} and {@code credits} are ignored here
 * on purpose: they are loaded and attached by the query service so that a
 * detail request issues exactly the queries it needs (no accidental
 * collection fetches).
 */
@Mapper(componentModel = "spring", uses = GenreMapper.class)
public interface ContentMapper {

    ContentSummaryDto toSummary(Content content);

    @Mapping(target = "seasons", ignore = true)
    @Mapping(target = "credits", ignore = true)
    ContentDetailDto toDetail(Content content);
}
