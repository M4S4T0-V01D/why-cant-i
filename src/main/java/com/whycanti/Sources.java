package com.whycanti;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

/**
 * Where items come from and what things in the world give, from sources.tsv and objects.tsv (built by
 * scripts/sources.py from the OSRS Wiki's drop tables): rocks, trees, fishing spots, stalls, people to pickpocket,
 * chests, shipwrecks and salvage, with the level each needs.
 */
@Slf4j
final class Sources
{
	/** One way to get an item: "mine" from "Gem rocks" at Mining 40, with a rarity like "9/128". */
	static final class Source
	{
		final String verb;
		final String from;
		final GameData.SkillLevel skill;
		final String rarity;

		Source(String verb, String from, GameData.SkillLevel skill, String rarity)
		{
			this.verb = verb;
			this.from = from;
			this.skill = skill;
			this.rarity = rarity;
		}

		/** "Mine Gem rocks", "Kill Green dragon", "From Bag full of gems". */
		String label()
		{
			return capitalise(verb) + " " + from;
		}
	}

	/** Something in the world you thieve, mine, chop, fish, catch or salvage, and what it gives. */
	static final class Thing
	{
		final String name;
		final Set<String> ids;
		final String verb;
		final GameData.SkillLevel skill;
		/** Item names and rarities, most common first. */
		final List<String[]> loot = new ArrayList<>();

		Thing(String name, Set<String> ids, String verb, GameData.SkillLevel skill)
		{
			this.name = name;
			this.ids = ids;
			this.verb = verb;
			this.skill = skill;
		}

		/** The words in brackets after its name, like the "small net, bait" of a fishing spot, or "". */
		String variant()
		{
			int open = name.indexOf(" (");
			return open < 0 || !name.endsWith(")") ? "" : name.substring(open + 2, name.length() - 1);
		}

		/** The tool it needs, if it's mined or chopped. */
		String tool()
		{
			return "mine".equals(verb) ? "Pickaxe" : "chop".equals(verb) ? "Axe" : null;
		}
	}

	/** The most variants listed for a name shared by many things, like "Chest" or "Fishing spot". */
	static final int VARIANTS = 4;

	private static Map<String, List<Source>> sources;
	/** Monsters left out of each item's list, by item. */
	private static Map<String, Integer> moreMonsters;
	private static Map<String, List<Thing>> byId;
	private static Map<String, List<Thing>> byName;

	private Sources()
	{
	}

