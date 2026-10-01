package dev.zynema.videoworker.media;

import dev.zynema.videoworker.config.Rendition;
import dev.zynema.videoworker.config.WorkerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The transcoder's failure paths, without FFmpeg: the seam makes the process
 * a fake, so the timeout, the exit-code branch, the synthetic audio and the
 * error tail are all reachable.
 */
class FfmpegTranscoderTest {

    private static final List<Rendition> LADDER = List.of(
        new Rendition("240p", 426, 240, 400, 64, null, null),
        new Rendition("720p", 1280, 720, 2800, 128, null, null));

    private static final WorkerProperties.Ffmpeg SETTINGS =
        new WorkerProperties.Ffmpeg("ffmpeg", 6, Duration.ofMinutes(30), LADDER);

    private static final WorkerProperties PROPERTIES =
        new WorkerProperties(null, null, SETTINGS, null);

    @TempDir
    Path workspace;

    private final Deque<Process> processes = new ArrayDeque<>();
    private final List<List<String>> commands = new ArrayList<>();

    private final ProcessRunner runner = builder -> {
        commands.add(builder.command());
        Process next = processes.poll();
        if (next == null) {
            throw new IOException("no process configured");
        }
        return next;
    };

    private FfmpegTranscoder transcoder;

    @BeforeEach
    void setUp() {
        transcoder = new FfmpegTranscoder(PROPERTIES, runner);
    }

    @Test
    void runsTheLadderAndDeletesTheLogOnSuccess() throws Exception {
        processes.add(probe("0\n"));
        processes.add(transcode(0));

        transcoder.transcode(Path.of("source.mp4"), workspace);

        assertThat(commands).hasSize(2);
        assertThat(commands.get(0)).startsWith("ffprobe");
        assertThat(commands.get(1)).startsWith("ffmpeg");
        assertThat(commands.get(1)).doesNotContain("anullsrc=channel_layout=stereo:sample_rate=48000");
        assertThat(Files.exists(workspace.resolve("240p"))).isTrue();
        assertThat(Files.exists(workspace.resolve("720p"))).isTrue();
        assertThat(Files.exists(workspace.resolve("ffmpeg.log"))).isFalse();
    }

    @Test
    void synthesisesSilenceWhenTheSourceHasNoAudio() throws Exception {
        processes.add(probe(""));
        processes.add(transcode(0));

        transcoder.transcode(Path.of("source.mp4"), workspace);

        assertThat(commands.get(1)).contains("anullsrc=channel_layout=stereo:sample_rate=48000");
    }

    @Test
    void killsAProcessThatNeverFinishes() throws Exception {
        Process stuck = mock(Process.class);
        when(stuck.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false);
        processes.add(probe("0\n"));
        processes.add(stuck);

        assertThatThrownBy(() -> transcoder.transcode(Path.of("source.mp4"), workspace))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("timed out");

        verify(stuck).destroyForcibly();
    }

    @Test
    void reportsTheExitCodeAndTheTailOfTheLog() throws Exception {
        processes.add(probe("0\n"));
        Process failed = transcode(1);
        processes.add(failed);
        Path logFile = workspace.resolve("ffmpeg.log");
        Files.writeString(logFile, "line one\nline two\nConversion failed!\n");

        assertThatThrownBy(() -> transcoder.transcode(Path.of("source.mp4"), workspace))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exit code 1")
            .hasMessageContaining("Conversion failed!");
    }

    @Test
    void saysSoWhenThereIsNoLogToRead() throws Exception {
        processes.add(probe("0\n"));
        processes.add(transcode(1));

        assertThatThrownBy(() -> transcoder.transcode(Path.of("source.mp4"), workspace))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("(no log)");
    }

    @Test
    void wrapsAFailureToStartTheProcess() throws Exception {
        processes.add(probe("0\n"));

        assertThatThrownBy(() -> transcoder.transcode(Path.of("source.mp4"), workspace))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not run FFmpeg");
    }

    @Test
    void keepsTheInterruptFlagWhenWaitingIsInterrupted() throws Exception {
        Process interrupted = mock(Process.class);
        when(interrupted.waitFor(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
        processes.add(probe("0\n"));
        processes.add(interrupted);

        assertThatThrownBy(() -> transcoder.transcode(Path.of("source.mp4"), workspace))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Interrupted");

        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void wrapsAFailureToStartTheProbe() {
        // The probe is the first process the transcoder starts; a runner with
        // nothing queued makes that first start fail.
        assertThatThrownBy(() -> transcoder.transcode(Path.of("source.mp4"), workspace))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not run ffprobe");
    }

    private Process probe(String output) throws Exception {
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes()));
        when(process.waitFor()).thenReturn(0);
        return process;
    }

    private Process transcode(int exitCode) throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(exitCode);
        return process;
    }
}
