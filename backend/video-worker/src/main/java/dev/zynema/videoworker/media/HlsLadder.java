package dev.zynema.videoworker.media;

import dev.zynema.videoworker.config.Rendition;
import dev.zynema.videoworker.config.WorkerProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Builds the single FFmpeg invocation that produces the whole ladder.
 *
 * <p>One pass, not one process per rung: FFmpeg decodes the source once and
 * fans it out to four encoders through {@code split}, so a 10-minute asset
 * costs one decode instead of four. The variants are written as
 * {@code <name>/playlist.m3u8} with a {@code master.m3u8} on top.
 *
 * <p>Segment boundaries are enforced, not hoped for: {@code -sc_threshold 0}
 * disables scene-cut keyframes and {@code -force_key_frames} places an IDR
 * exactly every {@code segmentSeconds}, which is what keeps every rendition
 * segment-aligned so a player can switch quality mid-segment.
 *
 * <p>This class is pure: it builds arguments and touches no process, which is
 * why the ladder itself is unit-testable without an FFmpeg binary.
 */
public final class HlsLadder {

    private HlsLadder() {
    }

    /**
     * @param hasAudio when the source has no audio track, a silent one is
     *                 generated so every variant still carries an audio
     *                 rendition (players expect it)
     */
    public static List<String> ffmpegCommand(WorkerProperties.Ffmpeg settings, Path input,
                                             boolean hasAudio, Path outputDirectory) {
        List<Rendition> renditions = settings.renditions();
        if (renditions.isEmpty()) {
            throw new IllegalStateException("The ladder has no renditions configured");
        }

        List<String> command = new ArrayList<>(List.of(
            settings.binary(), "-hide_banner", "-y", "-i", unix(input)));
        if (!hasAudio) {
            command.addAll(List.of("-f", "lavfi", "-i",
                "anullsrc=channel_layout=stereo:sample_rate=48000"));
        }

        command.addAll(List.of("-filter_complex", filterGraph(renditions)));

        for (int i = 0; i < renditions.size(); i++) {
            Rendition rendition = renditions.get(i);
            command.addAll(List.of(
                "-map", "[v" + i + "]",
                "-c:v:" + i, "libx264",
                "-preset:v:" + i, "veryfast",
                "-profile:v:" + i, "main",
                "-pix_fmt:v:" + i, "yuv420p",
                "-b:v:" + i, rendition.videoBitrateKbps() + "k",
                "-maxrate:v:" + i, rendition.maxrateKbpsOrDefault() + "k",
                "-bufsize:v:" + i, rendition.bufsizeKbpsOrDefault() + "k",
                "-sc_threshold:v:" + i, "0",
                "-force_key_frames:v:" + i,
                "expr:gte(t,n_forced*" + settings.segmentSeconds() + ")"));
        }

        String audioInput = hasAudio ? "0:a" : "1:a";
        for (int i = 0; i < renditions.size(); i++) {
            command.addAll(List.of(
                "-map", audioInput,
                "-c:a:" + i, "aac",
                "-ac:a:" + i, "2",
                "-b:a:" + i, renditions.get(i).audioBitrateKbps() + "k"));
        }

        command.addAll(List.of(
            "-f", "hls",
            "-hls_time", String.valueOf(settings.segmentSeconds()),
            "-hls_playlist_type", "vod",
            "-hls_flags", "independent_segments",
            "-hls_segment_filename", unix(outputDirectory.resolve("%v/segment_%05d.ts")),
            "-master_pl_name", "master.m3u8",
            "-var_stream_map", variantMap(renditions),
            unix(outputDirectory.resolve("%v/playlist.m3u8"))));

        return command;
    }

    private static String filterGraph(List<Rendition> renditions) {
        StringBuilder graph = new StringBuilder("[0:v]split=").append(renditions.size());
        for (int i = 0; i < renditions.size(); i++) {
            graph.append("[s").append(i).append("]");
        }
        graph.append(';');
        for (int i = 0; i < renditions.size(); i++) {
            Rendition rendition = renditions.get(i);
            graph.append("[s").append(i).append("]scale=w=").append(rendition.width())
                .append(":h=").append(rendition.height())
                .append(":force_original_aspect_ratio=decrease,")
                .append("pad=").append(rendition.width()).append(':').append(rendition.height())
                .append(":(ow-iw)/2:(oh-ih)/2[v").append(i).append(']');
            if (i < renditions.size() - 1) {
                graph.append(';');
            }
        }
        return graph.toString();
    }

    private static String variantMap(List<Rendition> renditions) {
        return IntStream.range(0, renditions.size())
            .mapToObj(i -> "v:%d,a:%d,name:%s".formatted(i, i, renditions.get(i).name()))
            .collect(Collectors.joining(" "));
    }

    /** FFmpeg on Linux wants forward slashes; Windows paths reach it as-is. */
    private static String unix(Path path) {
        return path.toString().replace('\\', '/');
    }
}
