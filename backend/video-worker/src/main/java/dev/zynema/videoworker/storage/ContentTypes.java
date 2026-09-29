package dev.zynema.videoworker.storage;

import java.util.Map;

/**
 * Content types for the objects the pipeline moves around.
 *
 * <p>Explicit and small: S3 stores whatever type it is told, and the HLS
 * playlists are the ones players actually validate.
 */
final class ContentTypes {

    private static final Map<String, String> BY_EXTENSION = Map.of(
        "m3u8", "application/vnd.apple.mpegurl",
        "ts", "video/mp2t",
        "m4s", "video/iso.segment",
        "mp4", "video/mp4",
        "mov", "video/quicktime",
        "mkv", "video/x-matroska");

    private ContentTypes() {
    }

    static String of(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String extension = dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
        return BY_EXTENSION.getOrDefault(extension, "application/octet-stream");
    }
}
