package dev.zynema.videoworker.storage;

import dev.zynema.videoworker.config.WorkerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * S3-compatible object storage (MinIO) through the AWS SDK.
 *
 * <p>The same client serves both ends of the pipeline: it downloads the source
 * from the private {@code zynema-videos} bucket and uploads the ladder to
 * {@code zynema-hls}. Path-style access is on because that is how MinIO
 * addresses buckets.
 *
 * <p>Uploads are a full replace: the prefix is deleted first so a re-run with a
 * shorter asset does not leave orphan segments behind that a player could
 * still request through an old playlist.
 */
@Slf4j
@Component
public class ObjectStorage {

    private final WorkerProperties.Storage settings;
    private final S3Client client;

    public ObjectStorage(S3Client client, WorkerProperties properties) {
        this.settings = properties.storage();
        this.client = client;
    }

    public Path download(String key, Path target) {
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create the workspace: " + ex.getMessage(), ex);
        }
        client.getObject(GetObjectRequest.builder()
            .bucket(settings.sourceBucket())
            .key(key)
            .build(), ResponseTransformer.toFile(target));
        log.info("Downloaded s3://{}/{} ({} bytes)", settings.sourceBucket(), key, size(target));
        return target;
    }

    public int upload(String prefix, Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            List<Path> uploadable = files.filter(Files::isRegularFile).toList();
            for (Path file : uploadable) {
                String key = prefix + "/" + directory.relativize(file).toString().replace('\\', '/');
                client.putObject(PutObjectRequest.builder()
                    .bucket(settings.hlsBucket())
                    .key(key)
                    .contentType(contentType(file))
                    .build(), RequestBody.fromFile(file));
            }
            log.info("Uploaded {} objects to s3://{}/{}", uploadable.size(), settings.hlsBucket(), prefix);
            return uploadable.size();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not upload the ladder: " + ex.getMessage(), ex);
        }
    }

    public int deletePrefix(String prefix) {
        int deleted = 0;
        for (var page : client.listObjectsV2Paginator(ListObjectsV2Request.builder()
            .bucket(settings.hlsBucket())
            .prefix(prefix + "/")
            .build())) {
            List<ObjectIdentifier> keys = page.contents().stream()
                .map(object -> ObjectIdentifier.builder().key(object.key()).build())
                .toList();
            if (!keys.isEmpty()) {
                client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(settings.hlsBucket())
                    .delete(Delete.builder().objects(keys).build())
                    .build());
                deleted += keys.size();
            }
        }
        if (deleted > 0) {
            log.info("Removed {} objects left over under {}/", deleted, prefix);
        }
        return deleted;
    }

    private String contentType(Path file) {
        return ContentTypes.of(file.getFileName().toString());
    }

    private long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException ex) {
            return -1;
        }
    }
}
