package dev.zynema.user.controller;

import dev.zynema.user.dto.ProfileCreateRequest;
import dev.zynema.user.dto.ProfileDto;
import dev.zynema.user.dto.UserDto;
import dev.zynema.user.dto.WatchHistoryDto;
import dev.zynema.user.dto.WatchProgressRequest;
import dev.zynema.user.dto.WatchlistAddRequest;
import dev.zynema.user.dto.WatchlistItemDto;
import dev.zynema.user.service.CurrentUserService;
import dev.zynema.user.service.UserCommandService;
import dev.zynema.user.service.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Self-service API.
 *
 * <p>There is no account id in any path: the caller's identity comes from the
 * validated token (JIT-provisioned on first use). Addressing another account
 * is therefore not expressible — the class of bug that ownership checks exist
 * to prevent simply cannot happen here.
 *
 * <p>The id-addressed admin API lives in {@code UserController},
 * {@code ProfileController} and {@code ProfileActivityController}.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Validated
@Tag(name = "Me", description = "Self-service: own account, profiles, watchlist and progress")
public class MeController {

    private final CurrentUserService currentUserService;
    private final UserQueryService queryService;
    private final UserCommandService commandService;

    // ────────────────────────────── account ───────────────────────────

    @GetMapping
    @Operation(summary = "Get my account",
               description = "Creates the local account on first call, linking it by verified email.")
    public UserDto me(@AuthenticationPrincipal Jwt jwt) {
        return currentUserService.resolve(jwt);
    }

    // ────────────────────────────── profiles ──────────────────────────

    @GetMapping("/profiles")
    @Operation(summary = "List my viewer profiles")
    public List<ProfileDto> listProfiles(@AuthenticationPrincipal Jwt jwt) {
        return queryService.listProfiles(currentUserService.resolveId(jwt));
    }

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a viewer profile")
    public ProfileDto createProfile(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody ProfileCreateRequest request) {
        return commandService.createProfile(currentUserService.resolveId(jwt), request);
    }

    @DeleteMapping("/profiles/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one of my profiles")
    public void deleteProfile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID profileId) {
        commandService.deleteProfile(currentUserService.resolveId(jwt), profileId);
    }

    // ──────────────────────────── watchlist ───────────────────────────

    @GetMapping("/profiles/{profileId}/watchlist")
    @Operation(summary = "My list, most recently added first")
    public List<WatchlistItemDto> listWatchlist(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID profileId) {
        return queryService.listWatchlist(currentUserService.resolveId(jwt), profileId);
    }

    @PostMapping("/profiles/{profileId}/watchlist")
    @Operation(summary = "Add a title to my list", description = "Idempotent.")
    public WatchlistItemDto addToWatchlist(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID profileId,
                                           @Valid @RequestBody WatchlistAddRequest request) {
        return commandService.addToWatchlist(currentUserService.resolveId(jwt), profileId, request);
    }

    @DeleteMapping("/profiles/{profileId}/watchlist/{contentId}")
    @Operation(summary = "Remove a title from my list")
    public void removeFromWatchlist(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID profileId,
                                    @PathVariable UUID contentId) {
        commandService.removeFromWatchlist(currentUserService.resolveId(jwt), profileId, contentId);
    }

    // ──────────────────────────── progress ────────────────────────────

    @GetMapping("/profiles/{profileId}/history")
    @Operation(summary = "My watch history, most recent first")
    public List<WatchHistoryDto> listHistory(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID profileId,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return queryService.listHistory(currentUserService.resolveId(jwt), profileId, limit);
    }

    @GetMapping("/profiles/{profileId}/continue-watching")
    @Operation(summary = "Titles I have started but not finished")
    public List<WatchHistoryDto> listContinueWatching(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable UUID profileId,
                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return queryService.listContinueWatching(currentUserService.resolveId(jwt), profileId, limit);
    }

    @PutMapping("/profiles/{profileId}/progress")
    @Operation(summary = "Record my playback progress", description = "Idempotent upsert; completes at 95%.")
    public WatchHistoryDto recordProgress(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable UUID profileId,
                                          @Valid @RequestBody WatchProgressRequest request) {
        return commandService.recordProgress(currentUserService.resolveId(jwt), profileId, request);
    }
}
