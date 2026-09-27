package dev.zynema.catalog.controller;

import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.service.CatalogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Content addressed by id, regardless of type.
 *
 * <p>The type-specific endpoints (`/movies`, `/series`) are slug-based because
 * they back human-facing URLs. Services that store content ids — playback
 * sessions, watch history — need the id-based lookup, and so does the BFF when
 * it enriches a watch-history list with titles.
 */
@RestController
@RequestMapping("/api/v1/catalog/contents")
@RequiredArgsConstructor
@Tag(name = "Catalog — Contents", description = "Look up any title by its id")
public class ContentController {

    private final CatalogQueryService queryService;

    @GetMapping("/{id}")
    @Operation(summary = "Get a title by id (movie or series)")
    public ContentDetailDto getById(@PathVariable UUID id) {
        return queryService.getById(id);
    }
}
