package dev.zynema.bff.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The pagination envelope every list endpoint returns
 * ({@code PageResponse} in zynema-common).
 *
 * <p>Defined locally on purpose: the BFF consumes JSON over HTTP, not jars.
 * A service is free to change its internal classes without breaking the BFF,
 * and Jackson ignores the fields this record does not need.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DownstreamPage<T>(List<T> content) {
}
