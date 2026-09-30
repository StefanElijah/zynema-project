package dev.zynema.videoworker.pipeline;

import java.time.Duration;
import java.util.UUID;

/**
 * The outcome of one transcode, as the job reports it.
 *
 * @param masterKey       object key of the playlist the API will presign
 * @param uploadedObjects number of files written to the HLS bucket
 */
public record JobResult(
    PipelineTarget.Kind kind,
    UUID targetId,
    String masterKey,
    int uploadedObjects,
    Duration took
) {
}
