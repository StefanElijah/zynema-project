package dev.zynema.common.dto;

import java.util.List;

/**
 * Generic paginated response envelope used by all list endpoints.
 * Versioned: 1.0 — initial shape.
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {}
