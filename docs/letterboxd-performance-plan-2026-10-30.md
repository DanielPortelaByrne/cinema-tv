# Letterboxd performance review and fix plan (2026-10-30)

Handoff plan for an implementing agent. Complaint: the Letterboxd section of the Android TV app is "massively slow".
Everything below comes from reading the code. **Nothing has been measured or run yet**, so every cause is a
hypothesis until Phase 0 and Phase 1 confirm it. Work in the order given; do not skip measurement.

Repo: `C:\Users\danie\cinema-tv` (branch `main`, clean at time of writing). Create a feature branch first.
Do not commit or push unless asked. Do not touch unrelated sections (Home, Library, Downloads, player).

## 1. How the section works (read this first)

| Layer | File | Role |
|---|---|---|
| UI | `app/src/main/java/com/danielportelabyrne/cinema/ui/MoviesPanel.kt` lines ~1092-1760 | `buildLetterboxd`, `buildLbPerson`, `buildLbList`, `buildLbSearch`, `buildLbFollow`, `lbFilmRow`, `lbListRow`, `lbReviewRow`, `lbOpen` |
| Cards | `app/.../design/cinema/LetterboxdViews.kt` | `MemberCard`, `ListCard`, `ReviewCard`, `FollowCard`, `LbDots` |
| Repository | `app/.../data/letterboxd/LetterboxdRepository.kt` | `load()` (cache then refresh then parse), `posterUrl()`, follow list in SharedPreferences |
| Fetching | `app/.../data/letterboxd/LetterboxdWeb.kt` | plain `HttpURLConnection` first, hidden 1x1 `WebView` fallback behind Cloudflare check |
| Disk/memory page cache | `core/.../data/letterboxd/PageCache.kt` | 2 read threads, 2 network threads, 24-page memory LRU, `<sha1>.html` on disk |
| Parsing | `core/.../data/letterboxd/Letterboxd.kt` (`Lb`), `LetterboxdPages.kt` (`slim`) | regex parsers, `slim()` strips scripts/styles/svg |
| Images | `app/.../ui/RemoteImages.kt`, `ui/ImageCache.kt` | shared poster pipeline, 3 workers plus 1 priority worker |
| Timing hooks | `core/.../ui/Perf.kt` | `Perf.took/mark/begin/end`, logs under tag `Perf`, only when the app is debuggable (`CinemaApp.kt:14`) |

Cold open of the Letterboxd home page for N followed members issues **4N page loads** (`/u/`, `/u/films/diary/`,
`/u/watchlist/`, `/u/likes/films/`), then **one poster-URL lookup per visible film** (`lb.posterUrl(slug)`, one HTTP
request each, up to ~40 per row, 2N+1 rows), then one image download per poster. Letterboxd has no API; pages sit
behind a Cloudflare check that plain requests often fail, so a hidden WebView loads the page.

## 2. Suspected causes, ranked (verify each in Phase 1)

### A. Browser fallback is serialized and can latch on permanently (most likely the main cause)
- `LetterboxdWeb.page()` and `.json()` run `viaBrowser` inside `synchronized(browserLock)`. One WebView per
  `LetterboxdWeb`, so each lane (2 page lanes + 1 poster instance) does one load at a time. A load can take up to
  25 s (`done.await(25, SECONDS)`), polling every 250 ms (`poll`, up to 80 attempts).
- **Latch bug:** `plain()` starts with `if (refusals >= PLAIN_REFUSALS) return null`. `refusals` only resets inside
  `plain()` after a 200 response, but once it reaches 3 `plain()` returns immediately and can never succeed again.
  After three early refusals (very likely on a cold cookie jar) the instance sends **every** later request through
  the WebView for its whole lifetime, even after the WebView has obtained the clearance cookie that would let plain
  requests through. Each `LetterboxdWeb` instance has its own counter.
- Poster lookups (`posterUrl`) share a single `posterWeb`. With the latch tripped, up to 4 pool threads
  (`lbPosterPool`) queue on one browser lock, so posters load strictly one at a time, each a full WebView
  navigation of a tiny JSON endpoint. A row of 40 posters is therefore tens of seconds to minutes.
