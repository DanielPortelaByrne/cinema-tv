# Cinema for Android TV

Cinema is a TV app for Google TV, Android TV and Chromecast with Google TV. It brings your **Jellyfin** library and what is streaming on Netflix, Prime Video, Disney+ and the rest together in one place, and lets you request anything you don't have yet through **Seerr**. It also shows downloads as they arrive from **Sonarr**, **Radarr** and your **torrent client**.

Everything is set up on the TV the first time it opens. Nothing about anyone's server is built in.

For the Linux desktop and Steam Deck version, see [Flatpak installation, controls and building](flatpak/README.md).

## What it needs

| Service | Required? | What it's for |
|---|---|---|
| Jellyfin | **Yes** | Your library, playback, resume points. Sign in with username and password, or Quick Connect. |
| Seerr (Jellyseerr or Overseerr) | Optional | Requesting films and series. Sign in with your Jellyfin account or a Seerr email account. |
| Sonarr and Radarr | Optional | The Downloads page, single-episode requests, deleting from the library. Found automatically through Seerr when you sign in as a Seerr admin, or entered by hand (address and API key). |
| Torrent client | Optional | "Download next" and "Download now" controls, live speed and seeds. Works with **qBittorrent**, **Transmission** and **Deluge**. Any other client (rTorrent, Flood, Usenet clients...) still shows progress and can be cancelled through Sonarr and Radarr. |
| Letterboxd | Optional | Follow anyone's public profile from the Letterboxd page. Nothing is signed in. |

The streaming catalogue (what is on which service) comes from JustWatch for the country chosen during setup.

### Optional helper services

Two extra services can run next to Seerr. They are **not part of this repo**, and Cinema works without them. Their addresses go in **Settings → Helper services**.

- **Request helper.** It watches requests, retries searches, and reports why a request is stuck. Cinema calls `GET /requests`, `POST /requests/{id}/search`, `GET /torrents`, `POST /torrents/{hash}/next|now` and `POST /media/{tv|movie}/{tmdbId}/clear-stale`, each sent with the header `X-MCM-Action: 1`. Without it, "Search again" goes straight to Sonarr/Radarr, and torrent controls talk to the client directly.
- **Stream service.** It plays a film before it has downloaded, through `POST /start` and `POST /prepare`. Without it, the Stream now button is hidden.

## Installing on a TV

1. Get an APK. Either download one someone has built, or build it yourself (see below).
2. Pick one of these ways to put it on the TV:
   - **Downloader app.** Install *Downloader by AFTVnews* from the Play Store on the TV. Allow it to install unknown apps (Settings → Apps → Security & restrictions → Unknown sources). Then open the APK's URL in Downloader.
   - **adb over the network.**
     1. On the TV, turn on Developer options: Settings → System → About, then select *Android TV OS build* seven times.
     2. Turn on Settings → System → Developer options → USB debugging (or Wireless debugging on Android 11 and later). Then run:
        ```bash
        adb connect <tv-ip>:5555
        ```
        ```bash
        adb install app-release.apk
        ```
3. Open **Cinema** from the TV's apps row and follow the setup.

## Building

You need JDK 17 and the Android SDK (platform 36). Android Studio installs both.

```bash
git clone <this repo> && cd cinema-tv
```

Point Gradle at your SDK in `local.properties` (Android Studio writes this for you):

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

Then:

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Install it on a TV or the Android TV emulator (Device Manager → TV → Android 14+):

```bash
./gradlew installDebug
```

### Release builds

Release builds are signed with a key in `release.jks`, described by `keystore.properties` in the repo root. **Neither file is ever committed.**

```properties
storeFile=release.jks
storePassword=...
keyAlias=cinema
keyPassword=...
```

To make your own key:

```bash
keytool -genkeypair -keystore release.jks -alias cinema -keyalg RSA -keysize 4096 -validity 36500
```

Then build:

```bash
./gradlew assembleRelease
```

Without `keystore.properties` the release APK is unsigned and won't install. **Back up the key**: an installed copy can only be updated by an APK signed with the same key.

### Building with Nix

```bash
nix build
```

This gives `result/cinema-tv-unsigned.apk`. Sign it before installing:

```bash
apksigner sign --ks release.jks --out cinema.apk result/cinema-tv-unsigned.apk
```

After changing dependencies, refresh `nix/deps.json`:

```bash
$(nix build --no-link --print-out-paths .#cinema-tv.mitmCache.updateScript)
```

### Building with a preset

A preset bakes your setup into the APK, so the TV skips setup and signs in by itself. Write a JSON file. Every key is optional:

```json
{
  "jellyfin": { "url": "http://192.168.1.5:8096", "username": "you", "password": "..." },
  "seerr": { "url": "http://192.168.1.5:5055" },
  "sonarr": { "url": "http://192.168.1.5:8989", "apiKey": "..." },
  "radarr": { "url": "http://192.168.1.5:7878", "apiKey": "..." },
  "qbittorrent": { "username": "admin", "password": "..." },
  "region": "IE",
  "requestHelperUrl": "http://192.168.1.5:8787",
  "streamServiceUrl": "http://192.168.1.5:9000",
  "music": { "url": "http://192.168.1.5:4533", "username": "you", "password": "..." }
}
```

