package dev.zynema.videoworker;

import dev.zynema.videoworker.pipeline.PipelineTarget;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The command line of a job.
 *
 * <p>Validated eagerly and with complete messages: a batch job that fails
 * three minutes in because {@code --source} was misspelled has wasted three
 * minutes, and the log is the only interface it has.
 */
public record WorkerArguments(PipelineTarget target, String sourceKey) {

    public static WorkerArguments parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                int separator = arg.indexOf('=');
                options.put(arg.substring(2, separator), arg.substring(separator + 1));
            }
        }

        String source = options.get("source");
        String content = options.get("content");
        String episode = options.get("episode");

        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException(
                "Missing --source=<key>: the object key of the source video in the videos bucket");
        }
        if ((content == null) == (episode == null)) {
            throw new IllegalArgumentException(
                "Provide exactly one of --content=<slug|id> or --episode=<uuid>");
        }
        return new WorkerArguments(
            content != null ? PipelineTarget.content(content) : PipelineTarget.episode(UUID.fromString(episode)),
            source);
    }
}
