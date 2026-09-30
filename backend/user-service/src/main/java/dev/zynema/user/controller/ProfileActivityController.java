package dev.zynema.user.controller;

import dev.zynema.user.dto.WatchHistoryDto;
import dev.zynema.user.dto.WatchProgressRequest;
import dev.zynema.user.dto.WatchlistAddRequest;
import dev.zynema.user.dto.WatchlistItemDto;
import dev.zynema.user.service.UserCommandService;
import dev.zynema.user.service.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Watchlist and watch progress for a viewer profile.
 *
 * <p>Profiles are addressed through their owning account so an account can
 * only ever touch its own data.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/profiles/{profileId}")
@RequiredArgsConstructor
@Validated
@Tag(name = "Profile activity", description = "Watchlist and watch progress")
public class ProfileActivityController {

    private final UserQueryService queryService;
    private final UserCommandService commandService;

    // ──────────────────────────── watchlist ───────────────────────────

    @GetMapping("/watchlist")
    @Operation(summary = "List 'My list' entries, most recently added first")
    public List<WatchlistItemDto> listWatchlist(@PathVariable UUID userId, @PathVariable UUID profileId) {
        return queryService.listWatchlist(userId, profileId);
    }

    @PostMapping("/watchlist")
    @Operation(summary = "Add a title to 'My list'", description = "Idempotent: adding twice keeps one entry.")
    public WatchlistItemDto addToWatchlist(
        @PathVariable UUID userId,
        @PathVariable UUID profileId,
        @Valid @RequestBody WatchlistAddRequest request
    ) {
        return commandService.addToWatchlist(userId, profileId, request);
    }

    @DeleteMapping("/watchlist/{contentId}")
    @Operation(summary = "Remove a title from 'My list'")
    public void removeFromWatchlist(
        @PathVariable UUID userId,
        @PathVariable UUID profileId,
        @PathVariable UUID contentId
    ) {
        commandService.removeFromWatchlist(userId, profileId, contentId);
    }

    // ──────────────────────────── progress ────────────────────────────

    @GetMapping("/history")
    @Operation(summary = "List watch history, most recent first")
    public List<WatchHistoryDto> listHistory(
        @PathVariable UUID userId,
        @PathVariable UUID profileId,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return queryService.listHistory(userId, profileId, limit);
    }

    @GetMapping("/continue-watching")
    @Operation(summary = "List in-progress titles, most recent first")
    public List<WatchHistoryDto> listContinueWatching(
        @PathVariable UUID userId,
        @PathVariable UUID profileId,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return queryService.listContinueWatching(userId, profileId, limit);
    }

    @PutMapping("/progress")
    @Operation(summary = "Record playback progress",
               description = "Idempotent upsert keyed by profile + content (+ episode). Marks the title completed at 95%.")
    public WatchHistoryDto recordProgress(
        @PathVariable UUID userId,
        @PathVariable UUID profileId,
        @Valid @RequestBody WatchProgressRequest request
    ) {
        return commandService.recordProgress(userId, profileId, request);
    }
}
