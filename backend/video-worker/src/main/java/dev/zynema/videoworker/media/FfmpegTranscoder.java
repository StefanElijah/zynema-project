package dev.zynema.videoworker.media;

import dev.zynema.videoworker.config.Rendition;
import dev.zynema.videoworker.config.WorkerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs FFmpeg as a child process.
 *
 * <p>Three details that matter in a batch job:
 * <ul>
 *   <li><strong>FFprobe first.</strong> A source without an audio track would
 *       make the variant map reference a stream that does not exist; detecting
 *       it lets the ladder synthesise silence instead of failing.</li>
 *   <li><strong>Variant directories are pre-created</strong>, because FFmpeg
 *       does not create directories named by {@code %v}.</li>
 *   <li><strong>Output goes to a log file and the process has a timeout.</strong>
 *       Draining the stream avoids a full pipe blocking the child, and a hung
 *       transcode must not hold the job forever.</li>
 * </ul>
 */
@Slf4j
@Component
public class FfmpegTranscoder implements Transcoder {

    private final WorkerProperties.Ffmpeg settings;
    private final ProcessRunner runner;

    public FfmpegTranscoder(WorkerProperties properties, ProcessRunner runner) {
        this.settings = properties.ffmpeg();
        this.runner = runner;
    }

    @Override
    public void transcode(Path source, Path outputDirectory) {
        try {
            for (Rendition rendition : settings.renditions()) {
                Files.createDirectories(outputDirectory.resolve(rendition.name()));
            }

            boolean hasAudio = hasAudioTrack(source);
            List<String> command = HlsLadder.ffmpegCommand(settings, source, hasAudio, outputDirectory);
            log.info("Transcoding {} into {} renditions ({} audio)",
                source.getFileName(), settings.renditions().size(), hasAudio ? "with" : "synthetic");

            Path logFile = outputDirectory.resolve("ffmpeg.log");
            Process process = runner.start(new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile()));

            boolean finished = process.waitFor(settings.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("FFmpeg timed out after %s; see %s"
                    .formatted(settings.timeout(), logFile));
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("FFmpeg failed with exit code %d: %s"
                    .formatted(process.exitValue(), tail(logFile)));
            }
            Files.deleteIfExists(logFile);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not run FFmpeg: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while transcoding", ex);
        }
    }

    private boolean hasAudioTrack(Path source) {
        try {
            Process probe = runner.start(new ProcessBuilder("ffprobe", "-v", "error",
                "-select_streams", "a", "-show_entries", "stream=index", "-of", "csv=p=0",
                source.toString())
                .redirectErrorStream(true));
            String output = new String(probe.getInputStream().readAllBytes());
            probe.waitFor();
            return !output.isBlank();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not run ffprobe: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while probing", ex);
        }
    }

    private String tail(Path logFile) {
        try {
            List<String> lines = Files.readAllLines(logFile);
            return String.join(" | ", lines.subList(Math.max(0, lines.size() - 5), lines.size()));
        } catch (IOException ex) {
            return "(no log)";
        }
    }
}
