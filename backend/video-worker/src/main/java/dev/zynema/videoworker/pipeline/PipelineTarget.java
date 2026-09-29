package dev.zynema.videoworker.pipeline;

import java.util.UUID;

/**
 * What a job transcodes into.
 *
 * <p>Addressed by slug (humans) or id (machines); the key layout always uses
 * the resolved id, because a key that outlives a render must never depend on
 * something renamable.
 */
public record PipelineTarget(Kind kind, String reference) {

    public enum Kind {
        CONTENT,
        EPISODE
    }

    public static PipelineTarget content(String slugOrId) {
        return new PipelineTarget(Kind.CONTENT, slugOrId);
    }

    public static PipelineTarget episode(UUID episodeId) {
        return new PipelineTarget(Kind.EPISODE, episodeId.toString());
    }

    public String keyPrefix(UUID resolvedId) {
        return (kind == Kind.CONTENT ? "hls/contents/" : "hls/episodes/") + resolvedId;
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase() + ":" + reference;
    }
}
