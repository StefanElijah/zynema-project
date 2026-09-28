package dev.zynema.catalog.service;

import dev.zynema.catalog.config.CacheConfig;
import dev.zynema.catalog.domain.ContentType;
import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.dto.ContentFilter;
import dev.zynema.catalog.dto.ContentSummaryDto;
import dev.zynema.catalog.dto.EpisodeDto;
import dev.zynema.catalog.dto.GenreDto;
import dev.zynema.catalog.mapper.GenreMapper;
import dev.zynema.catalog.repository.CatalogReadRepository;
import dev.zynema.catalog.repository.GenreRepository;
import dev.zynema.common.dto.PageResponse;
import dev.zynema.common.exception.BusinessRuleException;
import dev.zynema.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read side of the catalogue (ADR-0006, ADR-0022).
 *
 * <p>Every answer comes from the read model: the query side no longer touches
 * the write tables, so a change in how content is stored cannot change what a
 * client sees until the projector decides it should.
 *
 * <p>Genres are the deliberate exception: they are reference data, not
 * projected state, and they are read from their own table.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogQueryService {

    private final CatalogReadRepository readRepository;
    private final GenreRepository genreRepository;
    private final GenreMapper genreMapper;

    // ────────────────────────────── lists ──────────────────────────────

    // Caching lives on the public entry points: a @Cacheable on a method that
    // is only reached through self-invocation would never be applied.

    @Cacheable(cacheNames = CacheConfig.CONTENT_LIST,
               key = "'MOVIE:' + (#filter == null ? 'all' : #filter.cacheKey()) + ':' + #sort + ':' + #page + ':' + #size")
    public PageResponse<ContentSummaryDto> listMovies(ContentFilter filter, String sort, int page, int size) {
        return readRepository.findPage(ContentType.MOVIE, filter, sort, page, size);
    }

    @Cacheable(cacheNames = CacheConfig.CONTENT_LIST,
               key = "'SERIES:' + (#filter == null ? 'all' : #filter.cacheKey()) + ':' + #sort + ':' + #page + ':' + #size")
    public PageResponse<ContentSummaryDto> listSeries(ContentFilter filter, String sort, int page, int size) {
        return readRepository.findPage(ContentType.SERIES, filter, sort, page, size);
    }

    @Cacheable(cacheNames = CacheConfig.CONTENT_LIST,
               key = "'search:' + #query.toLowerCase() + ':' + #page + ':' + #size")
    public PageResponse<ContentSummaryDto> search(String query, int page, int size) {
        if (query == null || query.isBlank()) {
            throw new BusinessRuleException("Search query must not be blank");
        }
        return readRepository.search(query.trim(), page, size);
    }

    // ───────────────────────────── detail ─────────────────────────────

    @Cacheable(cacheNames = CacheConfig.CONTENT_DETAIL, key = "#slug")
    public ContentDetailDto getBySlug(String slug) {
        return readRepository.findDetailBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Content", slug));
    }

    /**
     * Same detail view, addressed by id. Used by services that hold content ids
     * rather than slugs (playback sessions, watch history) and by the BFF.
     *
     * <p>Shares the detail cache region with a distinguishable key: slugs never
     * start with {@code id:}, so both lookups coexist and a single eviction
     * invalidates both.
     */
    @Cacheable(cacheNames = CacheConfig.CONTENT_DETAIL, key = "'id:' + #id")
    public ContentDetailDto getById(UUID id) {
        return readRepository.findDetailById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Content", id));
    }

    /**
     * Same read, without caching. Used by the write side right after a
     * mutation: the cache is evicted only after the write method returns, so
     * reading through {@link #getBySlug(String)} there could still answer with
     * the previous version.
     */
    public ContentDetailDto buildDetail(String slug) {
        return readRepository.findDetailBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Content", slug));
    }

    public ContentDetailDto buildDetailById(UUID id) {
        return readRepository.findDetailById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Content", id));
    }

    @Cacheable(cacheNames = CacheConfig.SEASON_EPISODES, key = "#slug + ':s' + #seasonNumber")
    public List<EpisodeDto> listEpisodes(String slug, int seasonNumber) {
        ContentDetailDto detail = readRepository.findDetailBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Content", slug));
        if (detail.type() != ContentType.SERIES) {
            throw new BusinessRuleException("Content '%s' is not a series".formatted(slug));
        }
        return readRepository.findEpisodes(slug, seasonNumber)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Season %d of %s".formatted(seasonNumber, slug)));
    }

    // ───────────────────────────── genres ─────────────────────────────

    @Cacheable(cacheNames = CacheConfig.GENRES, key = "'all'")
    public List<GenreDto> listGenres() {
        return genreRepository.findAllByOrderByNameAsc().stream().map(genreMapper::toDto).toList();
    }
}
