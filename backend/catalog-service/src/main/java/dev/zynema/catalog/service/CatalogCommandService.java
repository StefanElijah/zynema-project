package dev.zynema.catalog.service;

import dev.zynema.catalog.config.CacheConfig;
import dev.zynema.catalog.domain.Content;
import dev.zynema.catalog.domain.ContentStatus;
import dev.zynema.catalog.domain.Genre;
import dev.zynema.catalog.dto.ContentCreateRequest;
import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.dto.ContentUpdateRequest;
import dev.zynema.catalog.repository.ContentRepository;
import dev.zynema.catalog.repository.GenreRepository;
import dev.zynema.common.exception.BusinessRuleException;
import dev.zynema.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Write side of the catalog (admin API).
 *
 * <p>Every mutation evicts the whole catalog cache region: catalog writes are
 * rare compared to reads, and a coarse eviction is far easier to reason about
 * than per-key invalidation across list/detail/search caches.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogCommandService {

    private final ContentRepository contentRepository;
    private final GenreRepository genreRepository;
    private final CatalogQueryService queryService;

    @CacheEvict(cacheNames = {
        CacheConfig.CONTENT_LIST, CacheConfig.CONTENT_DETAIL,
        CacheConfig.SEASON_EPISODES, CacheConfig.GENRES
    }, allEntries = true)
    public ContentDetailDto create(ContentCreateRequest request) {
        if (contentRepository.existsBySlug(request.slug())) {
            throw new BusinessRuleException("Slug '%s' is already in use".formatted(request.slug()));
        }

        Content content = new Content();
        content.setType(request.type());
        content.setSlug(request.slug());
        content.setGenres(resolveGenres(request.genreSlugs()));
        applyCreateFields(content, request);

        Content saved = contentRepository.save(content);
        return queryService.buildDetail(saved.getSlug());
    }

    @CacheEvict(cacheNames = {
        CacheConfig.CONTENT_LIST, CacheConfig.CONTENT_DETAIL,
        CacheConfig.SEASON_EPISODES, CacheConfig.GENRES
    }, allEntries = true)
    public ContentDetailDto update(UUID id, ContentUpdateRequest request) {
        Content content = contentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Content", id));

        content.setGenres(resolveGenres(request.genreSlugs()));
        applyUpdateFields(content, request);

        Content saved = contentRepository.save(content);
        return queryService.buildDetail(saved.getSlug());
    }

    @CacheEvict(cacheNames = {
        CacheConfig.CONTENT_LIST, CacheConfig.CONTENT_DETAIL,
        CacheConfig.SEASON_EPISODES, CacheConfig.GENRES
    }, allEntries = true)
    public void delete(UUID id) {
        if (!contentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Content", id);
        }
        contentRepository.deleteById(id);
    }

    // ───────────────────────────── helpers ────────────────────────────

    private Set<Genre> resolveGenres(Set<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Genre> found = genreRepository.findAllBySlugIn(slugs);
        Set<String> foundSlugs = found.stream().map(Genre::getSlug).collect(Collectors.toSet());
        Set<String> missing = slugs.stream()
            .filter(slug -> !foundSlugs.contains(slug))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("Unknown genres: " + String.join(", ", missing));
        }
        return new LinkedHashSet<>(found);
    }

    private void applyCreateFields(Content content, ContentCreateRequest request) {
        content.setTitle(request.title());
        content.setOriginalTitle(request.originalTitle());
        content.setSynopsis(request.synopsis());
        content.setTagline(request.tagline());
        content.setReleaseYear(request.releaseYear());
        content.setMaturityRating(request.maturityRating());
        content.setRuntimeMinutes(request.runtimeMinutes());
        content.setPosterUrl(request.posterUrl());
        content.setBackdropUrl(request.backdropUrl());
        content.setTrailerUrl(request.trailerUrl());
        content.setAverageRating(request.averageRating());
        content.setPopularity(request.popularity() == null ? 0 : request.popularity());
        content.setStatus(request.status() == null ? ContentStatus.PUBLISHED : request.status());
        content.setMetadata(request.metadata() == null ? java.util.Map.of() : request.metadata());
    }

    private void applyUpdateFields(Content content, ContentUpdateRequest request) {
        content.setTitle(request.title());
        content.setOriginalTitle(request.originalTitle());
        content.setSynopsis(request.synopsis());
        content.setTagline(request.tagline());
        content.setReleaseYear(request.releaseYear());
        content.setMaturityRating(request.maturityRating());
        content.setRuntimeMinutes(request.runtimeMinutes());
        content.setPosterUrl(request.posterUrl());
        content.setBackdropUrl(request.backdropUrl());
        content.setTrailerUrl(request.trailerUrl());
        content.setAverageRating(request.averageRating());
        if (request.popularity() != null) {
            content.setPopularity(request.popularity());
        }
        if (request.status() != null) {
            content.setStatus(request.status());
        }
        if (request.metadata() != null) {
            content.setMetadata(request.metadata());
        }
    }
}
