# Native Live TV investigation

Tested on the connected TCL Smart TV Pro running Android 14 on 9 November 2026. The approved workstation egress lookup returned GB. The TV used its existing network configuration. The probe is a separate Android app. The CinemaTV release still uses its existing Live TV player.

| Route | Result on the TV |
| --- | --- |
| TCL provider channels via Android TV Input Framework | The probe received `onVideoAvailable` for Kim’s Convenience in 3.6 and 5.5 seconds on two tunes. Channel Up switched to Dr. Phil in 3.6 seconds, then The Graham Norton Show in 2.5 seconds. The provider supplied programme titles and times for Now/Next. Android screenshots were empty on this video path, so these measurements are provider callbacks. Picture and audio need an on-screen check. |
| Built-in aerial tuner | The hardware input was discoverable and the aerial connection was confirmed. The public listing query yielded no BBC, ITV, or Channel 4 aerial rows to tune. The TCL input selector was reached. Aerial playback inside the probe remains unverified. |
| TG4 | The official live page played the programme in the probe’s WebView after granting the protected media capability to TG4’s HTTPS origin. The screenshot showed the TG4 watermark and zoo programme after the initial advertisement. This is provider playback within the app. A TV-friendly full-screen flow and permission for distribution in CinemaTV remain open. |
| RTÉ | RTÉ Player was restored from Google Play. Its live guide opened without signing in. RTÉ One and RTÉ2 showed programme rights restrictions during the test. RTÉ News reached `PLAYING` in the official app. CinemaTV’s provider table still points to the older mobile package instead of the installed TV package, `com.twentyfouri.tvbridge.rte`. |
| BBC | The installed player pauses when its activity leaves the foreground. Its cookie and credential providers require signature permissions. The inspected build exposed no supported player service for CinemaTV to embed. Native BBC playback remains unverified. |
| Brazil | TV Brasil had verified Media3 playback in the original feature test. Globoplay exposes an Android TV input service on this TCL, but the probe found no channel rows for it. Globo documents a UK subscription for TV Globo Internacional. Its service needs an eligible account and published channels before an in-app test. |
| Canada | Kim’s Convenience is a separate provider-programmed sitcom channel. CBC, CTV and Global simulcasts remain unverified in CinemaTV. Global’s official FAQ lists the Canadian STACKTV route. |
| US entertainment | Adult Swim’s official marathon page redirected to its territory information page and supplied no video. No US premium simulcast was verified. The TCL catalogue included other independently programmed movie and series channels. |

The initial TV listings query returned zero rows. The runtime `android.permission.READ_TV_LISTINGS` consent opened the public listings and the probe filtered out preview rows and entries without input IDs. The result included 151 rows after that filter. CinemaTV should additionally match each row to a registered input and keep provider-labelled entertainment channels distinct from network simulcasts.

Installed apps can own authentication and render into CinemaTV when they expose a compatible input service. Android’s standard `TvView` path worked with TCL’s provider service. BBC and RTÉ did not expose that path on the tested TV. Privado’s split tunnelling operates at app level. Its separate SOCKS5 product could route requests from a compatible CinemaTV player session, subject to provider eligibility. It would still need to carry the session’s relevant media and licence requests consistently. No VPN route was tested.

## Resume from here

1. Wire provider-published channels and their Now/Next rows into the existing Cinema guide, with one active `TvView` session and existing Media3 playback for eligible direct streams. Test cold starts, repeated Channel Up/Down, Back, standby, audio, and the tuned picture. Use measured time to first video to set the switching target.
2. Finish the TCL aerial check in the system TV app, then verify any public Freeview rows in Cinema. Keep unverified channels out of the surf order. Fix the RTÉ and Globoplay package mappings and require an explicit provider action so a generic URL handler cannot open Downloader.
3. Confirm a supported TG4 distribution or embedding route before shipping the in-app player. Test the same programme through a normal licence exchange and show rights blocks distinctly from DRM or network errors.
4. Add further channels only after identifying an approved playback route. Confirm entitlements and territory separately from first video, maintain provider ads and rights checks, and reuse the existing same-channel fallback and metadata caching. Keep channel selection independent of media library and download services.

## Reproduce the probe

From the repository root with JDK 17, Android SDK 36 and ADB available:

```powershell
.\gradlew.bat -p scripts/live-tv-probe assembleDebug
adb -s <serial> install -r scripts/live-tv-probe/build/outputs/apk/debug/CinemaNativeProbe-debug.apk
adb -s <serial> shell am start -n com.danielportelabyrne.cinema.nativeprobe/.ProbeActivity --es mode inputs
adb -s <serial> logcat -d -s CinemaNativeProbe:I '*:S'
```

Allow the TV listings permission, select a channel and use Channel Up/Down. Channel IDs are local to the TV. The separate `--es mode web --es url https://www.tg4.ie/en/player/live/TG4 --ez protected_media true` test uses TG4’s official page and normal licence exchange. The probe was built and installed on the physical TV. The full CinemaTV suite was not rerun for these investigation-only files.

References. [Android TvView](https://developer.android.com/reference/android/media/tv/TvView), [TV Input Framework](https://source.android.com/docs/devices/tv), [TV provider source](https://android.googlesource.com/platform/packages/providers/TvProvider/+/master/src/com/android/providers/tv/TvProvider.java), [Protected media permission](https://developer.android.com/reference/android/webkit/PermissionRequest), [TG4 terms](https://www.tg4.ie/en/information/terms/), [Globoplay international channels](https://ajuda.globo/globoplay/app/globoplay-internacional/consumo/faq/quais-canais-estao-disponiveis-no-globoplay.ghtml), [Global TV FAQ](https://www.globaltv.com/faq/), [Adult Swim marathons](https://www.adultswim.com/streams), [Privado split tunnelling](https://www.privadovpn.com/split-tunneling/), [Privado SOCKS5](https://www.privadovpn.com/features/socks5proxy/).
