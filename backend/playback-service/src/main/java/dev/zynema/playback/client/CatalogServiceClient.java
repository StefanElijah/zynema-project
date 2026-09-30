package dev.zynema.playback.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * catalog-service: validates that the title exists and gives the session its
 * title and runtime.
 *
 * <p>Called with the viewer's token (relayed by the shared interceptor), so the
 * public read rules of catalog apply unchanged.
 */
@FeignClient(name = "catalog-service", path = "/api/v1/catalog")
public interface CatalogServiceClient {

    @GetMapping("/contents/{id}")
    ContentSummary currentContent(@PathVariable("id") UUID id);

    /**
     * The episode's HLS path. Playback stores the episode id on the session and
     * the catalogue is the only place that knows where its video lives.
     */
    @GetMapping("/episodes/{id}")
    EpisodeSource episode(@PathVariable("id") UUID id);

    record ContentSummary(UUID id, String title, String type, Integer runtimeMinutes, String hlsPath) {
    }

    record EpisodeSource(UUID id, String hlsPath) {
    }
}
