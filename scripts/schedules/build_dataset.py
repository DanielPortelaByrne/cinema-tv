#!/usr/bin/env python3
"""
Builds Cinema's classic TV schedule dataset from Wikipedia's season-by-season network schedule pages
(US networks from 1946, Canadian networks from 2003).

    pip install beautifulsoup4
    python scripts/schedules/build_dataset.py            # fetches (cached in scripts/schedules/.cache) and writes the dataset
    python scripts/schedules/build_dataset.py --offline  # only uses what is already cached

Each page has a table for every night of the week: a row per network and period of the season (fall, winter...), a
column per half hour, and cells that span columns (a long show) and rows (a show that stayed all season). The tables are
expanded into a full grid, and for each network, night and season we record
  - which shows ran directly after one another (adjacent), and
  - which shows ran on the same night of the same network (same night).
Each pair is counted once per season however many periods repeated it, so a count is "seasons it happened in".

The text of Wikipedia is CC BY-SA 4.0, so the output is too (see core/src/main/resources/cinema/schedules/LICENSE.txt).
The name matching in `normalise` must stay the same as ScheduleKnowledge.normalise in core.
"""
import argparse
import datetime
import json
import os
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request

from bs4 import BeautifulSoup

API = "https://en.wikipedia.org/w/api.php"
# Wikipedia asks API clients to say what they are. No personal details.
USER_AGENT = "CinemaScheduleBuilder/1.0 (open-source dataset build; https://github.com/DanielPortelaByrne/cinema-tv)"
HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, ".cache")
OUT = os.path.join(HERE, "..", "..", "core", "src", "main", "resources", "cinema", "schedules", "classic-schedules.json")

DASH = "–"
SOURCES = [("us", "United States", 1946, 2024), ("ca", "Canadian", 2003, 2023)]
TIME = re.compile(r"\d{1,2}(:\d{2})?\s*(a|p)\.?m", re.I)
GENERIC = re.compile(r"^(local|sign[- ]?off|test pattern|not programmed|no programming|network|programming|to be announced|tba|—|-|–)\b", re.I)


def normalise(name):
    """Lower case, no accents, no (brackets), "&" as "and", letters and digits only, no leading "the"."""
    s = unicodedata.normalize("NFKD", name)
    s = "".join(ch for ch in s if not unicodedata.combining(ch))
    s = re.sub(r"\([^)]*\)|\[[^\]]*\]", " ", s)
    s = s.lower().replace("&", " and ")
    s = re.sub(r"[^a-z0-9]+", " ", s).strip()
    return re.sub(r"^the ", "", s)


def season_title(start, label):
    end = start + 1
    tail = str(end) if end % 100 == 0 else "%02d" % (end % 100)
    if start == 1999:
        tail = "2000"
    return "%d%s%s %s network television schedule" % (start, DASH, tail, label)


def fetch(title, offline):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, re.sub(r"[^A-Za-z0-9]+", "_", title) + ".html")
    if os.path.exists(path):
        return open(path, encoding="utf-8").read()
    if offline:
        return None
    params = {"action": "parse", "page": title, "prop": "text", "format": "json", "redirects": 1, "formatversion": 2}
    req = urllib.request.Request(API + "?" + urllib.parse.urlencode(params), headers={"User-Agent": USER_AGENT})
    try:
        data = json.load(urllib.request.urlopen(req, timeout=60))
    except Exception as e:  # a season with no page is normal (not every year exists for every country)
        print("  skip %s: %s" % (title, e), file=sys.stderr)
        return None
    time.sleep(0.4)
    if "parse" not in data:
        return None
    html = data["parse"]["text"]
    open(path, "w", encoding="utf-8").write(html)
    return html


class Cell:
    __slots__ = ("el",)

    def __init__(self, el):
        self.el = el


def expand(table):
    """The table as rows of Cells, with rowspans and colspans repeated into every position they cover."""
    rows, pending = [], {}
    for tr in table.find_all("tr"):
        row, col = [], 0

        def fill_pending():
            nonlocal col
            while col in pending:
                cell, left = pending[col]
                row.append(cell)
                if left <= 1:
                    del pending[col]
                else:
                    pending[col] = (cell, left - 1)
                col += 1

        for el in tr.find_all(["td", "th"], recursive=False):
            fill_pending()
            cell = Cell(el)
            span = number(el.get("colspan"))
            down = number(el.get("rowspan"))
            for _ in range(span):
                row.append(cell)
                if down > 1:
                    pending[col] = (cell, down - 1)
                col += 1
        fill_pending()
        rows.append(row)
    return rows


def number(value):
    """A colspan or rowspan: the digits in it (some pages have stray characters), at least 1."""
    m = re.search(r"\d+", value or "")
    return max(1, int(m.group())) if m else 1


def clean(text):
    text = re.sub(r"\[[^\]]*\]", "", text)
    text = re.sub(r"\(\s*[\d/.,#= ]+\)", "", text)   # a ranking and rating, "(27/14.7)"
    text = text.replace("†", "").replace("‡", "").replace(" ", " ")
    return re.sub(r"\s+", " ", text).strip(" *†‡-")


