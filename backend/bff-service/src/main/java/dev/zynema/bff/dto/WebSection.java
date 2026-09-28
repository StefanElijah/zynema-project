package dev.zynema.bff.dto;

/**
 * A part of a composed screen that may be missing without breaking the page.
 *
 * <p>When a dependency that feeds one of these fails, the response says so in
 * {@code degraded} instead of pretending the section is empty: the frontend can
 * show "temporarily unavailable" rather than "you have nothing here".
 */
public enum WebSection {
    CONTINUE_WATCHING,
    MY_LIST,
    SUBSCRIPTION,
    PLAYBACK
}
