package com.whycanti;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.AgilityShortcut;

/** Agility shortcuts, from RuneLite's list, and the rooftop and other courses, by the map region they're in. */
final class Agility
{
	/** An agility course and the level its first obstacle needs. */
	static final class Course
	{
		final String name;
		final int level;

		Course(String name, int level)
		{
			this.name = name;
			this.level = level;
		}
	}

	private static final Map<Integer, Course> COURSES = new HashMap<>();
	/** Ladders and stairs are just ways up and down, even inside a course, apart from these obstacles. */
	private static final Set<String> CLIMBS = Set.of("ladder", "staircase");
	private static final Set<Integer> COURSE_LADDERS = Set.of(
		36221, // Prifddinas, the first obstacle
		42209, // Shayzien
		16682); // Barbarian Outpost
	/** What you do to an obstacle, as the first word of its menu option. */
	private static final Set<String> MOVES = Set.of("climb", "climb-up", "climb-down", "climb-over", "climb-on", "jump",
		"jump-up", "jump-over", "jump-on", "jump-in", "cross", "balance", "balance-across", "vault", "leap", "hurdle",
		"swing-on", "swing-across", "squeeze-through", "squeeze-past", "walk-on", "walk-across", "grab", "hop", "hop-on",
		"teeth-grip", "tightrope", "walk-down", "slide-down", "run-across", "shimmy", "crawl-through");

	static
	{
		course(9781, "Gnome Stronghold Agility Course", 1);
		course(6200, "Shayzien Basic Agility Course", 1);
		course(12338, "Draynor Village Rooftop Course", 10);
		course(13105, "Al Kharid Rooftop Course", 20);
		course(13356, "Agility Pyramid", 30);
		course(12853, "Varrock Rooftop Course", 30);
		course(10559, "Penguin Agility Course", 30);
		course(10039, "Barbarian Outpost Agility Course", 35);
		course(13878, "Canifis Rooftop Course", 40);
		course(5944, "Shayzien Advanced Agility Course", 45);
		course(11050, "Ape Atoll Agility Course", 48);
		course(12084, "Falador Rooftop Course", 50);
		course(11837, "Wilderness Agility Course", 52);
		course(14234, "Werewolf Agility Course", 60);
		course(10806, "Seers' Village Rooftop Course", 60);
		course(13358, "Pollnivneach Rooftop Course", 70);
		course(12895, "Prifddinas Agility Course", 75);
		course(10553, "Rellekka Rooftop Course", 80);
		course(10547, "Ardougne Rooftop Course", 90);
	}

	private Agility()
	{
	}

	private static void course(int region, String name, int level)
	{
		COURSES.put(region, new Course(name, level));
	}

	/** The shortcut an object is part of, the nearest to the player of those it could be, or null. */
	static AgilityShortcut shortcut(int objectId, WorldPoint player)
	{
		AgilityShortcut best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (AgilityShortcut s : AgilityShortcut.values())
		{
			boolean has = false;
			for (int id : s.getObstacleIds())
			{
				has |= id == objectId;
			}
			if (!has)
			{
				continue;
			}
			WorldPoint at = s.getWorldLocation();
			int distance = at == null || player == null || at.getPlane() != player.getPlane()
				? Integer.MAX_VALUE - 1 : at.distanceTo2D(player);
			if (best == null || distance < bestDistance)
			{
				best = s;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** The course an obstacle is on, going by the region the player is in and whether it has an obstacle's option. */
	static Course course(int region, int objectId, String name, String[] actions)
	{
		Course course = COURSES.get(region);
		boolean climb = name != null && CLIMBS.contains(name.toLowerCase(Locale.ROOT)) && !COURSE_LADDERS.contains(objectId);
		return course != null && !climb && obstacle(actions) ? course : null;
	}

	static boolean obstacle(String[] actions)
	{
		if (actions != null)
		{
			for (String a : actions)
			{
				if (a != null && MOVES.contains(a.split(" ")[0].toLowerCase(Locale.ROOT)))
				{
					return true;
				}
			}
		}
		return false;
	}
}
