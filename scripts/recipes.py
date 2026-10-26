#!/usr/bin/env python3
"""Builds src/main/resources/com/whycanti/recipes.tsv from the OSRS Wiki's recipe data.

Usage: python3 -I scripts/recipes.py [--calc DIR] [downloaded json files...]
Without files it downloads the data. It adds the skill calculators' data (Module:Skill calc/<Skill>) for growing,
gathering and other uses, read from DIR/<Skill>.lua when given. Each line of the output is one way to get or use an item:
output, skills ("Smithing:99,..."), materials ("5*Runite bar|..."), tools ("Hammer|..."), facility, and how: empty
for making it, otherwise a verb like "grow" or "chop", or "cast <spell>". Uses like burning logs have no output.
"""
import argparse
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
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/com/whycanti/recipes.tsv"
SKILLS = {"Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting",
          "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing", "Mining", "Herblore", "Agility", "Thieving",
          "Slayer", "Farming", "Runecraft", "Hunter", "Construction", "Sailing"}


def download():
    rows, offset = [], 0
    while True:
        query = "bucket('recipe').select('page_name','production_json').limit(5000).offset(%d).run()" % offset
        url = API + "?" + urllib.parse.urlencode({"action": "bucket", "format": "json", "query": query})
        request = urllib.request.Request(url, headers={"User-Agent": "why-cant-i-recipes/1.0"})
        page = json.load(urllib.request.urlopen(request)).get("bucket", [])
        rows += page
        if len(page) < 5000:
            return rows
        offset += 5000
        time.sleep(1)


CALC = ["Farming", "Woodcutting", "Mining", "Fishing", "Hunter", "Firemaking", "Prayer", "Magic", "Runecraft",
        "Cooking", "Crafting", "Fletching", "Herblore", "Smithing", "Construction"]
PATCHES = {"Allotment", "Bush", "Flower", "Fruit tree", "Herb", "Hops", "Tree"}
FISHING_TOOLS = {"Small net": "Small fishing net", "Big net": "Big fishing net", "Harpoon": "Harpoon",
                 "Fly": "Fly fishing rod", "Bait": "Fishing rod", "Lantern harpoon": "Harpoon"}
HUNTER_TOOLS = {"Bird snare": "Bird snare", "Box trap": "Box trap", "Butterfly net": "Butterfly net",
                "Net trapping": "Small fishing net|Rope"}


def calc_module(skill, folder):
    if folder:
        return (Path(folder) / ("%s.lua" % skill)).read_text(encoding="utf-8")
    url = "https://oldschool.runescape.wiki/index.php?" + urllib.parse.urlencode(
        {"title": "Module:Skill calc/%s" % skill, "action": "raw"})
    time.sleep(1)
    request = urllib.request.Request(url, headers={"User-Agent": "why-cant-i-recipes/1.0"})
    return urllib.request.urlopen(request).read().decode("utf-8")


def calc_rows(skill, entries):
    """(output, skill, level, materials, tools, facility, how) for each calculator entry worth showing."""
    for e in entries:
        if not isinstance(e, dict) or not isinstance(e.get("name"), str) or not isinstance(e.get("level"), int):
            continue
        name, kind = clean(e["name"]), str(e.get("type") or "")
        output = clean(e.get("outputItem") or "") or re.sub(r"\s*\(%s\)$" % skill, "", name)
        materials = []
        for m in e.get("materials") or []:
            if isinstance(m, dict) and clean(m.get("name")):
                q = m.get("quantity")
                materials.append("%d*%s" % (q if isinstance(q, int) and q > 0 else 1, clean(m["name"])))
        tools, facility, how = "", "", ""
        if skill == "Farming":
            output = "Grimy " + name.lower() if kind == "Herb" else output
            facility, how = (kind.lower() + " patch" if kind in PATCHES else ""), "grow"
        elif skill == "Woodcutting":
            tools, how = "Axe", "chop"
        elif skill == "Mining":
            tools, how = "Pickaxe", "mine"
        elif skill == "Fishing":
            tools, how = FISHING_TOOLS.get(kind, ""), "catch"
        elif skill == "Hunter":
            tools, how = HUNTER_TOOLS.get(kind, ""), "catch"
        elif skill == "Firemaking":
            if kind == "Pyre":
                continue  # pyre logs are made with sacred oil, which the recipe data has
            output, how = "", "burn"
        elif skill == "Prayer":
            if kind not in ("Regular", "Ashes"):
                continue  # offerings are Magic spells; the rest are minigames
            output, how = "", "scatter" if kind == "Ashes" else "bury"
        elif skill == "Magic":
            output, how = clean(e.get("outputItem") or ""), "cast " + name
        if materials or tools or how not in ("", "burn", "bury", "scatter"):
            yield output, skill, e["level"], materials, tools, facility, how


def clean(text):
    """Wiki links and markup to plain text: [[A|B]] is B."""
    text = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", str(text or ""))
    return re.sub(r"<[^>]*>|'''?", "", text).replace("\t", " ").strip()


def names(field):
    """A tools or facilities field as a list, without the "none" placeholders."""
    parts = field if isinstance(field, list) else re.split(r",\s*", clean(field))
    return [p for p in (clean(p) for p in parts) if p and p.lower() not in ("none", "n/a")]


def main():
    args = argparse.ArgumentParser()
    args.add_argument("--calc", help="folder of downloaded Module:Skill calc/<Skill> pages, as <Skill>.lua")
    args.add_argument("files", nargs="*", help="downloaded bucket json")
    args = args.parse_args()
    rows = [r for f in args.files for r in json.load(open(f)).get("bucket", [])] if args.files else download()
    lines = set()
    for row in rows:
        p = json.loads(row["production_json"])
        output = p.get("output")
        if not isinstance(output, dict) or not output.get("name") or not p.get("materials"):
            continue
        skills = []
        for s in p.get("skills") or []:
            name = clean(s.get("name")).capitalize()
            level = re.match(r"\d+", str(s.get("level") or ""))
            if name in SKILLS and level and int(level.group()) > 1:
                skills.append("%s:%s" % (name, level.group()))
        materials = []
        for m in p["materials"]:
            quantity = re.match(r"\d+", str(m.get("quantity") or "1"))
            if clean(m.get("name")):
                materials.append("%s*%s" % (quantity.group() if quantity else "1", clean(m["name"])))
        facility = names(p.get("facilities"))
        lines.add("\t".join([clean(output["name"]), ",".join(skills), "|".join(materials), "|".join(names(p.get("tools"))),
                             facility[0] if facility else "", ""]))
    made = {line.split("\t")[0].lower() for line in lines}
    for skill in CALC:
        for output, _, level, materials, tools, facility, how in calc_rows(skill, luadata.parse(calc_module(skill, args.calc))):
            if not how and output.lower() in made:
                continue  # the recipe data already has it, with tools and facilities
            lines.add("\t".join([output, "%s:%d" % (skill, level) if level > 1 else "", "|".join(materials), tools,
                                  facility, how]))
    OUT.write_text("".join(line + "\n" for line in sorted(lines)), encoding="utf-8")
    print("%d recipes to %s" % (len(lines), OUT))


if __name__ == "__main__":
    main()
