package dev.zynema.bff.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The pagination envelope every list endpoint returns.
 *
 * <p>Defined locally on purpose: the BFF consumes JSON over HTTP, not jars.
 * A service is free to change its internal classes without breaking the BFF,
 * and Jackson ignores the fields this record does not need.
 *
 * <p>The BFF serves it back to the SPA as its own page contract (Fase 8): the
 * generated TypeScript client types the SPA against this shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DownstreamPage<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {
}