- `viaBrowser` post-processing runs on the **main thread**: `evaluateJavascript(READ_PAGE_JS) { out -> html = decodeJsString(out) }`
  parses a 1-2 MB JSON string literal with `JSONArray` inside the callback.
- The WebView has JavaScript, DOM storage and every subresource except images enabled; only one consent-vendor
  host is blocked. Ads, analytics, fonts and CSS still load for pages that are only being scraped.

### B. Poster N+1 pattern
- One request per film for the poster URL, then another for the image. `posters.json` persists results, but the
  first run for each film is a network round trip. `lbPosters()` (person favourites, search-result previews, list
  fan) blocks a `shelfPool`/`slowPool` thread on up to 20 s per future.
- `lbListRow` fetches up to 5 preview posters per list card, so a Lists tab of 20 lists is up to 100 lookups.
- Check whether the poster URL can be derived or parsed from the list HTML already fetched (the
  `LazyPoster` placeholders may carry `data-poster-url` or image paths). If not derivable, batch/limit the lookups.

### C. Duplicate work per page
- `PageCache.load` calls `done` once with the saved copy and again with the fresh copy if it differs. Each call
  re-parses the HTML and the UI callback does `removeAllViews()` and rebuilds the whole row (`lbFilmRow`) with up to
  40 new `PosterCard`s, 4 per frame. With 4N loads on the home page that is many full row rebuilds, and
  "Recently watched" is rebuilt via `drawRecent()` as each member's diary arrives.
- `lbFilmRow` calls `frost?.refresh()` for every poster that arrives; `FrostView` snapshots and blurs
  (`RenderEffect` blur on API 31+, `LetterboxdViews.kt:93`). Check how often this fires during a cold open.

### D. Parse and storage cost on background threads
- `LetterboxdPages.slim()` uses three `DOT_MATCHES_ALL` regexes over pages up to ~1.4 MB; the SVG regex uses a
  tempered greedy token (`(?:(?!aria-label="...").)*?`), which is slow and can recurse deeply in `java.util.regex`.
  Failures are swallowed by `runCatching`, so a `StackOverflowError` would silently return null.
- `Lb.films`/`diary`/`reviews`/`lists` create many `Regex` lookups per chunk (the `patterns` map is cached, but the
  work is still O(chunks x patterns)); `profile()` and `section()` run `substring` on large strings.
- `LetterboxdRepository.load` computes SHA-256 over `html.toByteArray()` for every load that misses the in-memory
  parsed map, and writes a Java-serialized snapshot (`parsed-v2:` key). Java serialization is slow on Android;
  confirm the snapshot read is actually faster than a re-parse (it may not be).
- `following` getter reparses `JSONArray(s)` twice per element (`JSONArray(s).length()` then `JSONArray(s).getString(it)` in a loop), O(n^2). It is called in `buildLetterboxd`, `buildLbPerson`, `buildLbList` and from click handlers.

### E. Threading and queue shape
- Letterboxd page fetches: `PageCache.network` has 2 threads, `pageLanes` has 2 browsers, so at most 2 pages in
  flight. 4N pages (e.g. 4 members = 16 pages) queue behind each other, and the page that is *visible* (profile,
  first diary) is not prioritised over the 40th-percentile-off-screen watchlist/likes pages.
- `wanted[path]` cancellation only drops pages that have not started, and only if the caller passed `stillWanted`.
  `buildLetterboxd` and `buildLbPerson` do **not** pass it, so leaving the page does not cancel its queued fetches.
- Parse runs on the PageCache `reads`/`network` threads, which are also the threads that do I/O, so a slow parse blocks the next fetch.
- Image decode is `RGB_565` and shaded in a mutable bitmap; fine, but verify `Perf` lines for "letterboxd image".

### F. Possibly relevant, lower priority
- `isMinifyEnabled = false` and an empty `app/src/main/baseline-prof.txt` match for "letterboxd" (0 lines): the
  Letterboxd code paths are not in the baseline profile. Only worth doing after the above.
