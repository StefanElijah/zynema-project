package dev.zynema.catalog.domain;

/**
 * Lifecycle of a catalog entry. Only PUBLISHED content is exposed through
 * the public read API; DRAFT/ARCHIVED stay visible to admins only.
 */
public enum ContentStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
