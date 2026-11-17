# Classic TV schedule data

`build_dataset.py` builds `core/src/main/resources/cinema/schedules/classic-schedules.json`, which Cinema uses to know
which shows ran together on classic network television.

```bash
pip install beautifulsoup4
python scripts/schedules/build_dataset.py            # fetches and caches the pages, then writes the dataset
python scripts/schedules/build_dataset.py --offline  # only uses pages already cached
```

- **Source:** English Wikipedia's season-by-season network schedule pages: US networks from 1946 to the present, and
  Canadian networks 2003 to 2023. Pages are fetched through the MediaWiki API, one at a time, with a short pause, and kept
  in `scripts/schedules/.cache/` (ignored by git) so a rerun does not fetch them again.
- **What it records:** for every network, night and season, the shows that ran directly after one another and the shows that
  ran on the same night. Each pair counts once per season.
- **Licence:** the data is CC BY-SA 4.0 (it comes from Wikipedia text); see
  `core/src/main/resources/cinema/schedules/LICENSE.txt`.
- **Not covered:** the UK, Australia and the rest of Europe. Wikipedia has no schedule pages for them. BBC Genome holds
  the Radio Times listings, but its terms do not clearly allow collecting them in bulk, so nothing is taken from it.
- `normalise` in the script and `ScheduleKnowledge.normalise` in core must give the same result for any name; a unit test
  holds the expected values.