- Cache TTLs are short for slow-changing data (`/u/` 2 h, diary 30 min, watchlist/likes 1 h). Stale-while-revalidate
  already shows the saved copy first, so TTL mostly affects how often the browser fallback runs.

## 3. Phase 0 - Baseline (do before changing code)

Goal: numbers for a cold and a warm open, so every later change can be judged.

1. Build and run the tests that already exist: from `C:\Users\danie\cinema-tv`, `./gradlew :core:test` (Letterboxd
   tests: `LetterboxdParserTest`, `LbParseTest`, `FollowPageTest`, `PageCacheTest`). Record pass/fail. If Gradle or
   the JDK/Android SDK cannot run in this environment, say so and continue with code-reading and unit-level work.
2. The app already logs timing with `Perf.took(...)` and `Log.i("Letterboxd", ...)`. Existing messages:
   `letterboxd parse <path> N ms`, `letterboxd data <path> N ms`, `letterboxd image N ms`,
   `letterboxd poster image N ms`, and `browser <path>: N chars in N ms` from `LetterboxdWeb`. Use `adb logcat -s Perf Letterboxd Images`
   if a device or emulator is available; otherwise add the extra marks in step 3 and describe the manual capture.
3. Add timing marks (debug-only, via `Perf`) where none exist:
   - `LetterboxdWeb.plain`: URL path, result (200 / challenge / 403 / 503 / error), ms, and the `refusals` value.
   - `LetterboxdWeb.page/json`: time spent *waiting* for `browserLock` versus time spent in `viaBrowser`.
   - `LetterboxdRepository.posterUrl`: cache hit, plain hit, browser hit, total ms.
   - `PageCache.load`: saved-hit vs miss, and queue wait time before `fetch` starts.
   - `LetterboxdPages.slim`: input/output length and ms.
   - `MoviesPanel.lbFilmRow`: number of rebuilds per `build()` and per row, and `frost?.refresh()` count.
4. Write the baseline into a table at the end of this file (section 7) for: (a) cold open of the Letterboxd home with
   cleared app data, (b) warm open with a full disk cache, (c) opening one member, (d) opening one list.
   Columns: time to first content, time to all rows' posters, number of page fetches, number of poster lookups,
   number of browser (WebView) loads, number of row rebuilds.

## 4. Phase 1 - Confirm or kill each hypothesis

For each item record "confirmed / refuted / unmeasurable here" with the evidence (a log line or test result).

1. **Latch (A):** write a unit test or small harness around the `refusals` logic. The logic is in the Android-only
   `LetterboxdWeb`, so the easiest route is to extract the counter into a pure class in `core`
   (e.g. `PlainGate` with `allow()`, `success()`, `refusal()`, `reset()`) and test: three refusals then a success
   must not be impossible. Then confirm on device (or in logs) that after the first three refusals no `plain` request is ever attempted again.
2. **Serialization (A/B):** from logs, show how many `browser ... in N ms` lines exist for `/film/<slug>/poster/std/230/`
   and whether their timestamps are strictly sequential.
3. **Main-thread decode (A):** show the duration of `decodeJsString` for a ~1.5 MB page. A micro-benchmark in a unit
   test (JVM, `org.json` available through the existing test deps - check `core/build.gradle.kts`) is acceptable as an indicator only.
4. **`slim` and parser cost (D):** load a saved real page fixture (see `core/src/test/.../LetterboxdParserTest.kt`
   and `LbParseTest.kt` for fixtures; capture a larger real page from the device cache `cacheDir/letterboxd/*.html` if available)
   and time `slim`, `Lb.films`, `Lb.diary`, `Lb.profile`, `Lb.reviews`, `Lb.lists` over 50 iterations after warm-up. Report median/p95.
5. **Row rebuilds (C):** count how many times `lbFilmRow` runs for one cold open (expected: at least 2 per row for rows with stale data).
6. **Poster derivability (B):** inspect saved HTML for a watchlist or list page and check whether poster image URLs
   are present in the markup (search `data-poster-url`, `data-image`, `<img`, `srcset`, `LazyPoster` attributes). Report yes/no with a sample.

