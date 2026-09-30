package dev.zynema.playback.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Where the renditions live and how long their URLs are valid.
 *
 * <p>Three addresses, one object: the <strong>endpoint</strong> is what this
 * service uses to read manifests, the <strong>presign endpoint</strong> is the
 * host the signature must be valid for (SigV4 signs the host, and MinIO
 * validates it against the host nginx presents), and the <strong>public
 * base</strong> is where a browser can reach it — the nginx edge itself. See
 * ADR-0024.
 *
 * @param segmentTtl how long a segment URL stays valid; short on purpose, the
 *                   player fetches segments right after reading the playlist
 */
@ConfigurationProperties(prefix = "zynema.playback.storage")
public record StorageProperties(
    String endpoint,
    String presignEndpoint,
    String accessKey,
    String secretKey,
    String hlsBucket,
    String publicBaseUrl,
    Duration segmentTtl,
    boolean pathStyle
) {

    /**
     * The host a signature is valid for is the host the storage server sees —
     * which is nginx's, not this service's. They are the same address inside
     * the compose network; when the service runs outside it, the edge's
     * address is set explicitly so the signature still matches.
     */
    public String presignEndpointOrDefault() {
        return presignEndpoint == null || presignEndpoint.isBlank() ? endpoint : presignEndpoint;
    }
}
