package dev.zynema.playback.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * user-service: the account that owns the session and the durable watch
 * history. Progress is forwarded on every heartbeat, with the viewer's token,
 * so user-service keeps its own authorization rules.
 */
@FeignClient(name = "user-service", path = "/api/v1/users")
public interface UserServiceClient {

    @GetMapping("/me")
    UserAccount currentUser();

    @PutMapping("/me/profiles/{profileId}/progress")
    void recordProgress(@PathVariable("profileId") UUID profileId, @RequestBody ProgressUpdate progress);

    record UserAccount(UUID id, String email, String displayName) {
    }

    record ProgressUpdate(UUID contentId, UUID episodeId, Integer positionSeconds,
                          Integer durationSeconds, Boolean completed) {
    }
}