- Seerr signs in with the Jellyfin login unless it has its own `username` and `password`. For a local Seerr account, add `"signIn": "local"` and an `email`.
- `qbittorrent`, `transmission` and `deluge` logins are used when Sonarr or Radarr hide the client's password. The address still comes from Sonarr and Radarr.
- Jellyfin needs a username and password, not an API key: watch progress belongs to a user.
- `music` is a Subsonic server (Navidrome, Gonic, Airsonic). Use `username` and `password`, or an OpenSubsonic `apiKey` instead.

Pass the file to Gradle, or set `CINEMA_PRESET`:

```bash
./gradlew assembleRelease -Pcinema.preset=/path/to/preset.json
```

The build fails on invalid JSON or an unknown key. The app applies a preset once. Changes made in Settings stay until a build with a different preset is installed; Reset everything brings the preset back.

With Nix, pass a file or an attrset:

```nix
cinema-tv.packages.x86_64-linux.default.override { preset = ./preset.json; }
```

**Anyone with the APK can read the preset**, and with Nix it also sits in the world-readable store. Do not share a preset build.

## Tests

```bash
./gradlew :core:test testDebugUnitTest
```

GitHub Actions runs three jobs on every push to `main` and every pull request: the shared `core` tests (which need only a JDK), the Android TV unit tests with a debug build, and the Linux desktop tests with the Flatpak's jar bundle.

The unit tests cover the parsing and network logic without a device: Jellyfin, Seerr, Sonarr/Radarr, the three torrent clients (against fake servers), Letterboxd, the catalogue and the player maths. Run them before every PR.

### Checking it on a TV or emulator

Debug builds take a few extra launch options, so screens can be checked without signing in:

```bash
adb shell am start -n com.danielportelabyrne.cinema/.CinemaActivity --es debug_setup_step settings
```

- `debug_setup_step` opens a setup page: `welcome`, `jellyfin`, `signin`, `seerr`, `arr`, `client`, `region`, `advanced` or `settings`.
- `debug_cinema_dest` opens a Cinema page: `letterboxd`, `downloads`, `library` or `loading`.

## How the code is laid out

This repository holds both Cinema apps and the code they share:

| Folder | What it is |
|---|---|
| `core/` | Plain Kotlin for the JVM, shared by both apps: the service clients, parsing, the catalogue and the Continue/Restart rules. It knows nothing of Android or the desktop. Its tests run with just a JDK. |
| `app/` | Cinema for Android TV. |
| `desktop/` | Cinema for Linux and the Steam Deck (Compose for Desktop, mpv). |
| `flatpak/` | Packaging for the desktop app. |

`core` asks the device for only a few things (private folders, key-value preferences, encrypted secrets, a device name and a log) through the `Platform` interface in `core/.../platform/`. The TV implements it in `app/.../device/AndroidPlatform.kt` and the desktop in `desktop/.../DesktopPlatform.kt`. Anything that needs Android itself (WebView, MediaCodec, the Keystore) stays in `app/`.

```
core/src/main/kotlin/com/danielportelabyrne/cinema/
  platform/                Platform, Preferences, SecretStore and Log: what the apps provide
  data/settings/           CinemaSettings (addresses, country, helpers)
  data/jellyfin/           Jellyfin client, sign-in, network discovery, playback reporting
  data/movies/             Seerr, Sonarr/Radarr, the download queue, and the torrent clients:
                             DownloadClients.kt (shared interface), Qbit.kt, Transmission.kt, Deluge.kt
  data/letterboxd/         Reading public Letterboxd pages
  data/stream/, player/, search/   Stream ranking, player maths, search input
  ui/WatchProgress.kt      The Watch now / Continue / Restart rules (and EpisodeCardState.kt)

app/src/main/java/com/danielportelabyrne/cinema/
  CinemaActivity.kt        The only screen: Cinema, with setup and Settings over it, and remote-key routing
  ui/MoviesPanel.kt        Cinema itself: the rail, Home, Films, Series, Letterboxd, Library, Downloads, title pages
  ui/PlayerActivity.kt     Full-screen player (Media3 with FFmpeg audio), plus PlayerChrome for its controls
  ui/setup/SetupFlow.kt    First-run setup and the Settings pages
  ui/room/                 Sheets: sign-in, confirmations, reviews (InfoSheet)
  device/                  AndroidPlatform and AndroidSecretStore (Keystore-encrypted secrets)
  data/letterboxd/         The Letterboxd page cache (WebView), data/stream/StreamAndroid.kt (MediaCodec)
  design/                  The Cinema look: colours, type, buttons, glass and glow
```

- **Settings and secrets.** Jellyfin and Seerr keep their address and sign-in token in their own private preferences. Everything else lives in `CinemaSettings`. Passwords and API keys go only through `SecretStore`, which encrypts them with an Android Keystore key on the TV (and with a key file in the app's folder on the desktop); they are never written in plain text or logged.
- **Network calls.** They block and never throw, so callers run them on a background thread and treat `null` as "couldn't reach it".

### Adding a torrent client

1. Add it to `ClientKind` in `DownloadClients.kt`, using the `implementation` name that Sonarr/Radarr report in `/api/v3/downloadclient`.
2. Write a `DownloadClient` for it. Report each torrent's state in qBittorrent's words (`queuedDL`, `pausedDL`, `downloading`, `stalledDL`, `metaDL`, `checkingDL`, `error`, `uploading`), so the Downloads page needs no changes. `Transmission.kt` and `Deluge.kt` are short examples.
3. Return it from `DownloadClients.create`.
4. Add tests against a fake `HttpTransport`, like those in `DownloadClientsTest.kt`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for how to open a pull request.

## Licence

MIT. See [LICENSE](LICENSE).
