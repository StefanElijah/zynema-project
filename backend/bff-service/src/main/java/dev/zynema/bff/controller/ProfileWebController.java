package dev.zynema.bff.controller;

import dev.zynema.bff.client.DownstreamErrors;
import dev.zynema.bff.dto.AccountView;
import dev.zynema.bff.dto.ProfileHomeView;
import dev.zynema.bff.service.ProfileHomeService;
import dev.zynema.bff.service.UpstreamGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Profiles and what they list. Requires a token; user-service owns the data and
 * enforces that the profile belongs to the caller, which is why the BFF never
 * checks ownership itself.
 *
 * <p>The rails ({@code /home}), profile management and the watchlist all live
 * here because they are the same screen's concerns.
 */
@RestController
@RequestMapping("/api/v1/web/profiles")
@RequiredArgsConstructor
@Tag(name = "Web BFF", description = "Aggregated views for the SPA")
public class ProfileWebController {

    private final ProfileHomeService profileHomeService;
    private final UpstreamGateway gateway;

    @GetMapping
    @Operation(summary = "Profiles of the signed-in account")
    public Mono<List<AccountView.Profile>> profiles() {
        return gateway.profiles()
            .map(profiles -> profiles.stream()
                .map(profile -> new AccountView.Profile(profile.id(), profile.name(),
                    profile.kids(), profile.language()))
                .toList())
            .transform(publisher -> DownstreamErrors.toApiError("user-service", publisher));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a profile")
    public Mono<AccountView.Profile> createProfile(@Valid @RequestBody ProfileRequest request) {
        return gateway.createProfile(request.name(), request.avatarKey(), request.kids(), request.language())
            .map(profile -> new AccountView.Profile(profile.id(), profile.name(),
                profile.kids(), profile.language()))
            .transform(publisher -> DownstreamErrors.toApiError("user-service", publisher));
    }

    @DeleteMapping("/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a profile")
    public Mono<Void> deleteProfile(@PathVariable UUID profileId) {
        return gateway.deleteProfile(profileId)
            .transform(publisher -> DownstreamErrors.toApiError("user-service", publisher));
    }

    @GetMapping("/{profileId}/home")
    @Operation(summary = "Continue watching and my list for one profile, joined with the catalogue")
    public Mono<ProfileHomeView> profileHome(@PathVariable UUID profileId, @AuthenticationPrincipal Jwt jwt) {
        return profileHomeService.profileHome(profileId, jwt);
    }

    @PostMapping("/{profileId}/watchlist")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a title to the profile's list")
    public Mono<WatchlistAdded> addToWatchlist(@PathVariable UUID profileId,
                                               @Valid @RequestBody WatchlistRequest request) {
        return gateway.addToWatchlist(profileId, request.contentId())
            .map(entry -> new WatchlistAdded(entry.id(), entry.contentId(), entry.addedAt()))
            .transform(publisher -> DownstreamErrors.toApiError("user-service", publisher));
    }

    @DeleteMapping("/{profileId}/watchlist/{contentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a title from the profile's list")
    public Mono<Void> removeFromWatchlist(@PathVariable UUID profileId, @PathVariable UUID contentId) {
        return gateway.removeFromWatchlist(profileId, contentId)
            .transform(publisher -> DownstreamErrors.toApiError("user-service", publisher));
    }

    /** The create form: the same limits user-service enforces. */
    public record ProfileRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 40) String avatarKey,
        Boolean kids,
        @Size(max = 10) String language
    ) {
    }

    public record WatchlistRequest(@NotNull UUID contentId) {
    }

    public record WatchlistAdded(UUID id, UUID contentId, Instant addedAt) {
    }
}
