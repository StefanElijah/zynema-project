package dev.zynema.playback.storage;

import dev.zynema.playback.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.util.Optional;

/**
 * Reads renditions and signs their URLs.
 *
 * <p>The signature is computed for the <strong>internal</strong> endpoint
 * because that is the host MinIO will see: nginx presents it on the way in
 * (ADR-0024). The URL handed to a client is the same object through the public
 * base, which is a different address for the very same signature.
 */
@Component
@RequiredArgsConstructor
public class HlsStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    public Optional<String> read(String key) {
        try {
            String manifest = client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.hlsBucket())
                    .key(key)
                    .build())
                .asUtf8String();
            return Optional.of(manifest);
        } catch (NoSuchKeyException missing) {
            return Optional.empty();
        }
    }

    /** Exposed for tests: a tiny object standing in for a segment. */
    public void write(String key, byte[] content, String contentType) {
        client.putObject(PutObjectRequest.builder()
            .bucket(properties.hlsBucket())
            .key(key)
            .contentType(contentType)
            .build(), RequestBody.fromBytes(content));
    }

    public String presignedUrl(String key) {
        URL signed = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(properties.segmentTtl())
                .getObjectRequest(request -> request
                    .bucket(properties.hlsBucket())
                    .key(key))
                .build())
            .url();
        return publicForm(signed);
    }

    public Duration segmentTtl() {
        return properties.segmentTtl();
    }

    /**
     * Keeps the signed path and query, swaps the origin for the public edge.
     * The signature stays valid because MinIO never sees this address: nginx
     * forwards the request with the host the signature covers.
     *
     * <p>Concatenated, not rebuilt through {@code new URI(...)}: the multi-arg
     * constructor percent-encodes its components, and re-encoding an already
     * encoded query turns {@code %2F} into {@code %252F} — MinIO then rejects a
     * perfectly valid signature with "the Credential is mal-formed".
     */
    private String publicForm(URL signed) {
        String publicBase = StringUtils.trimTrailingCharacter(properties.publicBaseUrl(), '/');
        String query = signed.getQuery();
        return publicBase + signed.getPath() + (query == null ? "" : "?" + query);
    }
}
