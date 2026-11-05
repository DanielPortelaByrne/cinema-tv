# Office TV streaming comparison — 5 November 2026

## Result

The live test reproduced Cinema's failure to start Sharp Objects S01E03 and demonstrated working playback in Stremio. The first confirmed problem is source-discovery parity: **the installed Cinema app reported zero ported Stremio add-ons**. Its default request selected a different, metadata-stalled torrent. Supplying Stremio's working torrent to Cinema's backend changed the selection and produced a healthy download.

This establishes a concrete discovery/selection problem. It does not establish that the earlier code fixes are deployed, or that Cinema's player now matches Stremio's reliability.

## Device and scope

- Verified by Cast discovery: Office TV, Chromecast HD, 192.168.1.220.
- Connected through its existing authorized ADB TLS connection; no commands targeted the phone or other TVs.
- Stremio: version 1.12.1, version code 50000001.
- Cinema: version 1.0, version code 1; tested the existing installation.
- No APK updates, backend deployments, account changes, or data resets were performed.

## Measurements and observations

| Test | Observed result |
| --- | --- |
| Cinema normal UI, Sharp Objects S01E03 | Log reported `add-ons offered 0 ... (0 ported, imdb true)`. Search/start response took 15,268 ms. Loading showed zero seeders, one peer, and no useful download speed. No first frame was observed before the attempt was stopped roughly 90 seconds after the original press. |
| Cinema repeated normal backend request | Selected `Sharp Objects S01E03 Fix 1080p AMZN WEB-DL DDP5 1 H 264-NTb`, hash `f061d3a5b96b2bedca6f1359ba74573663b833ad`. qBittorrent reported `forcedMetaDL`, zero download speed, zero connected seeders, one connected leech, and four reported complete peers. The cached search reply returned quickly; that did not mean the media was ready. |
| Stremio, same episode, selected Torrentio 1080p Silence/QxR source | Source card advertised 51 seeders and 1.3 GB. Decoder initialization appeared about ten seconds after selection. Playback advanced to 00:36 of 52:27 within approximately 56 seconds of selection, at which point it was paused. No exact first-render callback was captured; decoder initialization is not a first-frame measurement. |
| Stremio stream identity | Hash `e9d58034e99d18ea0b31bb65d323b378cebc6de0`, episode file index 4, file size 1,395,978,392 bytes. Torrent is the Season 1 + Extras Silence pack. Runtime statistics showed nine connected peers and 82,575,360 downloaded bytes after pausing. Zero speed in that paused snapshot does not indicate a playback failure. |
| Cinema backend with the Stremio candidate supplied | Selected the same hash. Metadata and the correct S01E03 file appeared within about six seconds. Subsequent samples showed roughly 0.8–1.75 MB/s and three to four connected seeders. A later `/status` response reached `ready`. This download had warmed up before those later samples. |

The source candidate's first size estimate came from the displayed 1.3 GiB value. After reading the torrent metadata, the subsequent probe used the exact size and file index above. The synthetic candidate was supplied to the backend for diagnosis; it was not saved into the TV's account add-on collection.

## What the installed Stremio server actually does

The installed APK contains `assets/server.js` (4,759,280 bytes), which was extracted locally and inspected as text, without executing or copying it into Cinema. Its local `/stats.json` interface supplied the matching torrent and file metadata.

Verified behaviors in this installed bundle:

- Each file read stream selects a range and marks the next needed pieces as critical. Selections track read position and a bounded window.
- A hotswap routine can cancel reservations on sufficiently slower peers so faster peers can obtain the needed blocks.
- The active engine reported 55 connections, a 20,000 ms handshake timeout and a 4,000 ms request timeout. These are torrent peer settings, not player HTTP read timeouts.
- Active settings included a 15 MiB buffer and a 45 MiB circular memory buffer.
- Peer discovery supplied approximately twenty tracker sources plus DHT. Cinema's qBittorrent torrent had seven ordinary trackers, two reporting errors. qBittorrent also had DHT and PeX enabled.

These are observations of the installed build. They do not mean that copying its timeout numbers into qBittorrent or Media3 would reproduce its scheduler. The installed Stremio server has a JavaScript torrent-engine implementation; the earlier libtorrent recommendation describes a possible Cinema implementation, not a claim that this Stremio build uses libtorrent.

## Limits and next implementation work

1. Restore or complete Cinema's Stremio account/add-on import on this installation and verify a nonzero installed stream-add-on count. The live settings contained an integration toggle but no saved account add-on collection. The user's account was not relinked during the test.
2. Deploy the earlier parser/file-index/tracker fixes, then verify that the normal Cinema UI offers and selects the working hash without a diagnostic candidate injection.
3. Add playback-driven piece prioritization and measured startup/fallback decisions. The observed successful same-hash backend download means the qBittorrent path can find this swarm, but does not demonstrate reliable container probing, seeking or sustained playback.
4. Repeat controlled cold-cache and warm-cache player runs with the exact same file and collect first-frame and rebuffer events. Do not compare a warmed download to a cold start as if they were equivalent.

An attempt to launch Cinema's non-exported internal player directly was refused by Android. Therefore the same-hash diagnostic covers the backend download and readiness only; full matched-source Cinema player playback remains unverified. No export/security settings were changed to bypass that restriction.

Stremio was left paused at the test position. The local diagnostic ADB server forward was removed after inspection. Captures, redacted diagnostic logs and local APK/server inspection artifacts are in this task's `live-office` folder. The APK and extracted bundle were not added to either product repository.