## 5. Phase 2 - Fixes, in order of expected payoff

Keep each change small, separately testable, and behind the existing public API where possible. After each, rerun
the Phase 0 table row and the core tests. Stop and report if a fix does not move the numbers.

1. **Fix the plain-request latch.** Replace the permanent `refusals >= 3` short circuit with a time-based or
   success-probing policy: after the WebView has loaded a page (so the clearance cookie exists) re-allow plain
   requests, and re-probe periodically (e.g. one plain attempt per 60 s or after every browser success). Share one
   gate across all `LetterboxdWeb` instances rather than one counter each. Keep unit tests for the gate in `core`.
2. **Stop serializing poster lookups.** Options, in order of preference:
   a. derive or parse poster URLs from the already-fetched page HTML (if Phase 1 step 6 says it is possible), removing the lookup entirely;
   b. otherwise make `posterUrl` plain-first with the shared gate, never fall back to the WebView for a JSON endpoint
      unless plain has failed *after* a recent success probe, and bound concurrent lookups;
   c. dedupe in-flight lookups for the same slug (two rows can ask for the same film simultaneously);
   d. persist negative results briefly so a missing poster is not retried on every open.
3. **Move WebView result handling off the main thread.** Do not call `decodeJsString` inside the `evaluateJavascript`
   callback; hand the raw string to the waiting worker thread and decode there. Also consider returning the page via a
   `@JavascriptInterface` bridge instead of a JSON-escaped literal. Keep the existing `READ_PAGE_JS` consent-root removal.
4. **Make the WebView lighter.** Extend `shouldInterceptRequest` to refuse non-document subresources that do not affect
   the clearance check or HTML (analytics, ads, fonts, CSS, media). Verify against a real challenge page that
   clearance still succeeds; if it breaks, revert that part and keep the narrower block list.
5. **Prioritise and cancel page loads.** Give `PageCache`/`LetterboxdRepository` a priority (visible first: profile and
   first diary before off-screen watchlist/likes) and pass `stillWanted` from `buildLetterboxd` and `buildLbPerson`
   (`gen == generation`) so leaving the page cancels queued fetches. Keep the 2-lane limit unless Phase 1 shows the
   lanes are not the bottleneck; if so, consider 3 lanes but check device memory first.
6. **Avoid duplicate rebuilds.** In `lb.load`'s `done`, skip the rebuild when the fresh parsed value equals the previous
   one (data classes already have `equals`; compare the parsed values, not just HTML). Rebuild `lbFilmRow` content in
   place when only a few films changed instead of `removeAllViews()`. Coalesce `frost?.refresh()` calls (one per frame or per 100 ms).
7. **Cut parse and storage cost.** Replace the three `slim` regexes with a single linear scan (index-of based), or
   drop `slim` and parse only the sections needed; keep behaviour identical (see `PageCacheTest`/`LbParseTest`). Fix
   `following` to parse JSON once. If Phase 1 shows the Java-serialized snapshot is slower than parsing, remove it;
   otherwise replace the per-load SHA-256 of the whole page with a cheap key (length + `lastModified` from `PageCache`).
8. **Reduce first-open fan-out.** On the home page, load only what is visible first (member cards: profile and diary,
   then the first row), then start watchlist/likes rows via the existing `pending`/`fillNear` mechanism (they currently
   fetch eagerly through `lb.load` before rows are near the screen). Cap rows rendered at once for large N.
9. **Last:** add Letterboxd classes to the baseline profile, and re-check `LB_ROW_LIMIT` (40) and `CARDS_PER_FRAME` (4).

## 6. Acceptance criteria and guardrails

- A warm open of Letterboxd home shows content from disk in under 1 s and does not start a WebView load unless a page is
  actually stale and plain requests have failed.
- A cold open shows the member cards and "Recently watched" row populated within a time target to be set after Phase 0
  (propose it; do not invent one before measuring). No stage may depend on one WebView navigation per poster.
