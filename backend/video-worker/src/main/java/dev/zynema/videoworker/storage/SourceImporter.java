package dev.zynema.videoworker.storage;

import dev.zynema.videoworker.config.WorkerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Uploads a local file into the source bucket.
 *
 * <p>This exists because the platform does not ship MinIO's client: getting the
 * Creative-Commons samples into storage is another job for the same AWS SDK and
 * the same credentials, not a second tool to install.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SourceImporter {

    private final S3Client client;
    private final WorkerProperties properties;

    public void importFile(Path file, String key) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Source file does not exist: " + file);
        }
        try {
            client.putObject(PutObjectRequest.builder()
                    .bucket(properties.storage().sourceBucket())
                    .key(key)
                    .contentType(ContentTypes.of(file.getFileName().toString()))
                    .metadata(Map.of("size", String.valueOf(Files.size(file))))
                    .build(),
                RequestBody.fromFile(file));
            log.info("Imported {} ({} bytes) to s3://{}/{}",
                file.getFileName(), Files.size(file), properties.storage().sourceBucket(), key);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Could not read " + file, ex);
        }
    }
}
