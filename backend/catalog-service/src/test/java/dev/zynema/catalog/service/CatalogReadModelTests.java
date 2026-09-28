package dev.zynema.catalog.service;

import dev.zynema.catalog.AbstractCatalogIntegrationTest;
import dev.zynema.catalog.domain.ContentStatus;
import dev.zynema.catalog.domain.ContentType;
import dev.zynema.catalog.dto.ContentCreateRequest;
import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.dto.ContentFilter;
import dev.zynema.catalog.dto.ContentSummaryDto;
import dev.zynema.catalog.dto.ContentUpdateRequest;
import dev.zynema.catalog.repository.CatalogReadRepository;
import dev.zynema.catalog.repository.ContentRepository;
import dev.zynema.common.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CQRS guarantees of the catalogue (ADR-0006, ADR-0022).
 *
 * <p>Two properties are worth a test of their own: reads are answered by the
 * read model (not by the write tables wearing a different name), and a write
 * projects in its own transaction, so no client ever reads its own write from
 * the old shape.
 */
@Transactional
class CatalogReadModelTests extends AbstractCatalogIntegrationTest {

    @Autowired
    private CatalogQueryService queryService;

    @Autowired
    private CatalogCommandService commandService;

    @Autowired
    private CatalogReadRepository readRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    @Test
    @DisplayName("the read model holds one row per title, projection of the write tables")
    void projectionCoversTheWriteModel() {
        assertThat(readRepository.count()).isEqualTo(contentRepository.count());
        assertThat(readRepository.count()).isPositive();

        // The episode payload is part of the projection, keyed by season.
        Integer seasons = jdbc.queryForObject(
            "SELECT jsonb_object_keys(episodes)::int FROM content_read_model WHERE slug = 'arcane' LIMIT 1",
            Integer.class);
        assertThat(seasons).isEqualTo(1);
    }

    @Test
    @DisplayName("reads are served from the projection: editing it changes the API, the write tables do not")
    void readsComeFromTheProjection() {
        jdbc.update("""
            UPDATE content_read_model
            SET summary = jsonb_set(summary, '{title}', '"Mutated Title"')
            WHERE slug = 'dune-part-two'
            """);

        PageResponse<ContentSummaryDto> page =
            queryService.listMovies(ContentFilter.empty(), "releaseYear,desc", 0, 50);

        assertThat(page.content())
            .filteredOn(item -> item.slug().equals("dune-part-two"))
            .singleElement()
            .extracting(ContentSummaryDto::title)
            .isEqualTo("Mutated Title");

        // Untouched on the write side: the projection is what the API reads.
        assertThat(jdbc.queryForObject(
            "SELECT title FROM content WHERE slug = 'dune-part-two'", String.class))
            .isEqualTo("Dune: Part Two");
    }

    @Test
    @DisplayName("a write projects in the same transaction: create, update and delete are visible at once")
    void writesProjectInTheSameTransaction() {
        ContentDetailDto created = commandService.create(new ContentCreateRequest(
            ContentType.MOVIE, "Projection Test", null, "projection-test",
            "Created to observe the projection.", null, 2026, "PG-13", 100,
            null, null, null, new BigDecimal("6.0"), 1,
            ContentStatus.PUBLISHED, Set.of("drama"), Map.of()));

        UUID id = created.id();
        assertThat(readModelTitle(id)).isEqualTo("Projection Test");
        assertThat(queryService.getBySlug("projection-test").title()).isEqualTo("Projection Test");

        commandService.update(id, new ContentUpdateRequest(
            "Projection Test v2", null, "Updated.", null, 2026, "PG-13", 100,
            null, null, null, new BigDecimal("6.5"), 2,
            ContentStatus.PUBLISHED, Set.of("drama"), Map.of()));

        assertThat(readModelTitle(id)).isEqualTo("Projection Test v2");
        assertThat(queryService.getBySlug("projection-test").title()).isEqualTo("Projection Test v2");

        commandService.delete(id);

        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM content_read_model WHERE content_id = ?", Long.class, id))
            .isZero();
        assertThat(readRepository.findDetailBySlug("projection-test")).isEmpty();
    }

    private String readModelTitle(UUID id) {
        return jdbc.queryForObject(
            "SELECT summary->>'title' FROM content_read_model WHERE content_id = ?", String.class, id);
    }
}
