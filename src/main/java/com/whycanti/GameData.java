package com.whycanti;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;

/**
 * Quest requirements from the game's quest table, and item wear requirements from item params, both read from the
 * client's cache. Quest ids are the quest table's row ids, which are also RuneLite's {@link net.runelite.api.Quest} ids.
 */
public class GameData
{
	@Value
	static class SkillLevel
	{
		Skill skill;
		int level;
	}

	@Value
	static class Quest
	{
		int id;
		String name;
		boolean members;
		List<SkillLevel> stats;
		List<Integer> quests;
		int questPoints;
		int combat;
		/** Whether boosted levels count for its skill requirements. */
		boolean boostable;
		/** The quest this is part of, or -1. */
		int parent;
	}

	/** Item params holding the skills needed to wear an item, each paired with the param holding the level. */
	private static final int[][] WEAR_PARAMS = {{434, 436}, {435, 437}, {191, 613}, {579, 614}, {610, 615}, {611, 616}, {612, 617}};
	/** The quest table's default combat requirement, which every quest meets. */
	private static final int NO_COMBAT = 3;

	private final Map<Integer, Quest> quests;
	private final Map<Integer, List<Integer>> subquests = new HashMap<>();
	/** Quests by lower case name, longest name first so a search finds "Dragon Slayer II" before "Dragon Slayer I". */
	private final Map<String, Quest> byName = new LinkedHashMap<>();

	GameData(Collection<Quest> quests)
	{
		Map<Integer, Quest> map = new HashMap<>();
		for (Quest q : quests)
		{
			map.put(q.getId(), q);
			if (q.getParent() >= 0)
			{
				subquests.computeIfAbsent(q.getParent(), k -> new ArrayList<>()).add(q.getId());
			}
		}
		this.quests = map;
		List<Quest> sorted = new ArrayList<>(quests);
		sorted.sort(Comparator.comparingInt((Quest q) -> -q.getName().length()).thenComparing(Quest::getName));
		for (Quest q : sorted)
		{
			byName.putIfAbsent(q.getName().toLowerCase(Locale.ROOT), q);
		}
	}

	Quest quest(int id)
	{
		return quests.get(id);
	}

	List<Integer> subquests(int id)
	{
		return subquests.getOrDefault(id, Collections.emptyList());
	}

	/** Every quest, longest name first. */
	Collection<Quest> questsByName()
	{
		return Collections.unmodifiableCollection(byName.values());
	}

	Quest questNamed(String name)
	{
		return byName.get(name.toLowerCase(Locale.ROOT));
	}

	/** Reads the quest table. Client thread, once the cache is loaded. */
	static GameData load(Client client)
	{
		List<Quest> list = new ArrayList<>();
		for (int row : client.getDBTableRows(DBTableID.Quest.ID))
		{
			String name = string(client, row, DBTableID.Quest.COL_DISPLAYNAME);
			if (name.isEmpty())
			{
				continue;
			}
			// list columns: each read returns one position of the tuple across every entry
			Object[] statIds = field(client, row, DBTableID.Quest.COL_REQUIREMENT_STATS, 0);
			Object[] statLevels = field(client, row, DBTableID.Quest.COL_REQUIREMENT_STATS, 1);
			List<SkillLevel> stats = new ArrayList<>();
			for (int i = 0; i < Math.min(statIds.length, statLevels.length); i++)
			{
				Skill skill = skill(number(statIds[i], -1));
				int level = number(statLevels[i], 0);
				if (skill != null && level > 1)
				{
					stats.add(new SkillLevel(skill, level));
				}
			}
			List<Integer> required = new ArrayList<>();
			for (Object q : field(client, row, DBTableID.Quest.COL_REQUIREMENT_QUESTS, 0))
			{
				int id = number(q, -1);
				if (id >= 0)
				{
					required.add(id);
				}
			}
			int combat = integer(client, row, DBTableID.Quest.COL_REQUIREMENT_COMBAT, NO_COMBAT);
			list.add(new Quest(row, name, integer(client, row, DBTableID.Quest.COL_MEMBERS, 0) != 0 || bool(client, row, DBTableID.Quest.COL_MEMBERS),
				stats, required, integer(client, row, DBTableID.Quest.COL_REQUIREMENT_QUESTPOINTS, 0),
				combat > NO_COMBAT ? combat : 0, bool(client, row, DBTableID.Quest.COL_REQUIREMENTS_BOOSTABLE),
				integer(client, row, DBTableID.Quest.COL_PARENT_QUEST, -1)));
		}
		return new GameData(list);
	}

	/** The skill levels needed to wear an item. */
	static List<SkillLevel> wearRequirements(ItemComposition item)
	{
		List<SkillLevel> list = new ArrayList<>();
		for (int[] pair : WEAR_PARAMS)
		{
			Skill skill = skill(item.getIntValue(pair[0]));
			int level = item.getIntValue(pair[1]);
			if (skill != null && level > 1)
			{
				list.add(new SkillLevel(skill, level));
			}
		}
		return list;
	}

	/** The skill for a game stat id; RuneLite's skills are in the game's stat order, with the deprecated Overall after them. */
	static Skill skill(int stat)
	{
		Skill[] skills = Skill.values();
		return stat >= 0 && stat < skills.length && stat <= Skill.SAILING.ordinal() ? skills[stat] : null;
	}

	private static Object[] field(Client client, int row, int column, int tuple)
	{
		Object[] values = client.getDBTableField(row, column, tuple);
		return values == null ? new Object[0] : values;
	}

	private static int number(Object value, int otherwise)
	{
		return value instanceof Number ? ((Number) value).intValue() : otherwise;
	}

	private static int integer(Client client, int row, int column, int otherwise)
	{
		Object[] values = field(client, row, column, 0);
		return values.length > 0 ? number(values[0], otherwise) : otherwise;
	}

	private static boolean bool(Client client, int row, int column)
	{
		Object[] values = field(client, row, column, 0);
		return values.length > 0 && Boolean.TRUE.equals(values[0]);
	}

	private static String string(Client client, int row, int column)
	{
		Object[] values = field(client, row, column, 0);
		return values.length > 0 && values[0] instanceof String ? (String) values[0] : "";
	}
}
