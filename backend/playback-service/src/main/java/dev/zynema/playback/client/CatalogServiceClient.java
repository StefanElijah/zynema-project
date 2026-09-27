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

    /** Consumer-side slice: only the fields playback uses. */
    record ContentSummary(UUID id, String title, String type, Integer runtimeMinutes) {
    }
}
