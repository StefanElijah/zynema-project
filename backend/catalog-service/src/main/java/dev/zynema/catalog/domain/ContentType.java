package dev.zynema.catalog.domain;

/**
 * Discriminator for the unified {@link Content} table.
 * Movies and series share one table so search, ranking and metadata stay
 * uniform (see ADR-0012).
 */
public enum ContentType {
    MOVIE,
    SERIES
}
