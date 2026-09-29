package dev.zynema.videoworker.config;

/**
 * One rung of the HLS ladder.
 *
 * @param name             used as the variant directory and playlist name
 *                         ({@code 720p/playlist.m3u8}), so it must be a safe
 *                         path segment
 * @param videoBitrateKbps target bitrate; maxrate and bufsize are derived from
 *                         it unless set explicitly, keeping the ladder
 *                         consistent when a rung is tuned
 */
public record Rendition(
    String name,
    int width,
    int height,
    int videoBitrateKbps,
    int audioBitrateKbps,
    Integer maxrateKbps,
    Integer bufsizeKbps
) {

    public int maxrateKbpsOrDefault() {
        return maxrateKbps != null ? maxrateKbps : videoBitrateKbps + (videoBitrateKbps / 10);
    }

    public int bufsizeKbpsOrDefault() {
        return bufsizeKbps != null ? bufsizeKbps : videoBitrateKbps * 2;
    }
}
