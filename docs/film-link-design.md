# Film Link: design note (CIN-64) and implementation notes (CIN-65)

Film Link is a game for the TV app: start at one random film and reach another by hopping only through connected
films, in as few hops as you can. The design was reviewed and approved by Daniel on 2026-11-21 from a high-fidelity
mockup of six TV screens (the film page and "More films like this" today, the same two with the game on, the start
and target reveal, and the completion screen).

## Rules

- **Connections.** The only film-to-film link is the title page's "More films like this" row: TMDB's recommendations
  for the film, through Seerr, films only, never the film itself, no repeats, the first 16 (`FilmLinkRules.connected`).
  The TV app has no cast or filmography navigation, so v1 uses this row alone.
- **No shortcuts.** No global search, no arbitrary title jumps, no series, no streaming and no direct jump to the
  target while a run is on. The game is its own screen over the app, so normal Cinema navigation is out of reach.
- **Hops.** A run starts at zero. A hop to a film the current page connects to counts exactly once, including the
  final hop onto the target. Focus changes, retries and failed loads never count.
- **Back** returns to the previous film, is free, and does not undo a hop. At the start film, Back leaves the game.
- **Revisits.** The route is the path from the start. A film already on the route cannot be chosen again (it is dimmed
  and marked "Visited"). After Back, the film left is off the route again, so hopping to it again counts again.
- **Dead ends.** A film with no connections shows "Dead end", with Back and Give up still available.
- **Give up** ends the run, shows the route so far and the target, and offers Play again.
- **Timer.** Untimed by default. Timed shows a clock in the HUD; it pauses when the app goes to the background and stops
  on arrival or giving up.
- **Completion.** Reaching the target completes the run exactly once. A finished run accepts no further hops.

## Data and graph strategy

- Start films come from Seerr's popular-films pages (`/api/v1/discover/movies`), two random pages of the first five,
  films with a poster only (`FilmLinkSeeds`).
- A film's connections come from `SeerrClient.recommendations`, fetched once per session and kept
  (`CachedFilmGraph`); a failed lookup is not kept. Pair checking and play use the same cache, so the checked route is
  the route the player sees.
- **Pair generation** (`FilmLinkPairs`): pick a random seed as the start, run a breadth-first search over the graph to
  4 hops with at most 30 lookups, and pick a random film found 2 to 4 hops away as the target. A pair is therefore
  solvable by construction, never trivial (one hop) and never too long. Up to 6 starts are tried; if none works, the
  screen says so and offers Try again.
- **Limits:** 30 lookups per start, 6 starts, so at most 180 lookups to find one pair, usually far fewer.
- **Fallbacks:** Seerr not signed in, no popular films, or no workable pair each show a message with Try again or Leave.
  A failed connections lookup mid-run shows "Couldn't load the connected films" with Try again.

## State and session model

All state of a run lives in one `FilmLinkRun` object (core, plain Kotlin): start, target, route, offered films, hops,
phase, mode and clock. Nothing is saved: closing the game discards the run, and no scores or history are kept (single
player, local; no leaderboard). The game never writes watch history, playback progress, the watchlist or settings.

## Screens (as approved)

1. **Reveal:** start and target posters, one line of rules, Untimed or Timed, Begin, New pair.
2. **Film page:** the title page look with Find a link, Back to the previous film and Give up in place of Stream,
   Trailer and Watchlist; the route so far top left; the "More films like this" row.
3. **Row:** moving down slides the page up, quietens the details and shows the remote hints; visited films are dimmed;
   a focused card says "+1 hop".
4. **HUD** (top right, every film page): target poster and title, hops, and time when timed. A thin teal edge along the
   top marks that the game is on.
5. **Completion:** the route with a hop count between films, hops, time when timed, Play again, Same pair new try, Exit.

Deviation from the mockup: focus on a card is Cinema's own soft saffron glow (`PosterCard`), not a drawn ring, because
the Cinema language has no outlines.

## Entry point

A "Film Link" item in the rail, between Music and My library, with its own icon. It opens the game over the app.

## Touchpoints

| File | Change |
|---|---|
| `core/.../data/movies/FilmLink.kt` | `LinkFilm`, `FilmGraph`, `FilmLinkRules`, `FilmLinkPairs`, `FilmLinkRun` |
| `core/.../data/movies/FilmLinkSource.kt` | `CachedFilmGraph`, `FilmLinkSeeds` |
| `core/.../data/movies/Seerr.kt` | `SeerrClient.discoverMovies(page)` |
| `app/.../ui/FilmLinkScreen.kt` | the game screens, HUD and timer |
| `app/.../ui/MoviesPanel.kt` | the `Dest.FilmLink` rail item, its click, and Back routing |
| `app/src/main/res/drawable/ic_cin_link.xml` | the rail icon |
| `core/src/test/.../FilmLinkTest.kt` | pair generation, legal moves, hops, timer, completion, restart, isolation, cache |

## Scope and trade-offs

- v1 is films only, TV first, local, single player. Out of scope: multiplayer, leaderboards, daily challenges,
  accounts, saved history or best scores, cast and filmography links, mobile and desktop versions.
- Hop links only come from TMDB recommendations, which are not symmetric and vary by film; the pair check uses the
  same data, so a pair that is generated is winnable at the time it is generated.
- Without Seerr signed in the game cannot start. That is the same condition under which "More films like this" falls
  back to the row a title was opened from, which has no meaning in a game.

## Verification plan

- **Automated:** `FilmLinkTest` (pair generation and reachability, bounded effort, legal moves, exact hop counting,
  completion once, back, revisits, timer with pause and resume, restart, session isolation, cached graph).
- **Gate:** `./gradlew :core:test testDebugUnitTest assembleDebug`.
- **On a TV or the Android TV emulator with a real Seerr:** one complete run with the D-pad (reveal, Begin, hop through
  the row, Back, completion, Play again), a timed run with the app sent to the background, a dead end, Seerr signed out,
  and a check that the title pages, history and playback are unchanged afterwards. Not part of the automated gate:
  it needs a real Seerr and TMDB data.

## Example route (to be confirmed)

The mockup shows Alien, Blade Runner, Blade Runner 2049, Arrival. Those titles and links are illustrative and have not
been checked against real TMDB recommendations. The real-data run above is what proves a valid route.
