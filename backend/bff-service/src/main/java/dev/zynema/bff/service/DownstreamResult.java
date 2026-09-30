package dev.zynema.bff.service;

/**
 * The answer of an <em>optional</em> dependency: a value, or the fact that it
 * could not be asked.
 *
 * <p>Absent and degraded are different things and the API must not confuse
 * them: "you have no subscription" renders a paywall, "payment-service is down"
 * renders "try again". Both are represented as a missing value, so the flag is
 * what distinguishes a legitimate no from a failure.
 */
public record DownstreamResult<T>(T value, boolean degraded) {

    public static <T> DownstreamResult<T> of(T value) {
        return new DownstreamResult<>(value, false);
    }

    /** The dependency answered, and the answer was "nothing". */
    public static <T> DownstreamResult<T> absent() {
        return new DownstreamResult<>(null, false);
    }

    /** The dependency could not answer. */
    public static <T> DownstreamResult<T> unavailable() {
        return new DownstreamResult<>(null, true);
    }

    public boolean present() {
        return value != null;
    }
}
