# Gold eligibility: sources and thresholds (CIN-32)

Decision note for the shared-core `GoldPolicy` (`core/.../data/movies/GoldEligibility.kt`). It decides whether a title is
"Gold" (critically acclaimed) from ratings it is *given*. It does no network work and has no UI; CIN-33 wires it in.

## Which ratings Cinema can reach today

| Source | Where it is already read | Score | Rating count | Status |
|---|---|---|---|---|
| IMDb | `Catalog.kt` (JustWatch `scoring { imdbScore }`), Cinemeta `imdbRating` (`Stremio.kt`, `Channels.kt`) | yes | **not read** | Score only. A count must be added to the provider call before IMDb can qualify anything (a review gate: check the provider's terms first). |
| Letterboxd | `Letterboxd.kt` (`LbFilmPage.average`, `.ratings`) | yes (0..5) | yes | Read from the public film page, i.e. page scraping, not an API. **Review gate: confirm this is acceptable before Gold depends on it.** Not assumed to be a supported aggregate source. |
| TMDB | `Picks.kt` (Seerr `voteAverage`), Jellyfin `communityRating` | yes | Seerr's discover result carries `voteCount` but `Picks.kt` does not read it yet | Usable as a labelled fallback once the count is read. |
| Critics' consensus | none | n/a | n/a | No independent source is wired. The exception route (below) stays closed until one is. |

Nothing here is paid. No new provider, credential or scraping is added by this change.

## Decision

Evidence is `RatingEvidence(source, score, count, updatedAt)`; a source is never relabelled (a TMDB average is `TMDB`).

1. **Fail closed.** No rating, no count, no date, a stale rating (older than 365 days), a non-finite or out-of-range score,
   or a source unsupported for that kind means *not eligible*, with the reason.
2. **Baseline.** IMDb >= 8.0/10 or Letterboxd >= 4.0/5, with enough ratings. Movies and series have their own tables.
3. **Confidence, not a looser bar.** A minimum count is required at all. Below the "full confidence" count the score must clear
   the threshold by a margin that shrinks linearly to zero at full confidence.

   | Kind / source | Threshold | Min ratings | Full confidence | Max margin |
   |---|---|---|---|---|
   | Movie IMDb | 8.0 | 25,000 | 100,000 | +0.5 |
   | Movie Letterboxd | 4.0 | 10,000 | 50,000 | +0.25 |
   | Movie TMDB (fallback) | 8.0 | 5,000 | 20,000 | +0.5 |
   | Series IMDb | 8.0 | 10,000 | 50,000 | +0.5 |
   | Series Letterboxd | 4.0 | 5,000 | 25,000 | +0.25 |
   | Series TMDB (fallback) | 8.0 | 1,500 | 8,000 | +0.5 |

   These numbers are a judgement, chosen to be explainable, and are all configurable (`GoldThresholds`). They have not been
   calibrated against Cinema's real catalogue; do that once counts are available.
4. **TMDB is only the fallback**, used when neither IMDb nor Letterboxd evidence is present at all. A strong TMDB score never
   rescues a weak headline score.
5. **Contradiction.** Sources normalised to 0..10 that differ by more than 1.5 are not eligible.
6. **Hidden-gem exception (bounded).** A score down to 7.5 (Letterboxd 3.75) with at least 5,000 ratings (movies IMDb; other
   minimums in the code) qualifies only with an independent, verified critical consensus of >= 0.85 (0..1). The reason names
   the consensus source. There is no unconditional lower bar.

## Calibration examples (all in `GoldEligibilityTest`)

- IMDb 9.5 from 300 ratings: **not Gold** (tiny sample).
- IMDb 8.2 from 200,000 ratings: **Gold**.
- IMDb 8.2 from 30,000 ratings: not Gold (needs about 8.47 at that size); 8.6 is.
- IMDb 8.0 from 100,000 ratings: Gold; 7.99: not.
- Letterboxd 4.0 from 50,000: Gold; 3.99: not.
- IMDb 7.7 from 8,000 ratings: not Gold alone; Gold with a verified 0.9 consensus; not with 0.6 or an unverified one.

## Before CIN-33 uses this

1. Read rating counts from the existing providers (above), without adding a paid or unapproved provider.
2. Stamp each rating with the time it was fetched (`updatedAt`).
3. Get Daniel's decision on the Letterboxd scraping caveat and on whether a critics' consensus source is wanted.
