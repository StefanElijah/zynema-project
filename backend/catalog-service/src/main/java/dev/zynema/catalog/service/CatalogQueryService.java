package dev.zynema.catalog.service;

import dev.zynema.catalog.config.CacheConfig;
import dev.zynema.catalog.domain.Content;
import dev.zynema.catalog.domain.ContentStatus;
import dev.zynema.catalog.domain.ContentType;
import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.dto.ContentFilter;
import dev.zynema.catalog.dto.ContentSummaryDto;
import dev.zynema.catalog.dto.EpisodeDto;
import dev.zynema.catalog.dto.GenreDto;
import dev.zynema.catalog.mapper.ContentMapper;
import dev.zynema.catalog.mapper.CreditMapper;
import dev.zynema.catalog.mapper.EpisodeMapper;
import dev.zynema.catalog.mapper.GenreMapper;
import dev.zynema.catalog.mapper.SeasonMapper;
import dev.zynema.catalog.repository.ContentRepository;
import dev.zynema.catalog.repository.CreditRepository;
import dev.zynema.catalog.repository.EpisodeRepository;
import dev.zynema.catalog.repository.GenreRepository;
import dev.zynema.catalog.repository.SeasonRepository;
import dev.zynema.catalog.specification.ContentSpecifications;
import dev.zynema.common.dto.PageResponse;
import dev.zynema.common.exception.BusinessRuleException;
import dev.zynema.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Read side of the catalog (CQRS-ready: reads never touch the write model's
 * invariants, see ADR-0006).
 *
 * <p>Listing endpoints deliberately avoid collection fetches: they paginate
 * on the plain table and then batch-load genres for the page in one extra
 * query. That keeps pagination in the database instead of in memory.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogQueryService {

    private static final Map<String, String> ALLOWED_SORTS = Map.of(
        "popularity", "popularity",
        "rating", "averageRating",
        "releaseYear", "releaseYear",
        "title", "title"
    );

    private final ContentRepository contentRepository;
    private final SeasonRepository seasonRepository;
    private final EpisodeRepository episodeRepository;
    private final CreditRepository creditRepository;
    private final GenreRepository genreRepository;

    private final ContentMapper contentMapper;
    private final SeasonMapper seasonMapper;
    private final EpisodeMapper episodeMapper;
    private final CreditMapper creditMapper;
    private final GenreMapper genreMapper;

    // ────────────────────────────── lists ──────────────────────────────

    // Caching lives on the public entry points: a @Cacheable on a method that
    // is only reached through self-invocation would never be applied.

    @Cacheable(cacheNames = CacheConfig.CONTENT_LIST,
               key = "'MOVIE:' + (#filter == null ? 'all' : #filter.cacheKey()) + ':' + #sort + ':' + #page + ':' + #size")
    public PageResponse<ContentSummaryDto> listMovies(ContentFilter filter, String sort, int page, int size) {
        return list(ContentType.MOVIE, filter, sort, page, size);
    }

    @Cacheable(cacheNames = CacheConfig.CONTENT_LIST,
               key = "'SERIES:' + (#filter == null ? 'all' : #filter.cacheKey()) + ':' + #sort + ':' + #page + ':' + #size")
    public PageResponse<ContentSummaryDto> listSeries(ContentFilter filter, String sort, int page, int size) {
        return list(ContentType.SERIES, filter, sort, page, size);
    }

    private PageResponse<ContentSummaryDto> list(ContentType type, ContentFilter filter, String sort, int page, int size) {
        ContentFilter effective = filter == null ? ContentFilter.empty() : filter;
        Specification<Content> spec = Specification.allOf(
            ContentSpecifications.hasType(type),
            ContentSpecifications.hasStatus(ContentStatus.PUBLISHED),
            ContentSpecifications.hasGenre(effective.genre()),
            ContentSpecifications.releaseYearFrom(effective.yearFrom()),
            ContentSpecifications.releaseYearTo(effective.yearTo()),
            ContentSpecifications.minRating(effective.minRating())
        );
        Page<Content> result = contentRepository.findAll(spec, toPageable(page, size, sort));
        return toPageResponse(result);
    }

    @Cacheable(cacheNames = CacheConfig.CONTENT_LIST,
               key = "'search:' + #query.toLowerCase() + ':' + #page + ':' + #size")
    public PageResponse<ContentSummaryDto> search(String query, int page, int size) {
        if (query == null || query.isBlank()) {
            throw new BusinessRuleException("Search query must not be blank");
        }
        Page<UUID> ids = contentRepository.searchIdsByTitle(query.trim(), PageRequest.of(page, size));
        List<Content> contents = loadWithGenresPreservingOrder(ids.getContent());
        List<ContentSummaryDto> payload = contents.stream().map(contentMapper::toSummary).toList();
        return new PageResponse<>(
            payload,
            ids.getNumber(),
            ids.getSize(),
            ids.getTotalElements(),
            ids.getTotalPages(),
            ids.isFirst(),
            ids.isLast()
        );
    }

    // ───────────────────────────── detail ─────────────────────────────

    @Cacheable(cacheNames = CacheConfig.CONTENT_DETAIL, key = "#slug")
    public ContentDetailDto getBySlug(String slug) {
        return buildDetail(slug);
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
        return buildDetailById(id);
    }

    /**
     * Builds the detail view without touching the cache. Used by the write
     * side: reading through {@link #getBySlug(String)} right after a mutation
     * could serve a stale cached entry, because the cache is evicted only
     * after the write method returns.
     */
    public ContentDetailDto buildDetail(String slug) {
        Content content = contentRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Content", slug));
        return assembleDetail(content);
    }

    public ContentDetailDto buildDetailById(UUID id) {
        Content content = contentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Content", id));
        return assembleDetail(content);
    }

    private ContentDetailDto assembleDetail(Content content) {
        List<dev.zynema.catalog.dto.SeasonDto> seasons = content.getType() == ContentType.SERIES
            ? seasonRepository.findSeasonSummaries(content.getId()).stream().map(seasonMapper::toDto).toList()
            : List.of();

        var credits = creditMapper.toDtoList(
            creditRepository.findByContentIdOrderByBillingOrderAsc(content.getId()));

        return contentMapper.toDetail(content).toBuilder()
            .seasons(seasons)
            .credits(credits)
            .build();
    }

    @Cacheable(cacheNames = CacheConfig.SEASON_EPISODES, key = "#slug + ':s' + #seasonNumber")
    public List<EpisodeDto> listEpisodes(String slug, int seasonNumber) {
        Content content = contentRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Content", slug));
        if (content.getType() != ContentType.SERIES) {
            throw new BusinessRuleException("Content '%s' is not a series".formatted(slug));
        }
        var season = seasonRepository.findByContentIdAndSeasonNumber(content.getId(), seasonNumber)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Season %d of %s".formatted(seasonNumber, slug)));
        return episodeMapper.toDtoList(episodeRepository.findBySeasonIdOrderByEpisodeNumberAsc(season.getId()));
    }

    // ───────────────────────────── genres ─────────────────────────────

    @Cacheable(cacheNames = CacheConfig.GENRES, key = "'all'")
    public List<GenreDto> listGenres() {
        return genreRepository.findAllByOrderByNameAsc().stream().map(genreMapper::toDto).toList();
    }

    // ───────────────────────────── helpers ────────────────────────────

    private Pageable toPageable(int page, int size, String sort) {
        String[] parts = sort == null ? new String[0] : sort.split(",");
        String field = ALLOWED_SORTS.getOrDefault(parts.length > 0 ? parts[0] : "popularity", "popularity");
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
        // Secondary key keeps pagination stable when the primary key ties.
        return PageRequest.of(page, size, Sort.by(direction, field).and(Sort.by(Sort.Direction.ASC, "id")));
    }

    private PageResponse<ContentSummaryDto> toPageResponse(Page<Content> page) {
        List<Content> contents = loadWithGenresPreservingOrder(page.getContent().stream().map(Content::getId).toList());
        return new PageResponse<>(
            contents.stream().map(contentMapper::toSummary).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast()
        );
    }

    private List<Content> loadWithGenresPreservingOrder(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Content> byId = new LinkedHashMap<>();
        contentRepository.findAllWithGenresByIdIn(ids).forEach(content -> byId.put(content.getId(), content));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /** Exposed for the command service so both share one slug policy. */
    static Set<String> allowedSorts() {
        return ALLOWED_SORTS.keySet();
    }
}
