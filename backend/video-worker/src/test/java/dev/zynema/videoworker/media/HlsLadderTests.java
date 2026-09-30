package dev.zynema.videoworker.media;

import dev.zynema.videoworker.config.Rendition;
import dev.zynema.videoworker.config.WorkerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The ladder is the contract with the player: four aligned renditions and a
 * master playlist. It is pure data, so it is tested without FFmpeg.
 */
class HlsLadderTests {

    private static final List<Rendition> LADDER = List.of(
        new Rendition("240p", 426, 240, 400, 64, null, null),
        new Rendition("720p", 1280, 720, 2800, 128, null, null));

    private static final WorkerProperties.Ffmpeg SETTINGS =
        new WorkerProperties.Ffmpeg("ffmpeg", 6, Duration.ofMinutes(30), LADDER);

    @Test
    @DisplayName("one FFmpeg pass produces every rendition plus the master playlist")
    void buildsOnePassLadder() {
        List<String> command = HlsLadder.ffmpegCommand(SETTINGS, Path.of("/work/source.mp4"), true, Path.of("/work/hls"));

        assertThat(command).containsSubsequence("-hide_banner", "-y", "-i", "/work/source.mp4");
        assertThat(command).contains("-filter_complex");
        assertThat(joined(command)).contains("[0:v]split=2[s0][s1]");
        assertThat(joined(command)).contains("[s0]scale=w=426:h=240");
        assertThat(joined(command)).contains("[s1]scale=w=1280:h=720");
        assertThat(command).containsSubsequence("-map", "[v0]", "-map", "[v1]");
        assertThat(command).containsSubsequence("-preset:v:0", "veryfast");
    }

    @Test
    @DisplayName("segment boundaries are forced, not left to scene detection")
    void forcesKeyframesOnSegmentBoundaries() {
        List<String> command = HlsLadder.ffmpegCommand(SETTINGS, Path.of("/work/source.mp4"), true, Path.of("/work/hls"));

        assertThat(joined(command)).contains("-sc_threshold:v:0 0");
        assertThat(joined(command)).contains("-force_key_frames:v:0 expr:gte(t,n_forced*6)");
        assertThat(command).containsSubsequence("-hls_time", "6");
        assertThat(command).containsSubsequence("-hls_playlist_type", "vod");
        assertThat(command).containsSubsequence("-hls_flags", "independent_segments");
        assertThat(command).containsSubsequence("-master_pl_name", "master.m3u8");
    }

    @Test
    @DisplayName("the variant map names the directories the playlists live in")
    void namesVariantsAndSegments() {
        List<String> command = HlsLadder.ffmpegCommand(SETTINGS, Path.of("/work/source.mp4"), true, Path.of("/work/hls"));

        assertThat(command).containsSubsequence("-var_stream_map", "v:0,a:0,name:240p v:1,a:1,name:720p");
        assertThat(joined(command)).contains("-hls_segment_filename /work/hls/%v/segment_%05d.ts");
        assertThat(command).last().isEqualTo("/work/hls/%v/playlist.m3u8");
    }

    @Test
    @DisplayName("a source without audio gets silence instead of a failed run")
    void synthesisesAudioWhenTheSourceHasNone() {
        List<String> command = HlsLadder.ffmpegCommand(SETTINGS, Path.of("/work/source.mp4"), false, Path.of("/work/hls"));

        assertThat(joined(command)).contains("anullsrc=channel_layout=stereo:sample_rate=48000");
        assertThat(command).containsSubsequence("-map", "1:a");
        assertThat(joined(command)).doesNotContain("-map 0:a");
    }

    @Test
    @DisplayName("an existing audio track is reused for every rendition")
    void reusesTheSourceAudio() {
        List<String> command = HlsLadder.ffmpegCommand(SETTINGS, Path.of("/work/source.mp4"), true, Path.of("/work/hls"));

        assertThat(command.stream().filter("-map"::equals).count()).isEqualTo(4); // 2 video + 2 audio
        assertThat(joined(command)).contains("-b:a:1 128k");
    }

    @Test
    @DisplayName("the derived limits keep maxrate and bufsize proportional to the bitrate")
    void derivesMaxrateAndBufsize() {
        List<String> command = HlsLadder.ffmpegCommand(SETTINGS, Path.of("/work/source.mp4"), true, Path.of("/work/hls"));

        assertThat(joined(command)).contains("-maxrate:v:0 440k -bufsize:v:0 800k");
    }

    @Test
    @DisplayName("an empty ladder is a configuration error, not an empty playlist")
    void rejectsAnEmptyLadder() {
        WorkerProperties.Ffmpeg empty = new WorkerProperties.Ffmpeg("ffmpeg", 6, Duration.ofMinutes(1), List.of());

        assertThatThrownBy(() -> HlsLadder.ffmpegCommand(empty, Path.of("/s.mp4"), true, Path.of("/hls")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no renditions");
    }

    private static String joined(List<String> command) {
        return String.join(" ", command);
    }
}
