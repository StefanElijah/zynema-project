package dev.zynema.catalog.repository;

import dev.zynema.catalog.domain.Content;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentRepository extends JpaRepository<Content, UUID>, JpaSpecificationExecutor<Content> {

    boolean existsBySlug(String slug);

    /**
     * Detail lookup. {@code genres} is the only collection safe to fetch here:
     * seasons and credits are loaded by their own repositories to avoid
     * multiple-bag fetches.
     */
    @EntityGraph(attributePaths = {"genres"})
    Optional<Content> findBySlug(String slug);

    /**
     * Batch-loads genres for a page of contents. List endpoints paginate
     * without a collection fetch (no in-memory pagination) and then call this
     * once per page, so we get one extra query instead of N+1.
     */
    @EntityGraph(attributePaths = {"genres"})
    @Query("SELECT c FROM Content c WHERE c.id IN :ids")
    List<Content> findAllWithGenresByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * PostgreSQL full-text search over title and original title.
     * Returns ids only: the caller batch-loads the entities so the search
     * itself never triggers a collection fetch under pagination.
     */
    @Query(
        value = """
            SELECT c.id FROM content c
            WHERE to_tsvector('simple', c.title || ' ' || coalesce(c.original_title, ''))
                  @@ plainto_tsquery('simple', :q)
            ORDER BY c.popularity DESC
            """,
        countQuery = """
            SELECT count(*) FROM content c
            WHERE to_tsvector('simple', c.title || ' ' || coalesce(c.original_title, ''))
                  @@ plainto_tsquery('simple', :q)
            """,
        nativeQuery = true
    )
    Page<UUID> searchIdsByTitle(@Param("q") String query, Pageable pageable);

    @Override
    @Nullable
    Page<Content> findAll(@Nullable org.springframework.data.jpa.domain.Specification<Content> spec, Pageable pageable);
}
