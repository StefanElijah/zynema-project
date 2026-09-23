package dev.zynema.catalog.mapper;

import dev.zynema.catalog.domain.Credit;
import dev.zynema.catalog.dto.CreditDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CreditMapper {

    @Mapping(target = "personName", source = "person.name")
    @Mapping(target = "personSlug", source = "person.slug")
    CreditDto toDto(Credit credit);

    List<CreditDto> toDtoList(List<Credit> credits);
}