def shows_in(cell):
    """The shows named in a cell: one per line, taking the link's text where there is one."""
    el = cell.el
    for sup in el.find_all("sup"):
        sup.decompose()
    parts, cur = [], []
    for child in el.children:
        if getattr(child, "name", None) == "br":
            parts.append(cur); cur = []
        else:
            cur.append(child)
    parts.append(cur)
    found = []
    for part in parts:
        holder = BeautifulSoup("<span></span>", "html.parser").span
        for p in part:
            holder.append(p if not isinstance(p, str) else BeautifulSoup(p, "html.parser"))
        link = holder.find("a")
        name = clean(link.get_text(" ", strip=True)) if link else clean(holder.get_text(" ", strip=True))
        if name and not GENERIC.match(name) and not TIME.fullmatch(name) and len(name) <= 70:
            found.append(name)
    return found


def day_tables(soup):
    for table in soup.find_all("table", class_="wikitable"):
        first = table.find("tr")
        if not first:
            continue
        heads = [h.get_text(" ", strip=True) for h in first.find_all(["th", "td"], recursive=False)]
        if heads and heads[0].lower().startswith("network") and sum(1 for h in heads if TIME.search(h)) >= 2:
            yield table


def season_lineups(html):
    """(network, [shows in column order]) for every period row of every night's table on a page."""
    soup = BeautifulSoup(html, "html.parser")
    for table in day_tables(soup):
        grid = expand(table)
        if len(grid) < 2:
            continue
        t0 = next((i for i, c in enumerate(grid[0]) if TIME.search(c.el.get_text(" ", strip=True))), None)
        if not t0:
            continue
        network = None
        for r, row in enumerate(grid[1:], start=1):
            if len(row) <= t0:
                continue
            head = clean(row[0].el.get_text(" ", strip=True))
            if row[0].el.name == "th" and head:
                network = head
            if not network:
                continue
            seen, lineup = set(), []
            for cell in row[t0:]:
                if id(cell) in seen:
                    continue
                seen.add(id(cell))
                lineup.extend(shows_in(cell))
            # a show repeated by a break in its own cell is still one show in a row
            out = [s for i, s in enumerate(lineup) if i == 0 or s != lineup[i - 1]]
            if len(out) >= 2:
                yield network, out


def build(offline):
    names = {}                    # normalised -> display name
    stats = {}                    # normalised -> [first, last, seasons]
    adjacent, night = {}, {}
    pages = 0
    for code, label, first, last in SOURCES:
        for start in range(first, last + 1):
            title = season_title(start, label)
            html = fetch(title, offline)
            if not html:
                continue
            pages += 1
            season_adj, season_night = set(), set()
            season_shows = set()
            for network, lineup in season_lineups(html):
                keys = []
                for name in lineup:
                    k = normalise(name)
                    if not k:
                        continue
                    names.setdefault(k, name)
                    keys.append(k)
                    season_shows.add(k)
                for a, b in zip(keys, keys[1:]):
                    if a != b:
                        season_adj.add((network, min(a, b), max(a, b)))
                uniq = sorted(set(keys))
                for i in range(len(uniq)):
                    for j in range(i + 1, len(uniq)):
                        season_night.add((network, uniq[i], uniq[j]))
            for k in season_shows:
                s = stats.setdefault(k, [start, start, 0])
                s[0], s[1], s[2] = min(s[0], start), max(s[1], start), s[2] + 1
            for _, a, b in {(0, a, b) for _, a, b in season_adj}:
                adjacent[(a, b)] = adjacent.get((a, b), 0) + 1
            for _, a, b in {(0, a, b) for _, a, b in season_night}:
                night[(a, b)] = night.get((a, b), 0) + 1
            print("%s: %d shows" % (title, len(season_shows)), file=sys.stderr)
    return pages, names, stats, adjacent, night


def write(pages, names, stats, adjacent, night):
    used = sorted({k for pair in list(adjacent) + list(night) for k in pair})
    index = {k: i for i, k in enumerate(used)}
    doc = {
        "v": 1,
        "source": "Wikipedia season-by-season network television schedule pages (US 1946 onward, Canada 2003-2023), CC BY-SA 4.0",
        "generated": datetime.date.today().isoformat(),
        "seasons": pages,
        # [name, first season starting year, last, number of seasons seen]
        "shows": [[names[k]] + stats[k] for k in used],
        # [show, show, seasons the pair ran directly after one another]
        "adjacent": sorted([index[a], index[b], n] for (a, b), n in adjacent.items()),
        # [show, show, seasons the pair ran on the same night of the same network]
        "sameNight": sorted([index[a], index[b], n] for (a, b), n in night.items()),
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
    size = os.path.getsize(OUT)
    print("wrote %s: %d seasons, %d shows, %d adjacent pairs, %d same-night pairs, %.0f KB"
          % (os.path.normpath(OUT), pages, len(used), len(adjacent), len(night), size / 1024), file=sys.stderr)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--offline", action="store_true", help="use only pages already in the cache")
    args = ap.parse_args()
    write(*build(args.offline))
