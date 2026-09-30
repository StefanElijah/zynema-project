package dev.zynema.playback.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Heartbeat: where the viewer is right now. */
public record PositionRequest(

    @NotNull(message = "positionSeconds is required")
    @Min(value = 0, message = "positionSeconds must not be negative")
    Integer positionSeconds
) {
}
