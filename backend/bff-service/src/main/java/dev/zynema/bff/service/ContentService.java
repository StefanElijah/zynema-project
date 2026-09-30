package dev.zynema.bff.service;

import dev.zynema.bff.client.CatalogClient;
import dev.zynema.bff.client.DownstreamErrors;
import dev.zynema.bff.dto.ContentView;
import dev.zynema.bff.dto.WebSection;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The detail screen: catalogue data (required) plus two pieces of user context
 * that are nice to have and must never cost the page.
 *
 * <ul>
 *   <li><b>Can this caller watch?</b> Answered from the plan's entitlements.
 *       Anonymous → "sign in"; no plan → "subscribe"; the plan cannot be
 *       checked → "unavailable". The BFF never says "yes" on its own: the real
 *       gate stays in playback-service.</li>
 *   <li><b>Where did this profile stop?</b> Read from the profile's
 *       continue-watching rail, only when the caller asks for a profile.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ContentService {

    private final UpstreamGateway gateway;

    public Mono<ContentView> content(String idOrSlug, UUID profileId, Jwt jwt) {
        return gateway.contentDetail(idOrSlug)
            .transform(publisher -> DownstreamErrors.toApiError("catalog-service", publisher))
            .flatMap(detail -> Mono.zip(
                    playbackAvailability(jwt),
                    progress(profileId, jwt, detail.id()))
                .map(context -> assemble(detail, context.getT1(), context.getT2())));
    }

    private Mono<PlaybackContext> playbackAvailability(Jwt jwt) {
        if (jwt == null) {
            return Mono.just(new PlaybackContext(
                new ContentView.PlaybackAvailability(false, ContentView.PlaybackReason.AUTHENTICATION_REQUIRED),
                false));
        }
        return gateway.entitlements().map(result -> {
            if (result.degraded()) {
                return new PlaybackContext(
                    new ContentView.PlaybackAvailability(false, ContentView.PlaybackReason.UNAVAILABLE), true);
            }
            boolean active = result.present() && result.value().active();
            return new PlaybackContext(
                new ContentView.PlaybackAvailability(active,
                    active ? null : ContentView.PlaybackReason.SUBSCRIPTION_REQUIRED),
                false);
        });
    }

    private Mono<ProgressContext> progress(UUID profileId, Jwt jwt, UUID contentId) {
        if (jwt == null || profileId == null) {
            return Mono.just(new ProgressContext(null, false));
        }
        return gateway.continueWatching(profileId).map(result -> {
            if (result.degraded()) {
                return new ProgressContext(null, true);
            }
            return new ProgressContext(result.present() ? result.value().stream()
                .filter(entry -> contentId.equals(entry.contentId()))
                .findFirst()
                .map(entry -> new ContentView.Progress(
                    entry.positionSeconds() == null ? 0 : entry.positionSeconds(),
                    entry.durationSeconds(),
                    entry.completed(),
                    entry.lastWatchedAt(),
                    entry.episodeId()))
                .orElse(null) : null, false);
        });
    }

    private ContentView assemble(CatalogClient.ContentDetail detail, PlaybackContext playback, ProgressContext progress) {
        List<WebSection> degraded = new ArrayList<>();
        if (playback.degraded()) {
            degraded.add(WebSection.PLAYBACK);
        }
        if (progress.degraded()) {
            degraded.add(WebSection.CONTINUE_WATCHING);
        }
        return new ContentView(toDetail(detail), playback.availability(), progress.progress(), degraded);
    }

    private ContentView.Detail toDetail(CatalogClient.ContentDetail detail) {
        return new ContentView.Detail(
            detail.id(),
            detail.type(),
            detail.slug(),
            detail.title(),
            detail.originalTitle(),
            detail.synopsis(),
            detail.tagline(),
            detail.releaseYear(),
            detail.maturityRating(),
            detail.runtimeMinutes(),
            detail.posterUrl(),
            detail.backdropUrl(),
            detail.trailerUrl(),
            detail.averageRating(),
            detail.popularity(),
            ViewMapper.genres(detail.genres()),
            detail.seasons() == null ? List.of() : detail.seasons().stream()
                .map(season -> new ContentView.Season(season.seasonNumber(), season.title(),
                    season.releaseYear(), season.episodeCount()))
                .toList(),
            detail.credits() == null ? List.of() : detail.credits().stream()
                .map(credit -> new ContentView.Credit(credit.personName(), credit.role(), credit.characterName()))
                .toList());
    }

    private record PlaybackContext(ContentView.PlaybackAvailability availability, boolean degraded) {
    }

    private record ProgressContext(ContentView.Progress progress, boolean degraded) {
    }
}
