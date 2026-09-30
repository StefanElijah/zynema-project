package dev.zynema.bff.service;

import dev.zynema.bff.client.DownstreamErrors;
import dev.zynema.bff.config.BffCacheConfig;
import dev.zynema.bff.dto.HomeView;
import dev.zynema.bff.dto.TitleCard;
import dev.zynema.common.exception.DownstreamServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * The landing screen, composed from four parallel catalogue reads.
 *
 * <p>Anonymous on purpose: the visitor must see what the platform has before
 * being asked for anything. Watching is what needs an account and a plan, and
 * that is decided by playback-service, never here.
 *
 * <p>Cached as a whole for a few minutes: every visitor sees the same page, so
 * one shared entry absorbs the stampede that a landing screen attracts.
 */
@Service
@RequiredArgsConstructor
public class HomeService {

    private static final int RAIL_SIZE = 12;
    private static final int NEW_RELEASES_POOL = 8;
    private static final String NEWEST = "releaseYear,desc";
    private static final String POPULAR = "popularity";

    private final UpstreamGateway gateway;

    @Cacheable(cacheNames = BffCacheConfig.HOME, key = "'global'")
    public Mono<HomeView> home() {
        return Mono.zip(
                gateway.listSeries(POPULAR, RAIL_SIZE),
                gateway.listMovies(POPULAR, RAIL_SIZE),
                gateway.listMovies(NEWEST, NEW_RELEASES_POOL),
                gateway.listSeries(NEWEST, NEW_RELEASES_POOL))
            .flatMap(sources -> {
                List<TitleCard> popularSeries = sources.getT1().stream().map(ViewMapper::card).toList();
                List<TitleCard> popularMovies = sources.getT2().stream().map(ViewMapper::card).toList();
                List<TitleCard> newReleases = ViewMapper.newestFirst(sources.getT3(), sources.getT4(), RAIL_SIZE);

                TitleCard featured = firstNonNull(popularSeries, popularMovies, newReleases);
                if (featured == null) {
                    return Mono.error(new DownstreamServiceException("catalog-service",
                        "The catalogue returned no published titles"));
                }

                // The hero needs the synopsis, which a poster does not carry.
                return gateway.contentDetail(featured.id().toString())
                    .map(detail -> new HomeView(
                        new HomeView.Hero(ViewMapper.card(detail), detail.synopsis(), detail.tagline()),
                        List.of(
                            new HomeView.Row("new-releases", "Estrenos", newReleases),
                            new HomeView.Row("popular-series", "Series populares", popularSeries),
                            new HomeView.Row("popular-movies", "Películas populares", popularMovies))));
            })
            .transform(publisher -> DownstreamErrors.toApiError("catalog-service", publisher));
    }

    @SafeVarargs
    private static TitleCard firstNonNull(List<TitleCard>... rails) {
        return java.util.Arrays.stream(rails)
            .map(rail -> rail.isEmpty() ? null : rail.get(0))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }
}
