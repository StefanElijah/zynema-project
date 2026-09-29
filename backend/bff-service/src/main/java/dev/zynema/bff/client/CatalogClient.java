package dev.zynema.bff.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.zynema.bff.dto.ContentKind;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Catalog reads over HTTP.
 *
 * <p>No resilience here: the calls are raw publishers so the circuit breaker
 * and retry see the real HTTP outcome. Composition services map the failures
 * to the API contract once the call has given up (see {@link DownstreamErrors}).
 *
 * <p>The nested records are the wire format, deliberately separate from the
 * BFF's own DTOs: the JSON is the contract, and a rename in catalog-service
 * should fail a test, not silently change the frontend's payload.
 */
@Component
public class CatalogClient {

    private final WebClient webClient;

    public CatalogClient(@Qualifier("catalogWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<List<ContentSummary>> listMovies(String sort, int size) {
        return page(moviesPath(), filter -> filter, sort, 0, size).map(DownstreamPage::content);
    }

    public Mono<List<ContentSummary>> listSeries(String sort, int size) {
        return page(seriesPath(), filter -> filter, sort, 0, size).map(DownstreamPage::content);
    }

    /**
     * The browse screen: the caller's filters travel unchanged to the
     * catalogue, which is the only service that knows how to apply them.
     */
    public Mono<DownstreamPage<ContentSummary>> browse(ContentKind type, ContentFilter filter,
                                                       String sort, int page, int size) {
        String path = type == ContentKind.MOVIE ? moviesPath() : seriesPath();
        return page(path, builder -> {
            if (filter.genre() != null) {
                builder.queryParam("genre", filter.genre());
            }
            if (filter.yearFrom() != null) {
                builder.queryParam("yearFrom", filter.yearFrom());
            }
            if (filter.yearTo() != null) {
                builder.queryParam("yearTo", filter.yearTo());
            }
            if (filter.minRating() != null) {
                builder.queryParam("minRating", filter.minRating());
            }
            return builder;
        }, sort, page, size);
    }

    public Mono<DownstreamPage<ContentSummary>> search(String query, int page, int size) {
        return webClient.get()
            .uri(builder -> builder.path("/api/v1/catalog/search")
                .queryParam("q", query)
                .queryParam("page", page)
                .queryParam("size", size)
                .build())
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<DownstreamPage<ContentSummary>>() {
            });
    }

    private Mono<DownstreamPage<ContentSummary>> page(String path,
                                                      java.util.function.UnaryOperator<org.springframework.web.util.UriBuilder> filters,
                                                      String sort, int page, int size) {
        return webClient.get()
            .uri(builder -> filters.apply(builder.path(path)
                    .queryParam("sort", sort)
                    .queryParam("page", page)
                    .queryParam("size", size))
                .build())
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<DownstreamPage<ContentSummary>>() {
            });
    }

    /**
     * Accepts an id or a slug: the SPA has both depending on where it came
     * from, and making it know which endpoint to call would leak the catalogue's
     * URL layout into the UI.
     */
    public Mono<ContentDetail> contentDetail(String idOrSlug) {
        return isUuid(idOrSlug)
            ? detail("/api/v1/catalog/contents/" + idOrSlug)
            // A slug does not say which endpoint owns it; a miss on movies
            // means "try series", not "content not found".
            : detail("/api/v1/catalog/movies/" + idOrSlug)
                .onErrorResume(CatalogClient::isNotFound, missing -> detail("/api/v1/catalog/series/" + idOrSlug));
    }

    private Mono<ContentDetail> detail(String path) {
        return webClient.get()
            .uri(path)
            .retrieve()
            .bodyToMono(ContentDetail.class);
    }

    private static String moviesPath() {
        return "/api/v1/catalog/movies";
    }

    private static String seriesPath() {
        return "/api/v1/catalog/series";
    }

    private static boolean isNotFound(Throwable failure) {
        return failure instanceof WebClientResponseException response && response.getStatusCode().value() == 404;
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** The filters the SPA may pass through to the catalogue. */
    public record ContentFilter(String genre, Integer yearFrom, Integer yearTo, BigDecimal minRating) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentSummary(
        UUID id,
        ContentKind type,
        String title,
        String slug,
        Integer releaseYear,
        String maturityRating,
        Integer runtimeMinutes,
        String posterUrl,
        String backdropUrl,
        BigDecimal averageRating,
        Integer popularity,
        List<Genre> genres
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentDetail(
        UUID id,
        ContentKind type,
        String title,
        String originalTitle,
        String slug,
        String synopsis,
        String tagline,
        Integer releaseYear,
        String maturityRating,
        Integer runtimeMinutes,
        String posterUrl,
        String backdropUrl,
        String trailerUrl,
        BigDecimal averageRating,
        Integer popularity,
        List<Genre> genres,
        List<Season> seasons,
        List<Credit> credits,
        Instant createdAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Genre(UUID id, String name, String slug) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Season(UUID id, Integer seasonNumber, String title, Integer releaseYear, Integer episodeCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Credit(String personName, String role, String characterName) {
    }
}
