package dev.zynema.bff.dto;

/**
 * The kind of catalogue entry, mirroring catalog-service's {@code ContentType}.
 * The BFF talks JSON, so the names are the contract between the two.
 */
public enum ContentKind {
    MOVIE,
    SERIES
}
