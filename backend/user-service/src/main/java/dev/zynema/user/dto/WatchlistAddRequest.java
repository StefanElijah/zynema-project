package dev.zynema.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WatchlistAddRequest(
    @NotNull(message = "contentId is required")
    UUID contentId
) {
}
