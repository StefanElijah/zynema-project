package dev.zynema.videoworker.media;

import java.nio.file.Path;

/**
 * The pipeline's engine, behind an interface so the orchestration is testable
 * without FFmpeg: the real implementation shells out to the binary that only
 * the worker image ships.
 */
public interface Transcoder {

    /**
     * Produces the HLS ladder for {@code source} under {@code outputDirectory}:
     * {@code master.m3u8} plus one directory per rendition.
     */
    void transcode(Path source, Path outputDirectory);
}
