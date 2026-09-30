package dev.zynema.videoworker.storage;

import dev.zynema.videoworker.config.WorkerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.time.Duration;
import java.util.List;

/**
 * Creates the two buckets the pipeline uses, idempotently.
 *
 * <p>This is why the platform does not need MinIO's {@code mc} image: the
 * storage initialisation is a mode of the worker itself
 * ({@code --init-storage}), so bucket creation goes through the same AWS SDK
 * and the same credentials as every other storage operation.
 *
 * <p>The buckets stay private. HLS segments are served through playback's
 * short-lived presigned URLs (ADR-0024), so there is no anonymous-read policy
 * to configure here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BucketInitializer {

    private static final int ATTEMPTS = 30;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    private final S3Client client;
    private final WorkerProperties properties;

    /**
     * Retries until the server answers: the initialiser runs right after the
     * container starts, and MinIO takes a moment to accept connections. The
     * retry lives here because the Chainguard image ships no shell utilities,
     * so a compose healthcheck cannot do it for us.
     */
    public void initialize() {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                List.of(properties.storage().sourceBucket(), properties.storage().hlsBucket())
                    .forEach(this::createIfMissing);
                log.info("Storage ready: buckets '{}' and '{}' exist",
                    properties.storage().sourceBucket(), properties.storage().hlsBucket());
                return;
            } catch (RuntimeException notYet) {
                lastFailure = notYet;
                log.debug("Storage not ready (attempt {}/{}): {}", attempt, ATTEMPTS, notYet.getMessage());
                sleep();
            }
        }
        throw new IllegalStateException("Storage did not become ready at "
            + properties.storage().endpoint(), lastFailure);
    }

    private void sleep() {
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for storage", ex);
        }
    }

    private void createIfMissing(String bucket) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.debug("Bucket '{}' already exists", bucket);
        } catch (NoSuchBucketException missing) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created bucket '{}'", bucket);
        }
    }
}
