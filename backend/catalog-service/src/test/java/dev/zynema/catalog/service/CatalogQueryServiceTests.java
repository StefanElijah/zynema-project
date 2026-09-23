package dev.zynema.catalog.service;

import dev.zynema.catalog.AbstractCatalogIntegrationTest;
import dev.zynema.catalog.config.CacheConfig;
import dev.zynema.catalog.domain.ContentType;
import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.dto.ContentFilter;
import dev.zynema.catalog.dto.ContentSummaryDto;
import dev.zynema.catalog.dto.EpisodeDto;
import dev.zynema.catalog.dto.GenreDto;
import dev.zynema.common.dto.PageResponse;
import dev.zynema.common.exception.BusinessRuleException;
import dev.zynema.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Read-side tests against the real seeded database. Assertions are written
 * against facts that the seed migrations guarantee, so a broken migration or
 * a broken query fails here loudly.
 */
class CatalogQueryServiceTests extends AbstractCatalogIntegrationTest {

    @Autowired
    private CatalogQueryService queryService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    // ────────────────────────────── movies ────────────────────────────

    @Test
    @DisplayName("lists movies sorted by popularity by default")
    void listsMoviesSortedByPopularity() {
        PageResponse<ContentSummaryDto> page = queryService.listMovies(ContentFilter.empty(), "popularity,desc", 0, 10);

        assertThat(page.totalElements()).isEqualTo(28);
        assertThat(page.content()).hasSize(10);
        assertThat(page.content().get(0).slug()).isEqualTo("dune-part-two");
        assertThat(page.content().get(0).genres()).extracting(GenreDto::slug)
            .contains("action", "adventure", "drama", "sci-fi");
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isFalse();
    }

    @Test
    @DisplayName("filters movies by genre")
    void filtersMoviesByGenre() {
        PageResponse<ContentSummaryDto> page =
            queryService.listMovies(new ContentFilter("horror", null, null, null), "popularity,desc", 0, 50);

        assertThat(page.content()).isNotEmpty();
        assertThat(page.content()).allSatisfy(movie ->
            assertThat(movie.genres()).extracting(GenreDto::slug).contains("horror"));
        assertThat(page.content()).extracting(ContentSummaryDto::slug)
            .containsExactlyInAnyOrder("alien-romulus", "five-nights-at-freddys", "five-nights-at-freddys-2")
            .doesNotContain("dune-part-two");
    }

    @Test
    @DisplayName("filters movies by release year range and minimum rating")
    void filtersMoviesByYearAndRating() {
        PageResponse<ContentSummaryDto> page =
            queryService.listMovies(new ContentFilter(null, 2020, 2025, new BigDecimal("8.0")), "releaseYear,desc", 0, 50);

        assertThat(page.content()).isNotEmpty();
        assertThat(page.content()).allSatisfy(movie -> {
            assertThat(movie.releaseYear()).isBetween(2020, 2025);
            assertThat(movie.averageRating()).isGreaterThanOrEqualTo(new BigDecimal("8.0"));
        });
    }

    // ────────────────────────────── series ────────────────────────────

    @Test
    @DisplayName("lists series with their genres")
    void listsSeries() {
        PageResponse<ContentSummaryDto> page = queryService.listSeries(ContentFilter.empty(), "popularity,desc", 0, 5);

        assertThat(page.totalElements()).isEqualTo(24);
        assertThat(page.content()).hasSize(5);
        assertThat(page.content().get(0).slug()).isEqualTo("breaking-bad");
        assertThat(page.content()).allSatisfy(series ->
            assertThat(series.type()).isEqualTo(ContentType.SERIES));
    }

    // ────────────────────────────── detail ────────────────────────────

    @Test
    @DisplayName("returns movie detail with genres and credits, no seasons")
    void returnsMovieDetail() {
        ContentDetailDto detail = queryService.getBySlug("dune-part-two");

        assertThat(detail.type()).isEqualTo(ContentType.MOVIE);
        assertThat(detail.title()).isEqualTo("Dune: Part Two");
        assertThat(detail.runtimeMinutes()).isEqualTo(166);
        assertThat(detail.seasons()).isEmpty();
        assertThat(detail.credits()).extracting(dev.zynema.catalog.dto.CreditDto::personName)
            .contains("Timothée Chalamet", "Zendaya", "Denis Villeneuve");
        assertThat(detail.credits()).anySatisfy(credit -> {
            assertThat(credit.personName()).isEqualTo("Timothée Chalamet");
            assertThat(credit.characterName()).isEqualTo("Paul Atreides");
        });
        assertThat(detail.metadata()).containsEntry("franchise", "Dune");
    }

