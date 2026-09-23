package dev.zynema.catalog.mapper;

import dev.zynema.catalog.domain.Genre;
import dev.zynema.catalog.dto.GenreDto;
import org.mapstruct.Mapper;

import java.util.LinkedHashSet;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface GenreMapper {

    GenreDto toDto(Genre genre);

    default Set<GenreDto> toDtoSet(Set<Genre> genres) {
        if (genres == null) {
            return Set.of();
        }
        Set<GenreDto> result = new LinkedHashSet<>();
        genres.forEach(genre -> result.add(toDto(genre)));
        return result;
    }
}
