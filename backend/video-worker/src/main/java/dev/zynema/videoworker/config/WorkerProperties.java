package dev.zynema.videoworker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Everything the worker needs to run one job: where the objects live, who it is
 * when it talks to the catalogue, and how the ladder is built.
 *
 * <p>The FFmpeg settings are configuration, not code: the ladder is a product
 * decision (which qualities, at which bitrates) and changing it should not
 * require a rebuild of the pipeline.
 */
@ConfigurationProperties(prefix = "zynema.worker")
public record WorkerProperties(Storage storage, Catalog catalog, Ffmpeg ffmpeg, String workspace) {

    public record Storage(
        String endpoint,
        String accessKey,
        String secretKey,
        String sourceBucket,
        String hlsBucket,
        boolean pathStyle
    ) {
    }

    public record Catalog(String baseUrl, Token token) {

        public record Token(String url, String clientId, String clientSecret) {
        }
    }

    public record Ffmpeg(String binary, int segmentSeconds, Duration timeout, List<Rendition> renditions) {
    }
}
