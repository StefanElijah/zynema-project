package dev.zynema.videoworker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerArgumentsTests {

    private static final String EPISODE = "e0000000-0000-4000-8000-000000000001";

    @Test
    @DisplayName("a content job accepts a slug and keeps it for resolution")
    void parsesContentBySlug() {
        WorkerArguments arguments = WorkerArguments.parse(new String[]{
            "--source=samples/sintel.mp4", "--content=dune-part-two"});

        assertThat(arguments.sourceKey()).isEqualTo("samples/sintel.mp4");
        assertThat(arguments.target().reference()).isEqualTo("dune-part-two");
        assertThat(arguments.target().keyPrefix(UUID.fromString(EPISODE)))
            .isEqualTo("hls/contents/" + EPISODE);
    }

    @Test
    @DisplayName("an episode job addresses the episode and keys the render by its id")
    void parsesEpisodeJobs() {
        WorkerArguments arguments = WorkerArguments.parse(new String[]{
            "--source=samples/arcane-s01e01.mp4", "--episode=" + EPISODE});

        assertThat(arguments.target().reference()).isEqualTo(EPISODE);
        assertThat(arguments.target().keyPrefix(UUID.fromString(EPISODE)))
            .isEqualTo("hls/episodes/" + EPISODE);
    }

    @Test
    @DisplayName("missing or contradictory options fail with the reason, not a stack of usage")
    void rejectsIncompleteCommands() {
        assertThatThrownBy(() -> WorkerArguments.parse(new String[]{"--content=dune-part-two"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--source");

        assertThatThrownBy(() -> WorkerArguments.parse(new String[]{"--source=x.mp4"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exactly one");

        assertThatThrownBy(() -> WorkerArguments.parse(new String[]{
            "--source=x.mp4", "--content=dune-part-two", "--episode=" + EPISODE}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exactly one");
    }
}