	private static String capitalise(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** A name as things are looked up: lower case, without the words in brackets. */
	static String key(String name)
	{
		return name == null ? "" : name.replaceAll("\\s*\\([^)]*\\)$", "").trim().toLowerCase(Locale.ROOT);
	}

	/** Where to get an item, most likely first. */
	static synchronized List<Source> forItem(String itemName)
	{
		load();
		return sources.getOrDefault(Recipes.base(itemName), Collections.emptyList());
	}

	/**
	 * The things an object or NPC could be: those with its ID, or else those with its name that the wiki has no IDs
	 * for. Of many with one name, those whose variant matches its actions (a fishing spot's "Net" and "Bait").
	 */
	static synchronized List<Thing> find(boolean npc, int id, String name, String[] actions)
	{
		load();
		List<Thing> found = byId.get((npc ? "n" : "o") + id);
		if (found != null)
		{
			return found;
		}
		String prefix = npc ? "n" : "o";
		List<Thing> named = byName.getOrDefault(key(name), Collections.emptyList()).stream()
			.filter(t -> t.ids.stream().noneMatch(i -> i.startsWith(prefix)))
			.collect(Collectors.toList());
		List<Thing> exact = named.stream().filter(t -> t.name.equalsIgnoreCase(name)).collect(Collectors.toList());
		if (named.size() > 1 && !exact.isEmpty())
		{
			return exact;
		}
		if (named.size() > 1 && actions != null)
		{
			Set<String> acts = Arrays.stream(actions).filter(a -> a != null)
				.map(a -> a.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
			List<Thing> matching = named.stream()
				.filter(t -> !t.variant().isEmpty() && Arrays.stream(t.variant().split(",\\s*"))
					.allMatch(v -> acts.contains(v.toLowerCase(Locale.ROOT))))
				.collect(Collectors.toList());
			if (!matching.isEmpty())
			{
				named = matching;
			}
		}
		return named;
	}

	/** Things with exactly an item's name that you open or sort, like salvage. */
	static synchronized List<Thing> opened(String itemName)
	{
		load();
		String name = itemName == null ? "" : itemName.toLowerCase(Locale.ROOT);
		return byName.getOrDefault(key(itemName), Collections.emptyList()).stream()
			.filter(t -> t.ids.isEmpty() && t.name.toLowerCase(Locale.ROOT).equals(name))
			.collect(Collectors.toList());
	}

	private static void load()
	{
		if (sources != null)
		{
			return;
		}
		Map<String, List<Source>> items = new HashMap<>();
		Map<String, Integer> more = new HashMap<>();
		for (String[] f : read("sources.tsv", 5))
		{
			if ("kill".equals(f[1]) && f[2].isEmpty())
			{
				try
				{
					more.put(Recipes.base(f[0]), Integer.parseInt(f[4].replace("+", "")));
				}
				catch (NumberFormatException e)
				{
					// not a count
				}
				continue;
			}
			items.computeIfAbsent(Recipes.base(f[0]), k -> new ArrayList<>()).add(new Source(f[1], f[2], skill(f[3]), f[4]));
		}
		Map<String, List<Thing>> ids = new HashMap<>();
		Map<String, List<Thing>> names = new HashMap<>();
		for (String[] f : read("objects.tsv", 5))
		{
			Set<String> thingIds = f[1].isEmpty() ? Collections.emptySet() : new java.util.HashSet<>(Arrays.asList(f[1].split(",")));
			Thing thing = new Thing(f[0], thingIds, f[2], skill(f[3]));
			for (String l : f[4].split("\\|"))
			{
				int tilde = l.lastIndexOf('~');
				if (tilde > 0)
				{
					thing.loot.add(new String[]{l.substring(0, tilde), l.substring(tilde + 1)});
				}
			}
			for (String id : thingIds)
			{
				ids.computeIfAbsent(id, k -> new ArrayList<>()).add(thing);
			}
			names.computeIfAbsent(key(f[0]), k -> new ArrayList<>()).add(thing);
		}
		sources = items;
		moreMonsters = more;
		byId = ids;
		byName = names;
	}

	private static List<String[]> read(String resource, int fields)
	{
		List<String[]> out = new ArrayList<>();
		try (InputStream in = Sources.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				return out;
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			for (String line; (line = reader.readLine()) != null; )
			{
				String[] f = line.split("\t", -1);
				if (f.length >= fields)
				{
					out.add(f);
				}
			}
		}
		catch (IOException e)
		{
			log.warn("Couldn't read {}", resource, e);
		}
		return out;
	}

	/** "Mining:40" as a level, or null for none or a skill this client doesn't have. */
	static GameData.SkillLevel skill(String s)
	{
		int colon = s.indexOf(':');
		if (colon <= 0)
		{
			return null;
		}
		try
		{
			return new GameData.SkillLevel(Skill.valueOf(s.substring(0, colon).toUpperCase(Locale.ROOT)),
				Integer.parseInt(s.substring(colon + 1)));
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	private static String note(GameData.SkillLevel skill, String rarity)
	{
		String level = skill == null ? null : skill.getSkill().getName() + " " + skill.getLevel();
		String chance = rarity.isEmpty() ? null : rarity;
		return level == null ? chance : chance == null ? level : level + ", " + chance;
	}

	/** "Get it" with each place an item comes from, ticked if the player has the level. */
	static Requirement getIt(String itemName, java.util.function.ToIntFunction<Skill> level)
	{
		List<Source> list = forItem(itemName);
		Requirement group = new Requirement(Requirement.Kind.GROUP, "Get it", Requirement.Status.MET, null, -1);
		for (Source s : list)
		{
			boolean met = s.skill == null || level.applyAsInt(s.skill.getSkill()) >= s.skill.getLevel();
			group.getChildren().add(new Requirement(Requirement.Kind.SKILL, s.label(),
				met ? Requirement.Status.MET : Requirement.Status.NOT_MET, note(s.skill, s.rarity), -1));
		}
		Integer more = moreMonsters.get(Recipes.base(itemName));
		if (more != null)
		{
			group.getChildren().add(new Requirement(Requirement.Kind.SKILL, "And " + more + " more monsters",
				Requirement.Status.MET, null, -1));
		}
		return group;
	}

	/** "Mine it" with its level and tool ticked off, then what it gives. */
	static Requirement explain(Thing thing, boolean many, RequirementTree tree, ClueExplainer.Items items, IntFunction<String> names)
	{
		List<Requirement> children = new ArrayList<>();
		if (thing.skill != null)
		{
			BlockParser.Block block = new BlockParser.Block(Collections.emptyList(), Collections.singletonList(thing.skill), 0, 0);
			children.addAll(tree.explain(null, block, null, Collections.emptyList()).getRequirements());
		}
		String tool = thing.tool();
		if (tool != null)
		{
			children.add(ClueExplainer.item(Requirement.Kind.ITEM, tool, new Recipes.Named(tool, 1, true, names), false, items));
		}
		if (!thing.loot.isEmpty())
		{
			Requirement gives = new Requirement(Requirement.Kind.GROUP, "Gives", Requirement.Status.MET, null, -1);
			for (String[] l : thing.loot)
			{
				gives.getChildren().add(new Requirement(Requirement.Kind.ITEM, l[0], Requirement.Status.MET, l[1], -1));
			}
			children.add(gives);
		}
		String label = "from".equals(thing.verb) ? "Loot it" : capitalise(thing.verb) + " it";
		if (many)
		{
			label += " (" + thing.name + ")";
		}
		Requirement group = new Requirement(Requirement.Kind.GROUP, label,
			children.stream().anyMatch(Requirement::anyUnmet) ? Requirement.Status.NOT_MET : Requirement.Status.MET, null, -1);
		group.getChildren().addAll(children);
		return group;
	}
}
