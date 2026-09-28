package dev.zynema.bff.dto;

/**
 * A genre as the frontend needs it: something to display and something to
 * filter by, not the catalogue's internal representation.
 */
public record GenreRef(String slug, String name) {
}