- No change to what is displayed: same rows, same ordering, same fallbacks ("Couldn't load..." notes).
- All existing core tests pass; add tests for any new pure logic (gate, in-flight dedupe, coalescing). Do not weaken or delete tests.
- Do not scrape differently than today in a way that increases load on Letterboxd (no more concurrent requests than
  now; respect the existing 2-lane cap). Do not add credentials, cookies handling beyond the existing `CookieManager` use, or sign-in.
- Debug-only instrumentation must go through `Perf` (no logging in release builds beyond what already exists).
- Report honestly: for anything you could not run (device, emulator, Gradle), say it was not verified.

## 7. Results log (agent fills in)

Device: Chromecast HD (Android TV 14, 192.168.1.220), debug build `com.danielportelabyrne.cinema.perf` (`-Pcinema.appIdSuffix=.perf`), followed: EvaCawley, DanielPortelaB. Cold = Letterboxd cache cleared (`run-as ... rm -rf cache/letterboxd`); warm = cache kept. Times from logcat `Perf`/`Letterboxd` lines, from "home open". Two runs each unless stated. Single network, so ±8 s run to run.

| Scenario | Before | After | Notes |
|---|---|---|---|
| Cold home, first content (first profile card) | 30.1 s, 15.4 s (avg 22.7) | 12.7 s, 14.5 s (avg 13.6) | Probe gate (2.1) and decode off main thread (2.3). Spread is large; treat as indicative. |
| Cold home, all posters (last poster drawn) | 47.4 s, 40.1 s (avg 43.8) | 36.7 s, 37.2 s (avg 37.0) | Limited by the one poster WebView (serialised, 0 overlaps) and 8 page loads that take 10–14 s each. |
| Warm home, first content | 1.35 s | 1.19 s | No warm-path change. Warm cost is mostly main-thread drawing. |
| Warm home, all posters | 5.16 s | 4.53 s | Images from disk; no fetches or WebView loads on either build. |
| Open member | not measured | not measured | Not run on the device in this pass. |
| Open list | not measured | not measured | Not run on the device. `loadWatches` makes one page request per member per film; see follow-ups. |
| Page fetches (cold home) | 8 / 8 | 8 / 8 | Unchanged. |
| Poster lookups (cold home) | 35, 39 | 39, 39 | Unchanged: every lookup is unique (no in-run duplicates), so de-duplication would not reduce them. |
| WebView loads (cold home) | 42, 47 | 47, 47 | Unchanged. No extra load. |
| Plain (no-browser) attempts per cold home | 13, 10 (37 and 45 skipped by the latch) | 4, 4 (51 and 51 skipped) | Fewer refused requests to Letterboxd. Probes are one a minute. |
| Main-thread JSON decode (cold home, summed) | 1.7 s, 3.4 s (max 1.44 s in one frame) | 0 on main; 3.0–3.1 s on the worker (max 1.06 s) | Decode moved off the UI thread. Does not shorten the browser lock hold. |
| Row builds per cold home | 11 | 11 | Unchanged; each row is built twice (placeholder, then data). Not targeted. |
| Frost refreshes | one per poster drawn (about 30) | same | Each `frost.snapshot` takes 1–3 ms; not a bottleneck. |

**Follow-up pass (same device, same two members).** Cold home, first content / last poster: 11.6 s / 33.0 s with the bridge, 12.7 s / 29.3 s with in-page poster fetches (one run each). WebView loads per cold home: 47–54 before in-page fetches, 9 after (38 of 39 lookups answered in-page). Mean browser page load: 6.3 s before the bridge, 4.2 s after. Warm open: first content 1.2–1.7 s, all posters 4.5–5.8 s (noisy; no warm-path change). Snapshot read 47–247 ms per page against a re-parse of 479–3060 ms, so the snapshot stays. List "movies I really need to watch" (169 films, first 100 shown): watch checks requested up front 200 (code path), now on demand, 72 requested in the first 30 s; row builds during open 108 → 9, because later renders update the cards in place. Badges checked against the cached diary: *Saturday Night Fever* shows the EVA badge in place.

## 8. Hand-back format

Final message should list: what was confirmed or refuted (section 4), what was changed (files and one line each),
before/after numbers (section 7), tests added and their results, anything not verified, and any follow-ups worth a separate task.
