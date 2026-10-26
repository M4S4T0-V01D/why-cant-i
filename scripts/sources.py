#!/usr/bin/env python3
"""Builds sources.tsv and objects.tsv in src/main/resources/com/whycanti from the OSRS Wiki's drop tables.

Usage: python3 -I scripts/sources.py [--drops FILE] [--ids FILE...] [--calc DIR]
Without files it downloads the data: the 'dropsline' bucket, and the 'infobox_scenery' and 'infobox_npc' buckets for the
game's object and NPC IDs. DIR/Thieving.lua (Module:Skill calc/Thieving) tells pickpockets from stalls and chests.

sources.tsv has one line per way to get an item: item, verb ("mine", "kill", "from"), source, skill ("Mining:40" or
empty) and rarity. A "kill" line with no source counts the monsters left out.
objects.tsv has one line per thing you can thieve, mine, chop, fish, catch or salvage: name, IDs ("o11380,n1530"),
verb, skill, and its loot as "Item~rarity|...", most common first.
"""
import argparse
import collections
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))  # -I leaves the script's folder off the path
import luadata  # noqa: E402

API = "https://oldschool.runescape.wiki/api.php"
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/com/whycanti"
# Farming is in recipes.tsv as growing; the rest have no level or aren't something you click.
SKILLED = {"thieving", "mining", "woodcutting", "fishing", "hunter", "sailing", "construction", "smithing",
           "firemaking", "crafting", "agility"}
VERBS = {"mining": "mine", "woodcutting": "chop", "fishing": "fish at", "hunter": "catch"}
TAGS = re.compile(r"\s*\((?:scenery|NPC|Hunter|Thieving|Farming|Mining|Woodcutting|Fishing|object)\)$")
SKILLING_SOURCES, MONSTERS, REWARDS, LOOT = 6, 3, 2, 8


def bucket(name, fields):
    rows, offset = [], 0
    while True:
        query = "bucket('%s').select(%s).limit(5000).offset(%d).run()" % (
            name, ",".join("'%s'" % f for f in fields), offset)
        url = API + "?" + urllib.parse.urlencode({"action": "bucket", "format": "json", "query": query})
        request = urllib.request.Request(url, headers={"User-Agent": "why-cant-i-recipes/1.0"})
        page = json.load(urllib.request.urlopen(request)).get("bucket", [])
        rows += page
        if len(page) < 5000:
            return rows
        offset += 5000
        time.sleep(1)


def chance(rarity):
    """A rarity as a probability, for sorting: "1/128" is 0.0078, "Always" is 1."""
    r = str(rarity or "").replace(",", "").strip().lower()
    m = re.match(r"([\d.]+)\s*/\s*([\d.]+)", r)
    if m and float(m.group(2)) > 0:
        return float(m.group(1)) / float(m.group(2))
    return {"always": 1, "common": 0.1, "uncommon": 0.02, "rare": 0.005, "very rare": 0.001}.get(r, 0)


def level(text):
    levels = [int(n) for n in re.findall(r"\d+", str(text or ""))]
    return min(levels) if levels else 0


def clean(text):
    return re.sub(r"<[^>]*>|\[\[|\]\]", "", str(text or "")).replace("\t", " ").replace("|", "/").strip()


def verb(kind, source, pickpockets, npc):
    lower = source.lower()
    if kind in VERBS:
        return "check" if "birdhouse" in lower else VERBS[kind]
    if kind == "thieving":
        if "stall" in lower:
            return "steal from"
        base = re.sub(r"\s*\([^)]*\)$", "", source)
        return "pickpocket" if base in pickpockets or npc else "search"
    if kind == "sailing":
        return "salvage" if "shipwreck" in lower else "sort" if "salvage" in lower else "do" if "tasks" in lower else "from"
    if kind == "construction":
        return "search"
    return "from"


