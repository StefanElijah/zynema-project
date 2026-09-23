package dev.zynema.catalog.service;

import dev.zynema.catalog.AbstractCatalogIntegrationTest;
import dev.zynema.catalog.config.CacheConfig;
import dev.zynema.catalog.domain.ContentStatus;
import dev.zynema.catalog.domain.ContentType;
import dev.zynema.catalog.dto.ContentCreateRequest;
import dev.zynema.catalog.dto.ContentDetailDto;
import dev.zynema.catalog.dto.ContentUpdateRequest;
import dev.zynema.common.exception.BusinessRuleException;
import dev.zynema.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class CatalogCommandServiceTests extends AbstractCatalogIntegrationTest {

    @Autowired
    private CatalogCommandService commandService;

    @Autowired
    private CatalogQueryService queryService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    @Test
    @DisplayName("creates a catalog entry with genres and returns the detail view")
    void createsContent() {
        ContentDetailDto created = commandService.create(new ContentCreateRequest(
            ContentType.MOVIE,
            "Zynema Test Movie",
            "Zynema Test Movie",
            "zynema-test-movie",
            "A movie created by the test suite.",
            "Testing in production.",
            2026,
            "PG-13",
            120,
            "/assets/test.jpg",
            "/assets/test-backdrop.jpg",
            null,
            new BigDecimal("7.5"),
            10,
            ContentStatus.PUBLISHED,
            Set.of("sci-fi", "drama"),
            Map.of("country", "AR")
        ));

        assertThat(created.slug()).isEqualTo("zynema-test-movie");
        assertThat(created.genres()).extracting("slug").containsExactlyInAnyOrder("sci-fi", "drama");
        assertThat(created.metadata()).containsEntry("country", "AR");
        assertThat(queryService.getBySlug("zynema-test-movie").title()).isEqualTo("Zynema Test Movie");
    }

    @Test
    @DisplayName("rejects a duplicate slug")
    void rejectsDuplicateSlug() {
        assertThatThrownBy(() -> commandService.create(new ContentCreateRequest(
            ContentType.MOVIE, "Duplicate", null, "dune-part-two",
            null, null, 2026, null, null, null, null, null, null, 0,
            ContentStatus.PUBLISHED, Set.of(), Map.of()
        )))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("already in use");
    }

    @Test
    @DisplayName("rejects unknown genres and names them")
    void rejectsUnknownGenres() {
        assertThatThrownBy(() -> commandService.create(new ContentCreateRequest(
            ContentType.MOVIE, "Bad Genres", null, "bad-genres",
            null, null, 2026, null, null, null, null, null, null, 0,
            ContentStatus.PUBLISHED, Set.of("sci-fi", "not-a-genre"), Map.of()
        )))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("not-a-genre");
    }

    @Test
    @DisplayName("updates an existing entry")
    void updatesContent() {
        UUID id = queryService.getBySlug("dune-part-two").id();

        ContentDetailDto updated = commandService.update(id, new ContentUpdateRequest(
            "Dune: Part Two (remastered)", "Dune: Part Two", "Updated synopsis.",
            "Long live the fighters.", 2024, "PG-13", 170,
            null, null, null, new BigDecimal("8.6"), 100,
            ContentStatus.PUBLISHED, Set.of("sci-fi"), Map.of("remastered", true)
        ));

        assertThat(updated.title()).isEqualTo("Dune: Part Two (remastered)");
        assertThat(updated.runtimeMinutes()).isEqualTo(170);
        assertThat(updated.genres()).extracting("slug").containsExactly("sci-fi");
        assertThat(updated.metadata()).containsEntry("remastered", true);
    }

    @Test
    @DisplayName("updating an unknown id raises ResourceNotFoundException")
    void updateUnknownIdRaisesNotFound() {
        assertThatThrownBy(() -> commandService.update(UUID.randomUUID(), new ContentUpdateRequest(
            "Nope", null, null, null, null, null, null,
            null, null, null, null, null, null, Set.of(), Map.of()
        )))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deletes an entry")
    void deletesContent() {
        UUID id = queryService.getBySlug("tron-ares").id();

        commandService.delete(id);

        assertThatThrownBy(() -> queryService.getBySlug("tron-ares"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deleting an unknown id raises ResourceNotFoundException")
    void deleteUnknownIdRaisesNotFound() {
        assertThatThrownBy(() -> commandService.delete(UUID.randomUUID()))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("writes evict the read caches")
    void writesEvictCaches() {
        queryService.getBySlug("interstellar");
        assertThat(cacheManager.getCache(CacheConfig.CONTENT_DETAIL).get("interstellar")).isNotNull();

        commandService.create(new ContentCreateRequest(
            ContentType.SERIES, "Cache Eviction Show", null, "cache-eviction-show",
            null, null, 2026, null, null, null, null, null, null, 1,
            ContentStatus.PUBLISHED, Set.of(), Map.of()
        ));

        assertThat(cacheManager.getCache(CacheConfig.CONTENT_DETAIL).get("interstellar")).isNull();
        assertThat(cacheManager.getCache(CacheConfig.CONTENT_LIST).get("SERIES:*|*|*|*:popularity,desc:0:20")).isNull();
    }
}
