# Why Can't I?

A RuneLite plugin that explains why the game won't let you do something. When you're blocked, it lists every
requirement and marks which ones you have.

## Features

- **Game messages and dialogue:** "You need a Mining level of 15", quest locks and the like are turned into a full list
  of quests, subquests, skill levels and quest points, shown in a popup and the side panel.
- **Items you can't wear:** every level the item needs.
- **Items you can make, grow or gather:** the levels, ingredients, tools and station needed to smith, fletch, craft,
  brew or cook it, grow it from a seed, or chop, mine, fish or hunt it, with each ingredient and tool marked as carried,
  banked or missing.
- **What an item is used for:** what a seed grows, what a herb, log, bar or rune makes, and the spells, tools and
  burning or burying that use it, each ticked if you have the levels.
- **Where to get an item:** the rocks, trees, fishing spots, stalls, people to pickpocket, chests, shipwrecks,
  monsters and reward caskets it comes from, with the level each needs and how often it drops ("Mine Gem rocks, Mining
  40, 9/128").
- **Objects and NPCs:** right-click a stall, chest, rock, tree, fishing spot, person to pickpocket, shipwreck, agility
  shortcut, course obstacle or guild door or ladder for what it needs, the tool, and what it gives.
- **Clue scrolls:** the items a step needs (emote clues, skill challenges, Falo the Bard, places that need gear) and
  which ones you have. The Clue Scroll plugin is not required.
- **Right-click "Why can't I?":** on every item, pins its requirements, or a clue scroll's current step. Turn on
  "Only when needed" to show it just on clue scrolls, and on items, objects and NPCs you lack the levels for.

## Data

Recipe data (levels, materials, tools and facilities), drop tables, object and NPC IDs, and the skill calculators' data
come from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/), licensed under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/). They are bundled in
`src/main/resources/com/whycanti/` as `recipes.tsv` (rebuilt with `python3 -I scripts/recipes.py`) and `sources.tsv`
and `objects.tsv` (rebuilt with `python3 -I scripts/sources.py`). Agility shortcuts come from RuneLite's own list.

## License

BSD 2-Clause, © 2026 M4S4T0-V01D. See [LICENSE](LICENSE).