    @Test
    @DisplayName("returns series detail with seasons and episode counts")
    void returnsSeriesDetail() {
        ContentDetailDto detail = queryService.getBySlug("arcane");

        assertThat(detail.type()).isEqualTo(ContentType.SERIES);
        assertThat(detail.seasons()).hasSize(2);
        assertThat(detail.seasons().get(0).seasonNumber()).isEqualTo(1);
        assertThat(detail.seasons().get(0).episodeCount()).isEqualTo(9);
        assertThat(detail.seasons().get(1).episodeCount()).isZero();
        assertThat(detail.credits()).extracting(dev.zynema.catalog.dto.CreditDto::personName)
            .contains("Hailee Steinfeld", "Ella Purnell");
    }

    @Test
    @DisplayName("unknown slug raises ResourceNotFoundException")
    void unknownSlugRaisesNotFound() {
        assertThatThrownBy(() -> queryService.getBySlug("does-not-exist"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("does-not-exist");
    }

    // ───────────────────────────── episodes ───────────────────────────

    @Test
    @DisplayName("lists episodes of a season in order")
    void listsEpisodes() {
        List<EpisodeDto> episodes = queryService.listEpisodes("arcane", 1);

        assertThat(episodes).hasSize(9);
        assertThat(episodes.get(0).episodeNumber()).isEqualTo(1);
        assertThat(episodes.get(0).title()).isEqualTo("Welcome to the Playground");
        assertThat(episodes.get(8).episodeNumber()).isEqualTo(9);
        assertThat(episodes.get(8).title()).isEqualTo("The Monster You Created");
    }

    @Test
    @DisplayName("episodes of a movie raise BusinessRuleException")
    void episodesOfMovieRaisesBusinessRule() {
        assertThatThrownBy(() -> queryService.listEpisodes("dune-part-two", 1))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("not a series");
    }

    @Test
    @DisplayName("unknown season raises ResourceNotFoundException")
    void unknownSeasonRaisesNotFound() {
        assertThatThrownBy(() -> queryService.listEpisodes("arcane", 99))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ────────────────────────────── search ────────────────────────────

    @Test
    @DisplayName("full-text search matches titles and ranks by popularity")
    void searchMatchesTitles() {
        PageResponse<ContentSummaryDto> page = queryService.search("dune", 0, 10);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(ContentSummaryDto::slug)
            .containsExactly("dune-part-two", "dune-part-one");
    }

    @Test
    @DisplayName("full-text search is case-insensitive and partial")
    void searchIsCaseInsensitive() {
        assertThat(queryService.search("SPIDER", 0, 10).content())
            .extracting(ContentSummaryDto::slug)
            .contains("spider-man-across-the-spider-verse");
    }

    @Test
    @DisplayName("blank search raises BusinessRuleException")
    void blankSearchRaisesBusinessRule() {
        assertThatThrownBy(() -> queryService.search("   ", 0, 10))
            .isInstanceOf(BusinessRuleException.class);
    }

    // ────────────────────────────── genres ────────────────────────────

    @Test
    @DisplayName("lists all genres alphabetically")
    void listsGenres() {
        List<GenreDto> genres = queryService.listGenres();

        assertThat(genres).hasSize(15);
        assertThat(genres).isSortedAccordingTo((a, b) -> a.name().compareTo(b.name()));
        assertThat(genres).extracting(GenreDto::slug)
            .contains("action", "sci-fi", "western");
    }

    // ────────────────────────────── cache ─────────────────────────────

    @Test
    @DisplayName("detail reads are cached as JSON and round-trip to the exact type")
    void detailIsCachedWithCorrectType() {
        queryService.getBySlug("dune-part-two");

        var cache = cacheManager.getCache(CacheConfig.CONTENT_DETAIL);
        assertThat(cache).isNotNull();
        var cached = cache.get("dune-part-two");
        assertThat(cached).isNotNull();
        assertThat(cached.get()).isInstanceOf(ContentDetailDto.class);

        ContentDetailDto fromCache = (ContentDetailDto) cached.get();
        assertThat(fromCache.title()).isEqualTo("Dune: Part Two");
        assertThat(fromCache.genres()).isNotEmpty();
    }

    @Test
    @DisplayName("list reads are cached and round-trip generics correctly")
    void listIsCachedWithCorrectGenerics() {
        queryService.listMovies(ContentFilter.empty(), "popularity,desc", 0, 5);

        var cache = cacheManager.getCache(CacheConfig.CONTENT_LIST);
        assertThat(cache).isNotNull();
        String key = "MOVIE:*|*|*|*:popularity,desc:0:5";
        assertThat(cache.get(key)).isNotNull();

        @SuppressWarnings("unchecked")
        PageResponse<ContentSummaryDto> cached = (PageResponse<ContentSummaryDto>) cache.get(key).get();
        assertThat(cached.content()).isNotEmpty();
        assertThat(cached.content().get(0)).isInstanceOf(ContentSummaryDto.class);
        assertThat(cached.content().get(0).slug()).isEqualTo("dune-part-two");
    }
}
