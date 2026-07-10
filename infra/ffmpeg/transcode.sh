#!/bin/bash
# transcode.sh - Convert source videos to HLS multi-bitrate
# Usage: ./transcode.sh <input.mp4> <output-name>
# Requires: ffmpeg in PATH

set -euo pipefail

INPUT="${1:?Usage: transcode.sh <input> <output-name>}"
NAME="${2:?Usage: transcode.sh <input> <output-name>}"
OUT_DIR="${OUT_DIR:-./infra/ffmpeg/output}"
BUCKET_NAME="${BUCKET_NAME:-zynema-videos}"

mkdir -p "$OUT_DIR/$NAME"

# Bitrate ladder
RESOLUTIONS=("426x240:400k" "640x360:800k" "854x480:1200k" "1280x720:2500k" "1920x1080:5000k")

for spec in "${RESOLUTIONS[@]}"; do
  RES="${spec%%:*}"
  BITRATE="${spec##*:}"
  echo "Encoding $NAME at $RES ($BITRATE)..."
  ffmpeg -y -i "$INPUT" \
    -vf "scale=$RES" \
    -c:a aac -ar 48000 -c:v h264 -profile:v main -crf 20 -sc_threshold 0 \
    -g 48 -keyint_min 48 -hls_time 4 -hls_playlist_type vod \
    -b:v "$BITRATE" -maxrate "$(( ${BITRATE%k} * 120 / 100 ))k" -bufsize "$(( ${BITRATE%k} * 2 ))k" \
    -hls_segment_filename "$OUT_DIR/$NAME/${RES%%x*}_%03d.ts" \
    "$OUT_DIR/$NAME/${RES%%x*}.m3u8" 2>&1 | tail -5
done

# Master playlist
cat > "$OUT_DIR/$NAME/master.m3u8" <<EOF
#EXTM3U
#EXT-X-VERSION:3
EOF

for spec in "${RESOLUTIONS[@]}"; do
  RES="${spec%%:*}"
  BITRATE="${spec##*:}"
  W="${RES%%x*}"
  H="${RES##*x}"
  cat >> "$OUT_DIR/$NAME/master.m3u8" <<EOF
#EXT-X-STREAM-INF:BANDWIDTH=$((${BITRATE%k} * 1000)),RESOLUTION=${W}x${H}
${W}.m3u8
EOF
done

echo "Done: $OUT_DIR/$NAME/master.m3u8"
echo "Upload with: mc cp --recursive $OUT_DIR/$NAME/ minio/$BUCKET_NAME/$NAME/"
