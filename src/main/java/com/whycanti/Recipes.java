package com.whycanti;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.Skill;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;

/**
 * How items are made, grown or gathered and what they're used for: levels, materials, tools and where, from recipes.tsv
 * (built by scripts/recipes.py from the OSRS Wiki). Items are matched by name without the dose, so a Super attack(4) is
 * made like a Super attack(3).
 */
@Slf4j
final class Recipes
{
	static final class Recipe
	{
		final String output;
		final List<GameData.SkillLevel> skills = new ArrayList<>();
		final List<Material> materials = new ArrayList<>();
		final List<String> tools = new ArrayList<>();
		final String facility;
		/** How it's done: empty for making it, otherwise a verb like "grow" or "chop", or "cast" and a spell. */
		final String how;

		Recipe(String output, String facility, String how)
		{
			this.output = output;
			this.facility = facility;
			this.how = how;
		}

		/** The heading over what it needs: "Make it", "Grow it", "Cast Superglass Make". */
		String heading()
		{
			return how.isEmpty() ? "Make it" : how.indexOf(' ') > 0 ? capitalise(how) : capitalise(how) + " it";
		}

		/** What it does with an item that goes into it: "Make Rune platebody", "Burn it", "Cast Wind Strike". */
		String use()
		{
			if (how.indexOf(' ') > 0)
			{
				return capitalise(how);
			}
			return (how.isEmpty() ? "Make" : capitalise(how)) + " " + (output.isEmpty() ? "it" : output);
		}

		/** Its levels as "Smithing 99, Mining 50", or null. */
		String levels()
		{
			return skills.isEmpty() ? null : skills.stream().map(s -> s.getSkill().getName() + " " + s.getLevel())
				.collect(Collectors.joining(", "));
		}

		/** How many of its levels the player lacks. */
		int unmet(ToIntFunction<Skill> level)
		{
			return (int) skills.stream().filter(s -> level.applyAsInt(s.getSkill()) < s.getLevel()).count();
		}

		int highest()
		{
			return skills.stream().mapToInt(GameData.SkillLevel::getLevel).max().orElse(0);
		}
	}

	static final class Material
	{
		final int quantity;
		final String name;

		Material(int quantity, String name)
		{
			this.quantity = quantity;
			this.name = name;
		}
	}

	/** Items with a name, at least a quantity of them; a tool also matches its kinds, so "Axe" matches a Rune axe. */
	static final class Named implements ItemRequirement
	{
		private final String name;
		private final int quantity;
		private final boolean tool;
		private final IntFunction<String> names;

		Named(String name, int quantity, boolean tool, IntFunction<String> names)
		{
			this.name = base(name);
			this.quantity = quantity;
			this.tool = tool;
			this.names = names;
		}

		@Override
		public boolean fulfilledBy(int itemId)
		{
			if (itemId <= 0)
			{
				return false;
			}
			String item = base(names.apply(itemId));
			return item.equals(name) || (tool && item.endsWith(" " + name));
		}

		@Override
		public boolean fulfilledBy(Item[] items)
		{
			long count = 0;
			for (Item item : items)
			{
				if (item != null && fulfilledBy(item.getId()))
				{
					count += item.getQuantity();
				}
			}
			return count >= quantity;
		}

		@Override
		public String getCollectiveName(Client client)
		{
			return name;
		}
	}

	/** The most uses listed under "Used for"; a Hammer has over a thousand. */
	static final int USES = 10;

	private static Map<String, List<Recipe>> recipes;
	/** Recipes by the base names of their materials and tools. */
	private static Map<String, List<Recipe>> uses;

	private Recipes()
	{
	}

	/** A name without its dose and case: "Super attack(4)" is "super attack". */
	static String base(String name)
	{
		return name == null ? "" : name.replaceAll("\\s*\\(\\d\\)$", "").trim().toLowerCase(Locale.ROOT);
	}

