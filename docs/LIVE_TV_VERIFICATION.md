# Live TV verification — 8–9 November 2026

This report separates catalogue presence, official-provider routes, and decoded live playback. The host's country-only network check returned **GB**. Physical-TV tests used the connected Smart TV Pro on the same local network. This is evidence for these devices and this connection at the recorded times, not a promise of availability on every UK ISP or for every programme.

## Real playback

The isolated debug application (`com.danielportelabyrne.cinema.livetv`) was installed alongside the existing Cinema app. Real broadcaster manifests and media segments were loaded by the dedicated Media3 player; no mocked media, protected-session scraping or downloads were used.

| Broadcaster/feed | Physical TV result | First decoded frame recorded, device time |
|---|---|---|
| TV Brasil, national feed | Played; live picture visually inspected alongside the guide | 8 Nov 23:54:39 |
| DW English | Played; live picture visually inspected alongside the guide | 8 Nov 23:56:02 |
| France 24 English | Played; Media3 first-video-frame event | 8 Nov 23:58:33 |
| NHK WORLD-JAPAN, international English service | Played; Media3 first-video-frame event | 8 Nov 23:59:24 |

TV Brasil's manifest was independently confirmed in EBC's [official embeddable player](https://aovivo.ebc.com.br/embed-tvbrasil.html), and DW's in its [official live page](https://www.dw.com/en/live-tv/channel-english). Only these reviewed broadcaster origins and paths are eligible; third-party restreams discovered alongside them are not enabled. The NHK World Premium service is explicitly excluded. The client does not redistribute media or claim broadcast rights on behalf of a provider.

## Priority countries and entertainment

| Region | Investigated / included targets | Direct playback verified | Official route and limitations |
|---|---|---|---|
| Ireland | RTÉ One, RTÉ2, RTÉjr, RTÉ News; Virgin Media One–Four; TG4 | None of these Irish channels | RTÉ Player, Virgin Media Play, TG4 Player. No freely authorised external-player UK simulcast established for the main Irish networks. TG4 says its player is available worldwide **where programme rights permit**; this does not mean its entire schedule is available abroad. Irish app playback was not verified. |
| UK | BBC One/Two, ITV1/2, Channel 4, E4, Film4, 5, U&Dave | No native simulcast integrated for these | Official app/service handoffs. BBC iPlayer, ITVX and Channel 4 were present on the physical TV. Accounts, broadcaster eligibility and device support apply; BBC requires a TV Licence. ITV documents Android TV live support. A provider-home handoff is not proof that a particular channel played. |
| Brazil | TV Brasil, TV Brasil Internacional, TV Globo/Rede Globo, SBT, Record, Band, TV Cultura | **TV Brasil** | TV Brasil Internacional has its own official global service, but no direct stream was verified in Cinema; it is never substituted with national TV Brasil. Globoplay and PlayPlus may require eligible subscriptions/accounts and international packages. +SBT, Bandplay and TV Cultura use official service pages; their UK live playback was not verified. |
| Canada | CBC Television, CTV, Global Television Network | None | Official CBC Gem, CTV and Global routes. Canadian territory and account/provider eligibility apply; no authorised UK simulcast established. Provider playback from the UK was not verified. |
| US Entertainment | Adult Swim marathons, Comedy Central, Cartoon Network, FX, FXX, AMC, TCM; Pluto TV and Plex alternatives | No target-network simulcast | Premium targets retain official provider/service routes with subscription/territory notes. No authorised UK direct simulcast was established. Pluto/Plex offer separately identified provider-programmed alternatives, with no personal library needed; their app playback was not verified. |

Adult Swim deserves particular care: the [official support page](https://adultswim.zendesk.com/hc/en-us/articles/38968816327575-Is-the-app-free) distinguishes free marathons from subscription live TV. Although web-search infrastructure could render a marathon page, a direct request to `adultswim.com/streams` from the GB test connection returned the **Where to Watch** page. No UK marathon video was successfully played in Cinema, and no hidden stream/session endpoints were extracted. The app therefore offers the official route and labels UK marathon availability unverified.

Relevant primary sources: [IPTV-org API schema](https://github.com/iptv-org/api), [TG4 availability FAQ](https://www.tg4.ie/en/information/faq/frequently-asked-questions/), [EBC international service](https://www.ebc.com.br/imprensa/2026/tv-brasil-internacional-completa-dois-anos-conectando-o-brasil-ao-mundo), [ITVX live-device support](https://help.itv.com/support/solutions/articles/204000073856-how-do-i-watch-live-tv-on-itvx-/), [Global eligibility FAQ](https://www.globaltv.com/faq/), [TV Cultura official player](https://cultura.uol.com.br/aovivo/).

## Build, tests and device checks

The final full run passed **543 tests: 484 shared-core, 26 Android unit, and 33 desktop tests**, with zero failures. This includes **19 new Live TV tests**. The Android debug APK and desktop Kotlin code compiled. New tests exercise current IPTV-org joins/labels, nullable records, canonical provider IDs, deduplication, blocklisted direct streams, URL/DNS safety, M3U parsing and sanitised persistence, finite source ranking/retry, failures and geographic labels, favourite/history persistence, truthful unavailable states, XMLTV feed matching, DST conversion, external-entity rejection and catalogue fallback.

The Android TV emulator was also used, but its feature launch hit an input-focus ANR and activity launch timeout. At the time its logs showed CPU pressure around 69% and load over 7; its launcher and system services were also busy. The emulator image refused root access, so the protected main-thread trace could not be read. **Emulator playback is not verified and the ANR is unresolved.** Physical-TV playback is the successful runtime result; do not treat the emulator run as a pass.

Device screenshots exposed two issues that were corrected: automatic keyboard opening on entry, and Imgur returning outage graphics with HTTP 200 instead of logos. Initial focus now goes to the channel list; unsupported/outage artwork falls back to a TV icon.

The final rapid-launch smoke test also caught a Media3 session-ID collision between overlapping activity lifecycles. Live sessions now receive unique process-wide IDs; the corrected APK was rebuilt before repeating the device tests. The smoke script fails explicitly when any requested broadcaster produces no video frame.

The corrected rapid-launch rerun passed all four broadcasters. Physical remote checks confirmed D-pad channel-row navigation, hold-OK actions, adding a favourite (confirmed in Android preferences), and selecting BBC One handing off to the installed `bbc.iplayer.android` app. This verifies app launch, not BBC channel playback or account eligibility. Live TV Brasil continued while the guide and favourite menu were browsed. Full-screen testing caught and corrected an empty guide container occupying video space; the final screenshot confirmed video fills the TV, and Back restored the guide. In Explore the World, CH+ tuned DW English and CH− returned to TV Brasil, with actual decoded video frames observed.

Repeated final builds encountered Windows locks on old Gradle result/resource directories. A fresh local output directory and disabled file watching were used for final verification, rather than deleting user data or changing the repository's build layout permanently.

## Remaining limits

- The direct launch catalogue is deliberately small: four reviewed international broadcasters. UK and Irish mainstream entertainment largely uses provider handoffs; this is not a universal cable subscription replacement.
- No guarantee of EPG coverage: unavailable guide sources show “Live broadcast” and never fabricated titles. XMLTV matching/time conversion is tested; populated schedules were not independently validated for every target.
- Provider-home launching does not guarantee a channel deep link, account login, live-device support, or UK rights. No unsupported channel intents are invented.
- Optional imported playlists require the viewer's entitlement and manual refresh. Built-in catalogue metadata updates automatically. Credential-bearing/private-network playlists are intentionally unsupported.
- Logo availability depends on upstream artwork; generic icons are expected where images cannot safely load.
- The desktop build is preserved, but has no new Live TV UI in this release.

See [controls, implementation and build/install instructions](LIVE_TV.md). The repeatable `scripts/live-tv-smoke.ps1` reports actual first-video-frame events on a specified ADB device.
