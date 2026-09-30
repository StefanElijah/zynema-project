package dev.zynema.bff.service;

import dev.zynema.bff.client.CatalogClient;
import dev.zynema.bff.client.DownstreamErrors;
import dev.zynema.bff.client.UserClient;
import dev.zynema.bff.config.BffCacheConfig;
import dev.zynema.bff.dto.ProfileHomeView;
import dev.zynema.bff.dto.TitleCard;
import dev.zynema.bff.dto.WebSection;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The profile rails: the join that justifies a BFF.
 *
 * <p>user-service knows <em>what</em> was watched (ids and positions),
 * catalog-service knows <em>what it is</em> (title, artwork). Neither call is
 * enough alone, and the frontend must not orchestrate the join itself.
 *
 * <p>Two rules keep this honest:
 * <ul>
 *   <li>Each rail degrades independently — a failing watchlist does not hide
 *       the half-watched episode.</li>
 *   <li>An id that no longer exists in the catalogue is dropped silently (the
 *       title was unpublished); a catalogue outage fails the page, because then
 *       no rail can be built at all.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ProfileHomeService {

    private static final int RAIL_SIZE = 12;
    private static final int CARD_LOOKUP_LIMIT = 24;

    private final UpstreamGateway gateway;

    @Cacheable(cacheNames = BffCacheConfig.PROFILE_HOME, key = "#jwt.subject + ':' + #profileId")
    public Mono<ProfileHomeView> profileHome(UUID profileId, Jwt jwt) {
        return Mono.zip(gateway.continueWatching(profileId), gateway.watchlist(profileId))
            .flatMap(rails -> join(profileId, rails.getT1(), rails.getT2()));
    }

    private Mono<ProfileHomeView> join(UUID profileId,
                                       DownstreamResult<List<UserClient.WatchHistory>> continueWatching,
                                       DownstreamResult<List<UserClient.WatchlistEntry>> watchlist) {
        List<WebSection> degraded = new ArrayList<>();
        if (continueWatching.degraded()) {
            degraded.add(WebSection.CONTINUE_WATCHING);
        }
        if (watchlist.degraded()) {
            degraded.add(WebSection.MY_LIST);
        }

        List<UserClient.WatchHistory> history = continueWatching.present() ? continueWatching.value() : List.of();
        List<UserClient.WatchlistEntry> entries = watchlist.present() ? watchlist.value() : List.of();
        List<UUID> ids = distinctIds(history, entries);

        if (ids.isEmpty()) {
            return Mono.just(new ProfileHomeView(profileId, List.of(), List.of(), degraded));
        }

        return cardsById(ids).map(cards -> new ProfileHomeView(profileId,
            history.stream()
                .filter(item -> cards.containsKey(item.contentId()))
                .limit(RAIL_SIZE)
                .map(item -> new ProfileHomeView.ContinueWatching(
                    cards.get(item.contentId()),
                    item.positionSeconds() == null ? 0 : item.positionSeconds(),
                    item.durationSeconds(),
                    item.completed(),
                    item.lastWatchedAt(),
                    item.episodeId()))
                .toList(),
            entries.stream()
                .filter(item -> cards.containsKey(item.contentId()))
                .limit(RAIL_SIZE)
                .map(item -> new ProfileHomeView.Watchlist(cards.get(item.contentId()), item.addedAt()))
                .toList(),
            degraded));
    }

    /**
     * One detail call per id, in parallel and capped: the BFF trades a few
     * concurrent reads for not needing batch endpoints in the catalogue. Each
     * answer is cached, so a second visit costs nothing.
     */
    private Mono<Map<UUID, TitleCard>> cardsById(List<UUID> ids) {
        return Flux.fromIterable(ids)
            .flatMap(id -> gateway.contentDetail(id.toString())
                .map(detail -> Map.entry(id, ViewMapper.card(detail)))
                .onErrorResume(ProfileHomeService::isGone, missing -> Mono.empty()))
            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
            .transform(publisher -> DownstreamErrors.toApiError("catalog-service", publisher));
    }

    private static boolean isGone(Throwable failure) {
        return failure instanceof WebClientResponseException response && response.getStatusCode().value() == 404;
    }

    private List<UUID> distinctIds(List<UserClient.WatchHistory> history,
                                   List<UserClient.WatchlistEntry> entries) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        history.stream().map(UserClient.WatchHistory::contentId).filter(java.util.Objects::nonNull).forEach(ids::add);
        entries.stream().map(UserClient.WatchlistEntry::contentId).filter(java.util.Objects::nonNull).forEach(ids::add);
        return ids.stream().limit(CARD_LOOKUP_LIMIT).toList();
    }
}
