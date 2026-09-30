package dev.zynema.catalog.controller;

import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.dto.EpisodeDto;
import dev.zynema.catalog.dto.HlsPathRequest;
import dev.zynema.catalog.service.CatalogCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Where the video pipeline publishes its work.
 *
 * <p>Separate from the content CRUD on purpose: these two endpoints are the
 * only thing the transcoder needs, they take a machine identity
 * ({@code zynema-media} with the {@code content-manager} role), and they are
 * the seam where a future event-driven pipeline would plug in.
 */
@RestController
@RequestMapping("/api/v1/catalog/admin")
@RequiredArgsConstructor
@Tag(name = "Catalog — Admin", description = "Video pipeline callbacks")
public class PlaybackSourceAdminController {

    private final CatalogCommandService commandService;

    @PutMapping("/contents/{id}/hls-path")
    @Operation(summary = "Publish the HLS master playlist of a movie or series")
    public ContentDetailDto setContentHlsPath(@PathVariable UUID id,
                                              @Valid @RequestBody HlsPathRequest request) {
        return commandService.setContentHlsPath(id, request.hlsPath());
    }

    @PutMapping("/episodes/{id}/hls-path")
    @Operation(summary = "Publish the HLS master playlist of one episode")
    public EpisodeDto setEpisodeHlsPath(@PathVariable UUID id,
                                        @Valid @RequestBody HlsPathRequest request) {
        return commandService.setEpisodeHlsPath(id, request.hlsPath());
    }
}
