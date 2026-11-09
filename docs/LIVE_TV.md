# Live TV

Live TV is an independent Android TV destination in Cinema's navigation rail. On first run, **Watch Live TV · no server needed** opens it without configuring Jellyfin. Existing library, streaming, music and download flows are unchanged. The desktop continues to compile against the plain JVM core; this release adds no desktop Live TV interface.

## Watching

Choose Ireland, UK, Brazil, American Entertainment, Canada, or Explore the World. Search also matches alternate names. Country and category selectors scroll horizontally with the remote. Direct, reviewed streams tune with OK; other entries open their official provider. Favourites and recently watched channels have their own sections.

Hold OK on a channel to favourite, move it to the first favourite position, hide, unhide, or open its provider. Moving successive favourites to first also lets you arrange the complete order. Hidden channels remain accessible through Hidden. These preferences and source reliability are local to the device.

The guide and player share a screen: browsing does not interrupt the current channel. Full screen expands the player; Back restores the guide and its selected channel. CH+/CH− changes between the direct channels in the current filtered list. Media play/pause/stop keys control the live player. Leaving the app releases playback; returning reconnects to the same broadcaster at its live position.

**Play now** means video was successfully rendered on this device in the last 24 hours with no newer failure. A newly discovered, reviewed endpoint says **Try live stream · not yet verified**. Provider routes distinguish subscription requirements. A provider handoff opens the app's home screen, not a guessed channel deep link; account, device, territory and programme rights still apply. Where no compatible app or browser exists, the provider's official address and installation guidance are shown.

## Sources and boundaries

Metadata comes from IPTV-org's current [JSON API](https://github.com/iptv-org/api): channels, feeds, streams, logos, guides and blocklist. Feed joins use both channel ID and feed ID. The stream `labels` array is preserved. Closed/NSFW records and blocklisted direct streams are excluded; stream URLs are deduplicated. A blocklisted target can retain an official provider-home link, but never its listed stream.

An IPTV-org listing is **discovery metadata, not permission or proof of playback**. Only reviewed broadcaster endpoints enter the built-in direct resolver. The launch set is TV Brasil, DW English, France 24 English and NHK WORLD-JAPAN. The review constrains broadcaster identity, HTTPS origin, path, and language service. TV Brasil Internacional is a separate identity and is never substituted with the national TV Brasil feed. Additional familiar targets use official provider routes.

Pluto TV and Plex entries are explicitly labelled free alternatives. Their linear channels are programmed by those providers and need no personal media library. Neither is represented as the actual premium-channel simulcast. Adult Swim's official marathon route is distinct from its subscription cable network. See [verification and territorial limitations](LIVE_TV_VERIFICATION.md).

There are no virtual channels, scheduled library playback, media downloads, DRM workarounds, protected-session extraction, geographic bypasses, or new dependencies on personal servers or paid APIs.

## Implementation

`core/.../data/live/` contains channel/feed/source/provider models, ingestion, cache, preferences, reliability ranking, bounded retry, M3U parsing and XMLTV parsing/matching. `app/.../ui/live/` contains the guide/player and Media3 data source. The existing film player and download pipeline are not repurposed.

All six catalogue endpoints refresh on entry when the saved snapshot is over 24 hours old, and daily while the guide remains open. Refresh is transactional: a malformed or incomplete response leaves the previous snapshot usable. Parsing retains only the reviewed launch identities before fetching the next endpoint; the worldwide guide metadata alone was about 25 MB during verification. The device's compact catalogue snapshot was about 256 KB. No hosted catalogue generator is needed.

The resolver ranks previously successful feeds above recent failures, penalises geographic/intermittent labels, deduplicates URLs, and attempts at most four feeds of the **same channel**. Startup/no-video and prolonged-buffering deadlines are 20 seconds per attempt. Media3's internal load retry is disabled so the finite resolver owns failover. Access restriction responses are explained without bypassing them. Exhaustion offers the official provider when one exists. Reliability is recorded after an actual decoded video frame, not merely an HTTP 200 or a manifest parse.

Guide feeds are fetched on demand after focus settles, matched to the selected regional feed, cached with a failure cooldown, and bounded in size. XMLTV's canonical `channel@feed` IDs and broadcaster site IDs are supported without crossing regional feeds. Explicit offsets are converted to device-local time, including daylight-saving transitions; missing timezone means UTC. No data means **Live broadcast · programme information unavailable**. This launch has no guarantee of a populated guide for every broadcaster.

Public HTTPS and normal port 443 are required for remote metadata. Credentials, unsafe protocols, literal/private hosts and reserved/private DNS answers are rejected. Every media manifest, segment, key and redirect is checked. Redirects are finite, with connection/read timeouts. These checks reduce SSRF exposure; DNS validation uses the JVM/system resolver and is not a DNS-pinning transport.

The optional Playlist action imports externally programmed, authorised M3U channels from a public HTTPS URL without query parameters. Stream URLs carrying query parameters, inline headers, credentials or local addresses are rejected; cookies/DRM/player directives are not executed. Only accepted, normalised metadata is saved, not the original playlist or its rejected secrets. An explicit `tvg-id` joins feeds; without it, an individual URL retains its own identity. Playlist refresh is manual, because arbitrary user-provided feed changes cannot establish playback rights. The built-in catalogue is automatic.

Logos load where supported. Unavailable/unsupported artwork uses a TV icon. Imgur's observed HTTP-200 outage images are excluded rather than displayed as channel branding.

## Build and install

Use JDK 17 and Android SDK 36. Put the SDK path in untracked `local.properties`, or set `ANDROID_HOME`.

```powershell
./gradlew.bat :core:test :app:testDebugUnitTest :desktop:test :app:assembleDebug '-Pcinema.appIdSuffix=.livetv' '-Pcinema.versionCode=1001'
adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <device> shell am start -n com.danielportelabyrne.cinema.livetv/com.danielportelabyrne.cinema.CinemaActivity
```

The suffix installs a separate test app alongside the existing Cinema application and preserves its settings. Omit the suffix for the normal application ID, and select an appropriate version code and signing key before replacing an existing install. Standard release signing is unchanged. Linux desktop compilation is available through `:desktop:compileKotlin` or the existing desktop-only build option.

For a repeatable real-video smoke test with the debug APK:

```powershell
./scripts/live-tv-smoke.ps1 -Serial <device>
```

This tunes actual public sources and observes Media3's first-frame events. It does not use mocked playback, report credentials or claim future/global availability. Run it while the TV is available for testing.
