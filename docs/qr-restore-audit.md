# Sign-in persistence and QR restore: audit

Read-only audit for `PLAN_QR_RESTORE.md`, Step 1. Code references are to branch `feature/qr-restore`.

## Where each sign-in lives

| Sign-in | Stored in | Secret handling |
|---|---|---|
| Jellyfin | `prefs "jellyfin"`: `server`, `token`, `userId`, `deviceId` | Only the access token is kept, never the password (`JellyfinClient.kt:22`). Token is plain in prefs. |
| Seerr | `prefs "seerr"`: `server`, `cookie` (`connect.sid=...`) | Only the session cookie is kept (`Seerr.kt:107`). Cookie is plain in prefs. |
| Sonarr / Radarr (by hand) | `prefs "cinema_settings"` holds URL; key in `SecretStore` as `manual_<ARR>_key` | Encrypted with an Android Keystore AES key (`AndroidSecretStore.kt`). |
| Download client password | `SecretStore` `client_pw_<url>`; user in prefs | Encrypted as above. |
| Subsonic (Music) | URL and user in prefs; password and key in `SecretStore` | Encrypted as above. |
| Stremio key | `SecretStore` `stremio_key` | Encrypted as above. |
| Region, helper URLs, integration toggles, watchlist | `prefs "cinema_settings"` | Plain. Not secret. |

`CinemaSettings.kt` and `Preset.kt` cover everything in the last three rows.

## How long each session lasts

- **Jellyfin.** The token comes from `AuthenticateByName` and is kept until the server revokes it. Nothing in the app sets an expiry. Jellyfin tokens are long-lived, so they are unlikely to lapse on their own. They are lost if the user deletes the device in Jellyfin, changes the account's password, or revokes the key.
- **Seerr.** The cookie is an express session. The app sets no expiry and does not know the server-side lifetime, so I cannot confirm from this code how long it lasts. Treat it as possibly lapsing.
- **What the app does on a 401.**
  - Jellyfin (`JellyfinClient.kt:358`): on 401 it logs "Token rejected; signing out" and calls `disconnect()`, which removes `token` and `userId` but keeps `server`.
  - Seerr (`Seerr.kt:166`, `:174`, `:257`, `:336`): on 401 it calls `signOut()`, which removes only `cookie`.
  - **Gap.** After either happens, `hasServer` stays true, so `CinemaActivity.kt:91` opens the movies panel, not first run. `MoviesPanel.kt:1895` then shows a "Connect Jellyfin" card. Nothing tells the user that a saved sign-in was lost, and it is not a blank page. Step 4's "Sign back in" screen is needed here.

## Preset format

`Preset.kt` already parses `cinema-preset.json` from the APK. It holds Jellyfin and Seerr as `username`/`password`, plus Sonarr, Radarr, clients, Subsonic and region. It is a build-time file, not a backup. Reusing its shape for the restore bundle fits. The restore bundle needs token fields for Jellyfin and Seerr in place of passwords, because the phone does not send passwords through to the TV after first sign-in.

## Why the data is wiped

Two different things are involved, and only one of them is data loss.

1. **Update with a different signing key.** Android refuses to install the APK (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). The existing install is left alone, so nothing is wiped. `scripts/ci/deploy-tvs.sh:47` runs `adb install -r`, so a mismatch would show as "install failed" and leave the data in place. Release and debug builds both use the `release` signing config (`app/build.gradle.kts:76`), so the normal path does not mismatch.
2. **Uninstall, then reinstall.** This wipes the app's private data, including prefs. The Keystore key is also removed with the app, so the encrypted secrets can no longer be decrypted even if the prefs file survives. This is the probable cause of a "wipe". I can't confirm which one happened from the repo alone. Daniel should say whether the TV showed "install failed" or whether the app was uninstalled first.

Also, `AndroidSecretStore.get` returns `null` silently when decryption fails. A wiped Keystore key therefore looks like "no key saved" everywhere, with no warning.

## Backup

`AndroidManifest.xml:29` has `android:allowBackup="false"`. Android Auto Backup therefore does not run, and nothing is copied off the device. A restore of the prefs file would not bring back the Keystore key anyway, so the Keystore-encrypted values would stay unreadable. Turning backup on would not help, and the restore bundle has to be an explicit file.

## Surprises

- The plan says the branch should be cut from `main`, with Stremio merged there as PR #34. In this repo, `main` (`0d0b58e`) does not contain the Stremio commit `4416101`, and no PR #34 is visible in its history. The branch was cut from `feature/stremio-import` instead, so the Stremio settings are present.
- `adb devices` lists `192.168.1.220:5555` and `192.168.1.232:5555`, not the TCL at `192.168.1.217:5555`. The plan says not to touch `.220`. The TCL is not connected right now.
- Nayuki's QR library is not in the repo. `QrCode.java` needs three siblings, so four MIT files were vendored into `core/src/main/java/io/nayuki/qrcodegen/` (`QrCode`, `QrSegment`, `BitBuffer`, `DataTooLongException`). The plan allowed one file; this is the minimum that compiles. Each keeps its license header.
