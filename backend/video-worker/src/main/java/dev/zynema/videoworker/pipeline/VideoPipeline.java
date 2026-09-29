package dev.zynema.videoworker.pipeline;

import dev.zynema.videoworker.catalog.CatalogAdminClient;
import dev.zynema.videoworker.config.WorkerProperties;
import dev.zynema.videoworker.media.Transcoder;
import dev.zynema.videoworker.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * One transcode job, start to finish.
 *
 * <p>The order is the interesting part: the catalogue is told about the new
 * playlist <strong>last</strong>. Until the update lands, {@code hls_path} is
 * null and playback answers "not ready"; if anything earlier fails, the content
 * simply stays unplayable instead of pointing at a half-uploaded prefix.
 *
 * <p>Re-running a job is safe: the target prefix is emptied before the new
 * ladder goes up, so no segment from a previous, longer render survives.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoPipeline {

    private final WorkerProperties properties;
    private final ObjectStorage storage;
    private final Transcoder transcoder;
    private final CatalogAdminClient catalog;

    public JobResult run(PipelineTarget target, String sourceKey) {
        Instant started = Instant.now();
        UUID targetId = resolve(target);
        String prefix = target.keyPrefix(targetId);
        Path workspace = createWorkspace();

        try {
            Path source = storage.download(sourceKey,
                workspace.resolve("source." + extension(sourceKey)));
            Path ladder = createDirectories(workspace.resolve("hls"));

            transcoder.transcode(source, ladder);

            storage.deletePrefix(prefix);
            int uploaded = storage.upload(prefix, ladder);

            String masterKey = prefix + "/master.m3u8";
            catalog.setHlsPath(target.kind(), targetId, masterKey);

            return new JobResult(target.kind(), targetId, masterKey, uploaded,
                Duration.between(started, Instant.now()));
        } finally {
            deleteRecursively(workspace);
        }
    }

    private UUID resolve(PipelineTarget target) {
        return switch (target.kind()) {
            case CONTENT -> catalog.resolveContentId(target.reference());
            case EPISODE -> UUID.fromString(target.reference());
        };
    }

    private Path createWorkspace() {
        return createDirectories(Paths.get(properties.workspace())
            .resolve("job-" + UUID.randomUUID()));
    }

    private static Path createDirectories(Path directory) {
        try {
            return Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create the directory " + directory, ex);
        }
    }

    /** The extension only matters for FFmpeg's format detection; keep it. */
    private static String extension(String sourceKey) {
        int dot = sourceKey.lastIndexOf('.');
        return dot >= 0 ? sourceKey.substring(dot + 1) : "mp4";
    }

    private void deleteRecursively(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.warn("Could not delete {}: {}", path, ex.getMessage());
                }
            });
        } catch (IOException ex) {
            log.warn("Could not clean the workspace {}: {}", directory, ex.getMessage());
        }
    }
}
