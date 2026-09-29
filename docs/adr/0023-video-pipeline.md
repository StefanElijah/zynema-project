# ADR-0023: A one-shot FFmpeg worker with object storage as the hand-off

- **Status:** Accepted
- **Date:** 2026-09-29
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 6 (video pipeline)

## Context

Catalogue entries need to be watchable: a source video has to become an HLS
ladder, the renditions have to live somewhere a player can fetch them, and the
catalogue has to learn that a title is ready. Three shapes were on the table:

1. **Transcode inside playback-service.** One less process, but a heavy,
   bursty, CPU-bound job would share a runtime with the request path that must
   stay responsive. Rejected.
2. **A long-running transcoding service with a queue.** The production-shaped
   answer, but Kafka arrives in Fase 7: building it now means inventing a queue
   to throw away next phase.
3. **A one-shot job** that runs, transcodes, uploads, updates the catalogue and
   exits. No queue, no idle process, and Fase 7 can trigger the same job from a
   Kafka consumer without touching the pipeline.

Two more constraints shaped the decision. FFmpeg is a **binary**, not a
library: putting it in every service image would cost ~80 MB in eight images
that never transcode. And MinIO's client tooling (`mc`) is no longer published,
so anything that "just uses mc" is a dead end — bucket creation and source
import had to become part of our own code.

## Decision

A **`video-worker` module: a Spring Boot command-line job** (no web server), and
one FFmpeg invocation per title:

```
mvn -pl video-worker         →  zynema-video-worker.jar
  --init-storage                                   create buckets (idempotent)
  --import-source=<path> --key=<key>               put a source object
  --source=<key> --content=<slug|id>               transcode a title
  --source=<key> --episode=<uuid>                  transcode one episode
```

- **One FFmpeg pass builds the whole ladder.** `-filter_complex` splits the
  decoded source into four renders (240p/480p/720p/1080p), `-var_stream_map`
  writes `240p/playlist.m3u8`-style variants plus `master.m3u8`, and segment
  boundaries are **forced** (`-sc_threshold 0` + `-force_key_frames` every 6 s)
  so a player can switch quality mid-segment. The ladder is configuration
  (`zynema.worker.ffmpeg.renditions`), not code.
- **Object storage is the hand-off.** The source comes from
  `s3://zynema-videos`, the ladder goes to `s3://zynema-hls`, both private, both
  through the AWS SDK. No shared filesystem between services, no volume the
  request path could accidentally read.
- **The catalogue is updated last**, through
  `PUT /api/v1/catalog/admin/{contents|episodes}/{id}/hls-path`, with a machine
  identity: Keycloak client `zynema-media` (client credentials) carrying the
  `content-manager` role. Until that call lands, `hls_path` is null and playback
  refuses to start ("not ready") — a half-uploaded prefix is never published.
- **The column stores an object key, never a URL.** Signed URLs expire; see
  ADR-0024 for how playback turns the key into one, per session.
- **Re-running is safe**: the target prefix is emptied before the new ladder is
  uploaded, so a shorter re-render cannot leave orphan segments behind.
- **The worker also owns storage plumbing**: `--init-storage` replaces the
  `mc`-based init container, `--import-source` replaces `mc cp` for the demo
  samples. One client, one credential set, no second tool to install.

The image is the only one in the platform with FFmpeg
(`eclipse-temurin:21-jre-alpine` + `apk add ffmpeg`).

## Consequences

**Positive**

- The request path stays free of transcoding, and eight service images stay
  free of FFmpeg.
- The pipeline is testable without the binary: the ladder is pure data
  (`HlsLadder`), the storage layer is exercised against **MinIO in
  Testcontainers** through the real S3 API, and the catalogue callback against
  WireMock. Only FFmpeg itself needs the image, and that is what the E2E runs.
- Fase 7 wires a Kafka listener to the same `VideoPipeline.run(...)` call.
- A failed run leaves the content unplayable rather than broken: `hls_path`
  stays null.

**Negative**

- A heavy image (~350 MB with FFmpeg) that must be built before the first
  transcode; the E2E pays that once.
- No queue means no retries or parallelism beyond what the operator does by
  hand, and no progress reporting beyond logs.
- One more store to operate (MinIO) and one more credential
  (`zynema-media`), both introspectable only from the console/Keycloak.

## Notes

- **MinIO images**: `minio/minio` was removed from Docker Hub and quay, and the
  official AIStor image refuses to serve without a licence (verified: "Access
  denied. No license is installed"). The platform uses Chainguard's
  source-built image, **pinned by digest**, in both compose and tests.
- **No healthcheck on MinIO**: the Chainguard image ships no shell utilities,
  so the compose healthcheck had to go. The worker's bucket initialiser retries
  until the server answers, which is where that robustness belongs anyway.
- The pipeline answers the product question "why is this title not playing?"
  with a single fact: `hls_path` is null. `GET /api/v1/catalog/episodes/{id}`
  and the detail endpoint surface it, and playback turns it into
  `409 CONTENT_NOT_READY`.
