package dev.zynema.bff.dto;

import java.util.UUID;

/** A saga card on the landing page. */
public record FranchiseCard(
    UUID id,
    String slug,
    String name,
    String description,
    String coverUrl,
    long itemCount
) {
}
