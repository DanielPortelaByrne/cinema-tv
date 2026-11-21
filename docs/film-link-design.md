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
- **Back** returns to the previous film, is free, and does not undo a hop. At the start film, Back ends the game.
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

## Entry point: an Easter egg on the film info screens

There is no menu item. On any film's (or series') info screen, pressing **Up, Up, Down, Down, Left, Right, Left,
Right** (each within 2.5 seconds of the last) opens the Film Link start menu. A series page can start a game too, but
only from a random film, since the game is films only.

The **start menu** is a full-screen "now showing" title screen: a marquee of lights, film-strip edges, the title over
Cinema's stripe ribbon, a Start and Finish pair of posters that follows the choices, and three option rows with Play
focused (so OK alone starts a game on the defaults):

- **Start:** this film (default, only when the page is a film) or a random film.
- **Finish:** a random film (default) or a film the player chooses. Choosing takes them to Search, where OK on a film
  picks it instead of opening it; Back cancels.
- **Clock:** no clock (default) or timed.

A random finish from a chosen start, or a random start for a chosen finish, is found so it is solvable by
construction (`pickTarget`, `pickStart`). When both are chosen, a quick bounded check (`check`) looks for a route; if it
finds none the player is asked whether to start anyway.

## The game on the info screens

Each film hopped to is the app's real info page, built in a game mode inside `openDetail`: the page's own buttons
(streaming, downloads, delete, the watchlist, favourites) and its Letterboxd sections are out of play and its stream
warm-up is skipped; "Find a link" and "Give up" take their place, and the row "More films like this" is fed from the
game's own connections. A HUD (target poster and title, hops, and time when timed) and the route so far sit over the
page, with a thin teal edge along the top marking that the game is on.

- **Back** goes to the previous film along the route. At the start film it ends the game: a game that began on this
  film leaves the player on this film's page as normal; one that began on a random film closes that page.
- **Row:** a focused card says "+1 hop"; a film already on the route is dimmed and tagged "Visited".
- **Summary** (target reached or gave up): the route with a hop count between films, hops, time when timed, Play again,
  Same pair new try, Exit. Back there is Exit.

Deviation from the mockup: focus on a card is Cinema's own soft saffron glow (`PosterCard`), not a drawn ring, because
the Cinema language has no outlines.

## Touchpoints

| File | Change |
|---|---|
| `core/.../data/movies/FilmLink.kt` | `LinkFilm`, `FilmGraph`, `FilmLinkRules`, `FilmLinkPairs` (random, `pickTarget`, `pickStart`, `check`), `FilmLinkRun` |
| `core/.../data/movies/FilmLinkSource.kt` | `CachedFilmGraph`, `FilmLinkSeeds` |
| `core/.../data/movies/Seerr.kt` | `SeerrClient.discoverMovies(page)`, `findFilm(name, year)` |
| `app/.../ui/FilmLinkMenu.kt` | the start menu |
| `app/.../ui/FilmLinkGame.kt` | the run, HUD, route, clock, summary and pair planning |
| `app/.../ui/MoviesPanel.kt` | the Easter-egg code, the questions' flow, the finish picker (Search), the game mode of `openDetail`, and Back |
| `app/.../CinemaActivity.kt` | passes remote keys to the Easter-egg detector |
| `core/src/test/.../FilmLinkTest.kt` | pair generation (random, chosen start, chosen finish, check), legal moves, hops, timer, completion, restart, isolation, cache, film matching |

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
- **On a TV or the Android TV emulator with a real Seerr:** the Easter-egg code on an info screen, the start menu with
  each option, a run with the D-pad (Play, hop through the row, Back along the route, Back at the start ending the
  game, completion, Play again), choosing a finish in Search, a timed run with the app sent to the background, a dead
  end, Seerr signed out, and a check that the title pages, history and playback are unchanged afterwards. Not part of
  the automated gate: it needs a real Seerr and TMDB data. Done on a Chromecast so far: menu, Play on defaults, hopping,
  Back along the route and Back at the start. Not yet done on a TV: choosing a finish, the timed clock, dead ends,
  Seerr failures and the completion screen of this flow.

## Example route (to be confirmed)

The mockup shows Alien, Blade Runner, Blade Runner 2049, Arrival. Those titles and links are illustrative and have not
been checked against real TMDB recommendations. The real-data run above is what proves a valid route.

## On the Steam Deck (desktop)

The same Easter egg on a film's page (D-pad or keyboard arrows, each press within 2.5 seconds). The rules are core's, shared
with the TV; the screens are Compose (`desktop/.../FilmLinkModel.kt`, `ui/FilmLinkUi.kt`): the start menu, the game on the
app's own title pages with the HUD, a Hint button and the summary.

- **Random film** is a button with a shuffle icon in the Start and Finish rows. Pressing it runs through random poster
  images on that card and lands on a film; press again to reshuffle. Each card shows its film's name and a short
  description, and the HUD's target panel shows the target's description too, so a film you don't know can still be
  imagined.
- **No route found** (a start and a finish both chosen): the finish is *planted* (`FilmLinkPairs.plant`) as a connection of
  3 to 5 films reachable from the start, 1 to 3 hops out, preferring films the finish's own recommendations include, then
  the nearest in release year. Several films, not one, so a single wrong turn can't make the game unwinnable. The plants
  live only in the game's own graph copy (`CachedFilmGraph.plant`), never in Seerr or TMDB, and are cleared when the game
  ends (kept for "Same pair, new try"). **Hint** names the next film on the shortest route (`FilmLinkPairs.route`).
