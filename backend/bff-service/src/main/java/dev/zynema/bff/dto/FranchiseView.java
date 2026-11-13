package dev.zynema.bff.dto;

import java.util.List;
import java.util.UUID;

/** A saga and its ordered titles. */
public record FranchiseView(
    UUID id,
    String slug,
    String name,
    String description,
    String coverUrl,
    List<TitleCard> items
) {
}
