# CloudStream comparison notes

## Sources

- CloudStream official repository: https://github.com/recloudstream/cloudstream
- Android Media3 offline downloads: https://developer.android.com/media/media3/exoplayer/downloading-media

## Observed CloudStream patterns

CloudStream's source-level downloader separates VIDEO and M3U8 handling, passes link headers and referer into HLS requests, resolves HLS segments, supports retry and resume metadata, exposes explicit paused/downloading/failed/done states, and tries the next mirror when a download source fails. Its player keeps a selected link and can refresh/load next links after player errors. CloudStream explicitly rejects or skips unsupported types such as magnet/torrent and some DASH paths in its custom file downloader; it does not make all protected streams downloadable.

FMHuB24 already has provider `loadLinks`, ExtractorLink headers/referer during playback, Media3 DownloadService/DownloadManager for compatible streams, a custom non-DRM HLS segment downloader, foreground HLS service, Room status persistence, and local offline playback. Gaps include true link fallback on player error, robust queue actions (pause/resume/retry), HLS segment-level resume, and a user-facing per-download progress/error control surface.

## Safe inspiration selected

1. Keep native Media3 for progressive/adaptive-compatible downloads and custom HLS for non-DRM HLS.
2. Improve link ranking and fallback without bypassing DRM, paywalls, authentication, or geo-blocks.
3. Keep explicit download states and avoid claiming completion at enqueue time.
4. Preserve headers/referer for authorized requests.
