# ADR-0024: Signed HLS — manifests through playback, segments through the edge

- **Status:** Accepted
- **Date:** 2026-09-29
- **Deciders:** Project owner, AI co-pilot
- **Phase:** Fase 6 (video pipeline)

## Context

HLS and presigned URLs do not compose out of the box. A playlist references its
children **relatively** (`240p/playlist.m3u8`, `segment_00000.ts`), and a
relative reference drops the query string — signature included. So "presign the
master and be done" produces a playlist whose very next request is a 403. The
options were:

1. **Public HLS objects, a signed entry URL.** Simple, but the signature only
   gates discovery: every segment is world-readable and cacheable forever.
2. **Presign every object at packaging time** and rewrite the playlists to
   absolute signed URLs. Security theatre in reverse: the TTL is chosen when
   the video is transcoded, so a URL published today still works next week.
3. **Rewrite the manifests per request** and sign each segment on the way out.
4. **A token nginx validates** (`secure_link`, or signed cookies) for every
   object. Real, but propagation is the problem again: child requests carry no
   query string unless the playlists are rewritten (which is option 3 with extra
   nginx modules).

## Decision

Option 3, with the split that keeps a Java service out of the byte path:

- **Manifests go through playback-service.** `GET
/api/v1/playback/stream/{sessionId}/master.m3u8` returns the master with each
  variant pointing back at
  `/api/v1/playback/stream/{sessionId}/{variant}/playlist.m3u8`. Both endpoints
  require the caller's token and check **session ownership**: knowing a session
  id is not enough.
- **Segments never touch playback.** The variant playlist embeds a **presigned
  URL per segment with a 60-second TTL** (`S3Presigner`, AWS SDK). The player
  fetches bytes straight from the storage edge.
- **The signature is computed for the internal host** (`http://minio:9000`) and
  handed to the client as the **public edge** URL
  (`http://localhost:8090/minio/...`). SigV4 signs the Host header, so nginx
  presents the host the signature covers (`proxy_set_header Host minio:9000`) —
  that one line is what makes presigned URLs work through a proxy.
- **The buckets stay private.** No anonymous-read policy, no public HLS path.
  MinIO is published on `127.0.0.1` only; nginx is the single public delivery
  path, with the CORS headers a player on another origin needs.
- **"Not rendered yet" is a state, not an error**: with `hls_path` null,
  playback answers `409 CONTENT_NOT_READY`, and the client shows "not available"
  instead of retrying a broken stream.

The session response carries `streamPath` (the relative master URL), so the
client never builds a video URL itself.

## Consequences

**Positive**

- A segment URL is valid for a minute and for one object. Sharing it is
  pointless, and a leaked playlist ages out on its own.
- Segments are ordinary cacheable objects at the edge: the request path of
  playback-service handles a handful of tiny manifests per stream, never video
  bytes.
- The whole model is testable without a browser: MinIO in Testcontainers plus
  assertions on the rewritten playlists (TTL, host, path, tags preserved).
- The rejected alternatives cost nothing to revisit: turning on a CDN or
  `secure_link` later changes nginx, not the API.

**Negative**

- Two extra round trips per stream (master, variant) through the API before any
  bytes flow.
- The Host-header rewrite is subtle. It is documented in the runbook and the
  nginx config, but it is the kind of thing that breaks when somebody "cleans
  up" the config.
- The 60-second segment TTL assumes the player fetches segments right away.
  Pausing for a long time then resuming re-fetches the variant playlist, which
  re-signs — hls.js does this — but a client that caches playlists forever
  would fail.
- Safari's **native** HLS cannot send the Authorization header for the
  manifest. hls.js (every other browser) is unaffected; the fix, if it is ever
  needed, is the cookie-token flow this ADR rejected for now.

## Notes

- `hls_path` stores an **object key**, never a URL: the pipeline writes it once,
  and playback turns it into a URL per session. A stored URL would be a bug
  with a timer.
- The variant name is validated against `[A-Za-z0-9]{2,12}` before it reaches
  storage, and a traversal attempt dies at the container (encoded slash → 400)
  or the whitelist (→ 404).
- The player page (`/watch/:contentId`) is deliberately thin: read the title and
  the account from the BFF, start a session on a click, attach hls.js, heartbeat
  every 15 s, end the session on the way out. hls.js adds ~450 kB to the bundle;
  lazy-loading the player is a Fase 8 concern.
