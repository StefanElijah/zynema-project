package dev.zynema.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The video pipeline announcing where a rendition lives.
 *
 * <p>Validated as a <strong>key</strong>, not a URL: no scheme, no host, no
 * query string. A signed URL would expire in the database, and playback builds
 * those per session from this key (ADR-0024).
 */
public record HlsPathRequest(
    @NotBlank
    @Size(max = 500)
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9/._-]*$",
        message = "must be an object key: letters, digits, '/', '.', '_' or '-' only")
    String hlsPath
) {
}
