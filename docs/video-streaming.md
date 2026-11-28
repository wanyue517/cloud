# Progressive video previews

Status: implemented behind disabled-by-default flags; real R2 cold-start browser acceptance is incomplete. Keep `cloud.preview.progressive-enabled=false` and leave `VITE_PROGRESSIVE_VIDEO` unset for the existing MP4 path. Enable both only in a dedicated validation environment.

Original videos still use native playback, R2 signed URLs or authenticated Range proxying. Existing optimized MP4 assets remain reusable. A compatible stream is prepared when probing finds an incompatible source, or the original cannot start within 20 seconds.

`POST /api/v1/spaces/{space}/nodes/{id}/preview/video/session` authorizes the source and starts or observes a per-version HLS job. States are `PENDING`, `PROCESSING`, `PLAYABLE`, `READY`, and `FAILED`. `PLAYABLE` means at least one complete media segment is published; conversion need not have finished. A retry restarts a failed job. Completed cache survives a process restart; an incomplete cache is rebuilt on demand.

Remote FFmpeg input uses an opaque URL bound to loopback, with immutable 1 MiB disk-backed Range blocks fetched from the storage provider. This allows repeated track seeks to reuse blocks without downloading the whole source before encoding. Browser-compatible H.264/AAC tracks are copied; incompatible tracks are encoded to H.264/AAC. FFmpeg atomically publishes an EVENT playlist and complete MPEG-TS segments. A completion marker is written only after successful processing and output duration validation.

`stream-ticket` returns `assetType: VIDEO_HLS` for a playable HLS cache. Each manifest and segment request to `/api/v1/media/hls` verifies a short-lived media ticket and revalidates the source version. Manifests must not be cached; immutable segments may be privately cached. Segment filenames are restricted to generated names. FFmpeg's input URL and R2 credentials are never given to the browser.

Web and mobile use native HLS when available, otherwise lazy-load hls.js. Playback begins at the start, not the current end of an unfinished EVENT playlist. While processing continues, seeking is limited to generated content. Complete-cache playback supports the entire duration. Processing needs to remain faster than playback for uninterrupted viewing; queue wait, slow storage or a slow encoder can still delay or stall playback.

Video thumbnail generation runs in a bounded background pool and returns `202` with `Retry-After` while pending. It prefers optimized MP4 or a published local HLS segment over reading the R2 original again. Both clients retry pending thumbnails with a bound and do not store an empty pending response as a missing image.

For a 1.5 GB decimal source, a full download alone takes approximately 600/240/120 seconds at sustained 20/50/100 Mbps, before overhead. Progressive startup instead depends on authorization, source metadata, a few input blocks, the first decodable segment and browser buffering. Video duration, GOP length, source bitrate, encoder speed and server-to-R2 throughput matter more than total file size for startup.

Validation includes real FFmpeg conversion against throttled storage reads, playback availability before complete input transfer, complete-file duration, Range correctness, ticket rejection, restart/cache loss handling, async thumbnail behavior, and browser playback of real R2 samples.
