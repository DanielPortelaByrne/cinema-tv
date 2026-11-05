# Cinema / Stremio streaming investigation — 2026-11-05

Reference problem: Sharp Objects works more reliably in Stremio than Cinema. This investigation inspected the local Cinema TV and stream-service source, Stremio's public protocol and shared core, and the torrent engine documentation. It did not decompile the installed Android TV APK or measure live playback. These findings identify code defects; they do not establish which defect caused a particular viewing failure.

## What is verifiable about Stremio

The public stream protocol separates a stream's identity from its display metadata. Torrent streams identify `infoHash` and optional `fileIdx`, and can provide peer discovery `sources`. Without a file index, the documented fallback is the largest torrent file. Streams can instead contain a direct `url`, including an already hosted stream. Seeder counts and file sizes are not required protocol fields.

The shared Rust core preserves the torrent file index and announce sources, and converts `tracker:` sources into magnet tracker parameters. `description` accepts the old `title` alias. This is evidence for the public contract, not proof of the exact implementation or tuning in the installed Android TV build.

Sources:

- [Stremio stream protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md)
- [Stremio core stream representation and magnet conversion](https://github.com/Stremio/stremio-core/blob/master/src/types/resource/stream.rs)
- [Stremio core architecture and license](https://github.com/Stremio/stremio-core)
- [qBittorrent WebUI API](https://github.com/qbittorrent/qBittorrent/wiki/WebUI-API-%28qBittorrent-5.0%29)
- [libtorrent streaming versus sequential download](https://www.libtorrent.org/streaming.html)

The implementation changes here are original fixes to Cinema's protocol handling; no Android app implementation or bundled Stremio server code was copied.

## Cinema path before the fixes

`MoviesPanel.streamNow` asks the account's ported add-ons from the TV, sorts their results by seeders, caps the list, and sends candidates through `Seerr.startStream` to the helper service. The desktop's stream-now path does not send these account-specific candidates.

The service independently searches Prowlarr and its configured public add-ons, adds the client's candidates, picks one release, adds it to qBittorrent, waits for metadata, chooses a video, and serves completed pieces over HTTP ranges. The Android player consumes that URL with Media3. Downloads requested through Seerr/Sonarr/Radarr are a separate pipeline from the stream cache.

The sophisticated `StreamRanking.kt` scorer is not the scorer used by this stream-service selection path; Python's `pick` and `stream_score` choose that torrent.

## Confirmed defects and implemented fixes

| Defect | Consequence | Fix |
| --- | --- | --- |
| Both add-on parsers only accepted one emoji display format and required seed count plus size. | Valid protocol responses disappeared from Cinema's candidate pool. | Read `description` or legacy `title`, structured video size/filename, additional seed/size formats; retain unknown optional metadata without inventing values. |
| `fileIdx` and `sources` disappeared during parsing and serialization. | A different pack file could be chosen; add-on tracker hints never reached qBittorrent. | Preserve both through the client request and backend sanitization; honor explicit file indexes; use supplied trackers in generated magnets. |
| A single movie's 12 GiB cap also applied to a season pack. | Plausible packs were discarded despite modest per-episode size. | Apply a bounded season-pack allowance using the existing ten-episode estimate. |
| Stream cache identity was just the info hash. | Episode 5 could reuse an active entry for episode 4 in the same pack. | Reject conflicting active file/episode requests instead of silently returning the other episode; restore the selected file using file priorities. |
| Metadata force-start happened only after metadata existed. | Metadata fetching could remain queued behind ordinary downloads. | Force-start when the torrent row first appears, including metadata states. |
| `/status.progress` was whole-torrent progress. | The player's selected-episode seek limit could reflect other episodes in the pack. | Report the contiguous completed prefix of the selected file; expose whole-torrent progress separately as `torrentProgress`. |
| Failure of backend discovery discarded client candidates too. | Working account add-ons could not rescue a failed Prowlarr/public-add-on lookup. | Use supplied candidates when backend discovery fails. |
| Retrying selected the same metadata-failed source. | Repeated attempts could repeat the same failure. | Exclude metadata-failed sources for five minutes when choosing on a subsequent start. |

`/status` also exposes `advertisedSeeds`, `bufferedBytes`, and `fileIdx`. The existing `seeds` field continues to report connected qBittorrent seeders. Advertising 100 seeders is not equivalent to having 100 connected uploading peers. This distinction may explain some of the observed count discrepancy; a live comparison is needed to confirm it.

Explicit tracker hints replace the fallback tracker list when present. DHT hints are preserved but are not translated into qBittorrent DHT bootstrap configuration. Existing real magnets remain intact.

## Remaining architectural gaps

1. **Playback-aware piece scheduling.** `Handler.copy` waits for pieces at a requested offset but does not prioritize them in the torrent engine. Sequential mode and first/last-piece priority are not equivalent to scheduling by playback deadline. A container may request its tail index before the first frame; seeking adds more arbitrary ranges. Libtorrent explicitly documents why sequential downloads can stall on a slow peer while later pieces keep arriving. Its streaming API provides deadlines; the qBittorrent WebUI API used here does not expose that control.
2. **Automatic source recovery.** The service picks one torrent. Metadata failure is visible to the app, but does not automatically switch the current playback session to another candidate. The new retry exclusion applies to a subsequent start. Mid-playback failover needs player coordination and preservation of playback position.
3. **Throughput-based admission.** `stream_score` still assumes 1.5 Mbps per advertised seeder and only considers candidates with at least half the best count. This is a heuristic, not a throughput measurement. Runtime, peak bitrate and actual contiguous-piece arrival should drive automatic selection.
4. **Direct streams.** The account-candidate path still accepts torrents, not direct `url` responses or required proxy headers. If an add-on is configured for cached HTTP delivery in Stremio, Cinema is not reproducing that path. No debrid configuration was inspected during this investigation.
5. **Timeout mismatch.** Android currently allows 75 seconds to first picture, the service permits 90 seconds for metadata, and the HTTP read timeout is 60 seconds. Increasing these alone cannot fix missing piece scheduling. The player source already had unrelated local changes and was not edited here.
6. **Discovery latency and parity.** The TV can spend up to 25 seconds waiting for add-ons before `/start`; the backend can then spend another 30 seconds searching. Public backend add-ons and the account's configured add-ons are not interchangeable. `prepare` warms backend discovery, not the TV account's add-on requests. IMDb lookup fallback and manifest resource constraints also deserve coverage.
7. **Concurrent files in one torrent.** The conflict guard prevents wrong-episode playback, but does not allow simultaneous different files from the same season pack. That requires session identity by `(infoHash, fileIdx)`, per-file URLs/tickets, and ownership/reference counting of the shared torrent.

## Concrete engine design for the next implementation

Keep the existing qBittorrent adapter for ordinary downloads. Add a streaming engine interface with `open(hash, fileIdx, sources)`, `prioritize(fileOffset, length, deadline)`, `readCompletedRange`, `stats`, and `close`. A libtorrent adapter should prioritize the selected file, assign short deadlines to a bounded window at each active HTTP cursor, and handle container-header/tail probes separately. It should reclaim old deadlines as cursors move and permit more than one cursor per file.

Use an opaque playback session ID that owns `(hash, fileIdx)` rather than making the torrent hash the session ID. Track separate phases for discovery, metadata, first-byte availability, container probing, first frame and rebuffering. Allow a small ordered fallback list with measured startup budgets. Before first frame, the player can reopen a new source; after playback begins, switch through a new media source and restore the position, never substitute a different file halfway through the same byte response.

Estimate required bandwidth from the selected file's actual size/runtime with a peak margin. Measure download arrival over several windows and the playable contiguous reserve. Do not equate LAN reads of already cached bytes or advertised seeder counts with sustainable torrent throughput.

## Sharp Objects acceptance check

For the same episode and same configured add-on, compare the content ID, returned info hash, file index, tracker hints, advertised seed count, connected seeders, and selected filename. Keep URLs and tracker credentials out of logs. If Stremio is using HTTP delivery, record the source type without exposing its signed URL.

Measure time to discovery completion, metadata completion, first useful byte, tail/index completion, and first frame. During playback record contiguous reserve, download rate, missing piece at the read cursor, rebuffer duration and any fallback. Test a fresh cache, resume, tail probes, a seek, a dead high-seeder candidate, and a second episode from the same pack. Use authorized test media for repeatable torrent-engine benchmarks.

The Kotlin core build and all 374 core tests passed. All 119 backend tests passed, covering protocol handling and the fake-server pipeline, including ten new streaming-parity regressions. Existing socket ResourceWarnings appeared during the backend suite. No live Sharp Objects playback, TV deployment, full Android APK build, or engine-performance benchmark has been performed, so Stremio-level performance is not yet demonstrated.
