package dev.zynema.catalog.specification;

import dev.zynema.catalog.domain.Content;
import dev.zynema.catalog.domain.ContentStatus;
import dev.zynema.catalog.domain.ContentType;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * Composable filters for catalog listing endpoints.
 * All of them are safe to combine and none of them fetch collections, so
 * pagination stays a database concern (no in-memory pagination).
 */
public final class ContentSpecifications {

    private ContentSpecifications() {
    }

    public static Specification<Content> hasType(ContentType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Content> hasStatus(ContentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Content> hasGenre(String genreSlug) {
        return (root, query, cb) -> {
            if (genreSlug == null || genreSlug.isBlank()) {
                // Returning null means "no restriction"; without this guard the
                // predicate would compare slug = NULL and match nothing.
                return null;
            }
            if (query != null) {
                query.distinct(true);
            }
            return cb.equal(root.join("genres", JoinType.INNER).get("slug"), genreSlug);
        };
    }

    public static Specification<Content> releaseYearFrom(Integer from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("releaseYear"), from);
    }

    public static Specification<Content> releaseYearTo(Integer to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("releaseYear"), to);
    }

    public static Specification<Content> minRating(BigDecimal min) {
        return (root, query, cb) -> min == null ? null : cb.greaterThanOrEqualTo(root.get("averageRating"), min);
    }

}
