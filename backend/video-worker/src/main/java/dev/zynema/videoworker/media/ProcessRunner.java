package dev.zynema.videoworker.media;

import java.io.IOException;

/**
 * Starts the external process.
 *
 * <p>A seam, not an abstraction: the transcoder runs FFmpeg and FFprobe, and
 * testing its timeout, failure and no-audio paths should not require the
 * binaries (or a working OS process) to exist. Production uses
 * {@link #system()}; tests inject a fake.
 */
@FunctionalInterface
public interface ProcessRunner {

    Process start(ProcessBuilder builder) throws IOException;

    static ProcessRunner system() {
        return ProcessBuilder::start;
    }
}