def main():
    args = argparse.ArgumentParser()
    args.add_argument("--drops", help="downloaded dropsline bucket json")
    args.add_argument("--ids", nargs="*", help="downloaded infobox_scenery and infobox_npc bucket json")
    args.add_argument("--calc", help="folder with Module:Skill calc/Thieving as Thieving.lua")
    args = args.parse_args()
    drops = json.load(open(args.drops))["bucket"] if args.drops else bucket(
        "dropsline", ["page_name", "item_name", "drop_json"])
    if args.ids:
        id_rows = [r for f in args.ids for r in json.load(open(f))["bucket"]]
    else:
        id_rows = bucket("infobox_scenery", ["page_name", "object_id"]) + bucket("infobox_npc", ["page_name", "npc_id"])
    thieving = (Path(args.calc) / "Thieving.lua").read_text(encoding="utf-8") if args.calc else None
    pickpockets = {e["name"] for e in luadata.parse(thieving) if isinstance(e, dict) and e.get("type") == "Pickpocket"} \
        if thieving else set()

    ids = collections.defaultdict(set)
    for r in id_rows:
        for key, prefix in (("object_id", "o"), ("npc_id", "n")):
            for i in r.get(key) or []:
                if re.fullmatch(r"\d+", str(i)):
                    ids[r["page_name"]].add(prefix + str(i))

    sources = collections.defaultdict(dict)  # item -> (verb, source) -> (skill, rarity)
    monsters = collections.defaultdict(dict)  # item -> monster -> rarity
    rewards = collections.defaultdict(dict)
    objects = {}  # page -> [kind, level, {item: rarity}]
    for row in drops:
        d = json.loads(row["drop_json"])
        item, kind = clean(row.get("item_name")), str(d.get("Drop type") or "")
        page = clean(str(d.get("Dropped from") or "").split("#")[0])
        if not item or not page:
            continue
        rarity = clean(d.get("Rarity"))
        if kind == "combat":
            old = monsters[item].get(page)
            if old is None or chance(rarity) > chance(old):
                monsters[item][page] = rarity
        elif kind == "reward":
            rewards[item].setdefault(page, rarity)
        elif kind in SKILLED:
            lvl = level(d.get("Drop level"))
            entry = objects.setdefault(page, [kind, lvl, {}])
            entry[1] = min(entry[1], lvl) if entry[1] and lvl else entry[1] or lvl
            if chance(rarity) > chance(entry[2].get(item)):
                entry[2][item] = rarity
            npc = any(i.startswith("n") for i in ids.get(page, ()))
            skill = "%s:%d" % (kind.capitalize(), lvl) if lvl > 1 else ""
            key = (verb(kind, page, pickpockets, npc), TAGS.sub("", page))
            if key not in sources[item] or level(sources[item][key][0]) > lvl:
                sources[item][key] = (skill, rarity)

    lines = []
    for item in sorted(set(sources) | set(monsters) | set(rewards)):
        skilled = sorted(sources[item].items(), key=lambda s: (-chance(s[1][1]), level(s[1][0])))[:SKILLING_SOURCES]
        for (v, source), (skill, rarity) in skilled:
            lines.append([item, v, source, skill, rarity])
        killed = sorted(monsters[item].items(), key=lambda m: -chance(m[1]))
        for monster, rarity in killed[:MONSTERS]:
            lines.append([item, "kill", monster, "", rarity])
        if len(killed) > MONSTERS:
            lines.append([item, "kill", "", "", "+%d" % (len(killed) - MONSTERS)])
        for reward, rarity in sorted(rewards[item].items(), key=lambda m: -chance(m[1]))[:REWARDS]:
            lines.append([item, "from", reward, "", rarity])
    (OUT / "sources.tsv").write_text("".join("\t".join(l) + "\n" for l in lines), encoding="utf-8")

    out = []
    for page, (kind, lvl, loot) in sorted(objects.items()):
        npc = any(i.startswith("n") for i in ids.get(page, ()))
        best = sorted(loot.items(), key=lambda l: (-chance(l[1]), l[0]))[:LOOT]
        out.append([TAGS.sub("", page), ",".join(sorted(ids.get(page, ()))), verb(kind, page, pickpockets, npc),
                    "%s:%d" % (kind.capitalize(), lvl) if lvl > 1 else "", "|".join("%s~%s" % l for l in best)])
    (OUT / "objects.tsv").write_text("".join("\t".join(l) + "\n" for l in out), encoding="utf-8")
    print("%d item sources, %d objects" % (len(lines), len(out)))


if __name__ == "__main__":
    main()