	private static String capitalise(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** The ways to make, grow or gather an item, easiest first; empty if there are none. */
	static synchronized List<Recipe> forItem(String itemName)
	{
		load();
		return recipes.getOrDefault(base(itemName), Collections.emptyList());
	}

	/** What an item goes into as a material or tool, easiest first; a Rune axe is used like any "Axe". */
	static synchronized List<Recipe> uses(String itemName)
	{
		load();
		String name = base(itemName);
		List<Recipe> out = new ArrayList<>();
		for (Recipe r : uses.getOrDefault(name, Collections.emptyList()))
		{
			if (r.materials.stream().anyMatch(m -> base(m.name).equals(name)) || r.tools.stream().anyMatch(t -> base(t).equals(name)))
			{
				out.add(r);
			}
		}
		for (int space = name.indexOf(' '); space > 0; space = name.indexOf(' ', space + 1))
		{
			String kind = name.substring(space + 1);
			for (Recipe r : uses.getOrDefault(kind, Collections.emptyList()))
			{
				if (!out.contains(r) && r.tools.stream().anyMatch(t -> base(t).equals(kind)))
				{
					out.add(r);
				}
			}
		}
		out.sort(Comparator.comparingInt(Recipe::highest));
		return out;
	}

	private static void load()
	{
		if (recipes != null)
		{
			return;
		}
		Map<String, List<Recipe>> map = new HashMap<>();
		Map<String, List<Recipe>> used = new HashMap<>();
		try (InputStream in = Recipes.class.getResourceAsStream("recipes.tsv"))
		{
			if (in == null)
			{
				recipes = map;
				uses = used;
				return;
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			for (String line; (line = reader.readLine()) != null; )
			{
				Recipe recipe = parse(line);
				if (recipe == null)
				{
					continue;
				}
				if (!recipe.output.isEmpty())
				{
					map.computeIfAbsent(base(recipe.output), k -> new ArrayList<>()).add(recipe);
				}
				for (Material m : recipe.materials)
				{
					index(used, m.name, recipe);
				}
				for (String t : recipe.tools)
				{
					index(used, t, recipe);
				}
			}
		}
		catch (IOException e)
		{
			log.warn("Couldn't read recipes", e);
		}
		map.values().forEach(list -> list.sort(Comparator.comparingInt(Recipe::highest)));
		recipes = map;
		uses = used;
	}

	private static void index(Map<String, List<Recipe>> map, String name, Recipe recipe)
	{
		List<Recipe> list = map.computeIfAbsent(base(name), k -> new ArrayList<>());
		if (list.isEmpty() || list.get(list.size() - 1) != recipe)
		{
			list.add(recipe);
		}
	}

	static Recipe parse(String line)
	{
		String[] f = line.split("\t", -1);
		if (f.length < 5)
		{
			return null;
		}
		Recipe recipe = new Recipe(f[0], f[4].isEmpty() ? null : f[4], f.length > 5 ? f[5] : "");
		for (String s : f[1].split(","))
		{
			int colon = s.indexOf(':');
			if (colon > 0)
			{
				try
				{
					recipe.skills.add(new GameData.SkillLevel(Skill.valueOf(s.substring(0, colon).toUpperCase(Locale.ROOT)),
						Integer.parseInt(s.substring(colon + 1))));
				}
				catch (IllegalArgumentException e)
				{
					// a skill this client doesn't have
				}
			}
		}
		for (String m : f[2].split("\\|"))
		{
			int star = m.indexOf('*');
			if (star > 0)
			{
				recipe.materials.add(new Material(Integer.parseInt(m.substring(0, star)), m.substring(star + 1)));
			}
		}
		for (String t : f[3].split("\\|"))
		{
			if (!t.isEmpty())
			{
				recipe.tools.add(t);
			}
		}
		return recipe;
	}

	/** The recipe needing the fewest levels the player lacks, easiest first among equals. */
	static Recipe best(List<Recipe> recipes, ToIntFunction<Skill> level)
	{
		Recipe best = null;
		for (Recipe r : recipes)
		{
			if (best == null || r.unmet(level) < best.unmet(level))
			{
				best = r;
			}
		}
		return best;
	}

	/** Whether every way to make it needs a level the player lacks. */
	static boolean cantMake(List<Recipe> recipes, ToIntFunction<Skill> level)
	{
		return !recipes.isEmpty() && recipes.stream().allMatch(r -> r.unmet(level) > 0);
	}

	/** The recipes grouped by how they're done, in order: making, growing, chopping and so on. */
	static Map<String, List<Recipe>> byHow(List<Recipe> recipes)
	{
		Map<String, List<Recipe>> map = new LinkedHashMap<>();
		for (Recipe r : recipes)
		{
			map.computeIfAbsent(r.how, k -> new ArrayList<>()).add(r);
		}
		return map;
	}

	/**
	 * "Used for" with what an item goes into, each ticked if the player has its levels. Long lists keep the next few
	 * the player can't do yet and the hardest they can.
	 */
	static Requirement usedFor(List<Recipe> uses, ToIntFunction<Skill> level)
	{
		Map<String, Recipe> unique = new LinkedHashMap<>();
		for (Recipe r : uses)
		{
			unique.putIfAbsent(r.use() + "\t" + r.levels(), r);
		}
		List<Recipe> all = new ArrayList<>(unique.values());
		List<Recipe> shown = all;
		if (all.size() > USES)
		{
			List<Recipe> can = all.stream().filter(r -> r.unmet(level) == 0).collect(Collectors.toList());
			List<Recipe> cant = all.stream().filter(r -> r.unmet(level) > 0).collect(Collectors.toList());
			int next = Math.min(cant.size(), Math.max(USES / 2, USES - can.size()));
			shown = new ArrayList<>(can.subList(Math.max(0, can.size() - (USES - next)), can.size()));
			shown.addAll(cant.subList(0, next));
			shown.sort(Comparator.comparingInt(Recipe::highest));
		}
		Requirement group = new Requirement(Requirement.Kind.GROUP,
			"Used for" + (shown.size() < all.size() ? " (" + shown.size() + " of " + all.size() + ")" : ""),
			Requirement.Status.MET, null, -1);
		for (Recipe r : shown)
		{
			group.getChildren().add(new Requirement(Requirement.Kind.SKILL, r.use(),
				r.unmet(level) == 0 ? Requirement.Status.MET : Requirement.Status.NOT_MET, r.levels(), -1));
		}
		return group;
	}

	/** "Make it" with its levels, quests, materials and tools ticked off. */
	static Requirement explain(Recipe recipe, int ways, RequirementTree tree, ClueExplainer.Items items, IntFunction<String> names)
	{
		List<Integer> quests = recipe.skills.stream().anyMatch(s -> s.getSkill() == Skill.HERBLORE)
			? Collections.singletonList(net.runelite.api.Quest.DRUIDIC_RITUAL.getId()) : Collections.emptyList();
		BlockParser.Block block = new BlockParser.Block(quests, recipe.skills, 0, 0);
		List<Requirement> children = new ArrayList<>(tree.explain(null, block, null, Collections.emptyList()).getRequirements());
		for (Material m : recipe.materials)
		{
			String label = m.quantity > 1 ? m.quantity + " x " + m.name : m.name;
			children.add(ClueExplainer.item(Requirement.Kind.ITEM, label, new Named(m.name, m.quantity, false, names), false, items));
		}
		for (String tool : recipe.tools)
		{
			children.add(ClueExplainer.item(Requirement.Kind.ITEM, tool, new Named(tool, 1, true, names), false, items));
		}
		String facility = recipe.facility == null ? null : recipe.facility.toLowerCase(Locale.ROOT);
		String label = recipe.heading() + (facility == null ? "" : (facility.endsWith("patch") ? " in a " : " at a ") + facility)
			+ (ways > 1 ? " (easiest of " + ways + " ways)" : "");
		Requirement make = new Requirement(Requirement.Kind.GROUP, label,
			children.stream().anyMatch(Requirement::anyUnmet) ? Requirement.Status.NOT_MET : Requirement.Status.MET, null, -1);
		make.getChildren().addAll(children);
		return make;
	}
}
