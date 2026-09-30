package dev.zynema.videoworker;

import dev.zynema.videoworker.config.WorkerProperties;
import dev.zynema.videoworker.pipeline.JobResult;
import dev.zynema.videoworker.pipeline.VideoPipeline;
import dev.zynema.videoworker.storage.BucketInitializer;
import dev.zynema.videoworker.storage.SourceImporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * The video worker: a command-line job, not a service.
 *
 * <p>It exists as its own process because transcoding is heavy, bursty and
 * needs a binary the services must not carry. Fase 7 turns the trigger into a
 * Kafka event without touching the pipeline itself.
 *
 * <p>Three modes:
 * <pre>
 * java -jar zynema-video-worker.jar --init-storage
 * java -jar zynema-video-worker.jar \
 *   --import-source=/samples/sintel-trailer.mp4 --key=samples/sintel-trailer.mp4
 * java -jar zynema-video-worker.jar \
 *   --source=samples/sintel-trailer.mp4 --content=dune-part-two
 * </pre>
 */
@Slf4j
@SpringBootApplication
@EnableConfigurationProperties(WorkerProperties.class)
@RequiredArgsConstructor
public class VideoWorkerApplication implements CommandLineRunner {

    private final VideoPipeline pipeline;
    private final BucketInitializer bucketInitializer;
    private final SourceImporter sourceImporter;

    public static void main(String[] args) {
        SpringApplication.run(VideoWorkerApplication.class, args);
    }

    @Override
    public void run(String... args) {
        if (Arrays.asList(args).contains("--init-storage")) {
            bucketInitializer.initialize();
            return;
        }

        String importSource = option(args, "import-source");
        if (importSource != null) {
            String key = option(args, "key");
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("--import-source requires --key=<object key>");
            }
            sourceImporter.importFile(Path.of(importSource), key);
            return;
        }

        WorkerArguments arguments = WorkerArguments.parse(args);
        log.info("Video job started: {} from source '{}'", arguments.target(), arguments.sourceKey());

        JobResult result = pipeline.run(arguments.target(), arguments.sourceKey());

        log.info("Video job finished in {}s: {} objects uploaded, master at {}",
            result.took().toSeconds(), result.uploadedObjects(), result.masterKey());
    }

    private static String option(String[] args, String name) {
        String prefix = "--" + name + "=";
        return Arrays.stream(args)
            .filter(arg -> arg.startsWith(prefix))
            .map(arg -> arg.substring(prefix.length()))
            .findFirst()
            .orElse(null);
    }
}
