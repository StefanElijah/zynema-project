package dev.zynema.catalog.controller;

import dev.zynema.catalog.dto.EpisodeDto;
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
 * Episodes addressed by id.
 *
 * <p>Series-oriented endpoints live under the series slug (that is the
 * human-facing URL), but playback sessions store an episode id, so the
 * id-addressed read exists for the same reason {@code /contents/{id}} does.
 * The payload includes the HLS path the pipeline wrote.
 */
@RestController
@RequestMapping("/api/v1/catalog/episodes")
@RequiredArgsConstructor
@Tag(name = "Catalog — Episodes", description = "Look up an episode by id")
public class EpisodeController {

    private final CatalogQueryService queryService;

    @GetMapping("/{id}")
    @Operation(summary = "Get an episode by id, including its HLS path when it has one")
    public EpisodeDto getById(@PathVariable UUID id) {
        return queryService.getEpisodeById(id);
    }
}
