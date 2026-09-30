package dev.zynema.catalog.controller;

import dev.zynema.catalog.dto.GenreDto;
import dev.zynema.catalog.service.CatalogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/genres")
@RequiredArgsConstructor
@Tag(name = "Catalog — Genres", description = "Genre taxonomy")
public class GenreController {

    private final CatalogQueryService queryService;

    @GetMapping
    @Operation(summary = "List all genres", description = "Alphabetically ordered, cached.")
    public List<GenreDto> listGenres() {
        return queryService.listGenres();
    }
}
