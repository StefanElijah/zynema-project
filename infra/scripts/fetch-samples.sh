#!/usr/bin/env bash
#
# fetch-samples.sh - put the demo videos in the source bucket.
#
# The pipeline needs *something* to transcode. This script downloads two short
# Creative-Commons clips from the Blender open movies (Sintel and Big Buck
# Bunny, CC-BY) and imports them with the video worker itself, because the
# platform does not ship MinIO's client tooling.
#
# Offline? Set SYNTHETIC=1, or just let a failed download fall back to a clip
# generated with FFmpeg inside the worker image: the object keys are the same,
# so the rest of the flow does not care where the bytes came from.

set -euo pipefail

cd "$(dirname "$0")/../.."

SAMPLES_DIR="${SAMPLES_DIR:-.cache/samples}"
SYNTHETIC="${SYNTHETIC:-0}"
COMPOSE="docker compose"

MOVIE_KEY="samples/demo-movie.mp4"
EPISODE_KEY="samples/demo-episode.mp4"

# Blender open movies: Creative Commons Attribution.
MOVIE_URL="https://download.blender.org/peach/trailer/trailer_480p.mov"
EPISODE_URL="https://download.blender.org/durian/trailer/sintel_trailer-480p.mp4"

mkdir -p "$SAMPLES_DIR"

log() { printf '  %s\n' "$*"; }

generate_synthetic() {
    local file="$1"
    log "generating a synthetic clip with the worker's FFmpeg (offline fallback): $file"
    $COMPOSE run --rm -T \
        -v "$(pwd)/$SAMPLES_DIR:/samples" \
        --entrypoint sh video-worker -c \
        "ffmpeg -hide_banner -loglevel error -y \
            -f lavfi -i testsrc2=size=1280x720:rate=24 \
            -f lavfi -i sine=frequency=440:sample_rate=48000 \
            -t 20 -c:v libx264 -preset veryfast -pix_fmt yuv420p \
            -c:a aac -shortest /samples/$file"
}

fetch() {
    local url="$1" file="$2"
    if [ "$SYNTHETIC" = "1" ]; then
        generate_synthetic "$file"
        return
    fi
    log "downloading $url"
    if ! curl -fsSL --retry 2 --max-time 300 -o "$SAMPLES_DIR/$file" "$url"; then
        log "download failed (offline?) — falling back to a synthetic clip"
        generate_synthetic "$file"
    fi
}

import() {
    local file="$1" key="$2"
    log "importing $file -> s3://\$MINIO_BUCKET_VIDEOS/$key"
    $COMPOSE run --rm -T \
        -v "$(pwd)/$SAMPLES_DIR:/samples" \
        video-worker --import-source="/samples/$file" --key="$key"
}

echo "Preparing Creative-Commons demo samples..."
fetch "$MOVIE_URL" "demo-movie.mp4"
fetch "$EPISODE_URL" "demo-episode.mp4"
import "demo-movie.mp4" "$MOVIE_KEY"
import "demo-episode.mp4" "$EPISODE_KEY"

echo
echo "Done. Source objects in place:"
echo "  $MOVIE_KEY    (used by: make transcode)"
echo "  $EPISODE_KEY  (used by: make transcode)"
echo "Next: make transcode"
