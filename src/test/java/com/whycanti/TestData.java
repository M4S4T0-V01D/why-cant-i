package com.whycanti;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/** A few real quests with their cache requirements, and a player to check them against. */
final class TestData
{
	static final int COOKS_ASSISTANT = 17;
	static final int BLACK_KNIGHTS = 10;
	static final int DRAGON_SLAYER_I = 31;
	static final int DRAGON_SLAYER_II = 32;
	static final int LEGENDS = 90;
	static final int RFD = 100;
	static final int RFD_COOK = 101;

	private TestData()
	{
	}

	static GameData data()
	{
		return new GameData(Arrays.asList(
			quest(COOKS_ASSISTANT, "Cook's Assistant"),
			quest(BLACK_KNIGHTS, "Black Knights' Fortress"),
			new GameData.Quest(DRAGON_SLAYER_I, "Dragon Slayer I", false, Collections.emptyList(), Collections.emptyList(), 32, 0, false, -1),
			new GameData.Quest(LEGENDS, "Legends' Quest", true, Arrays.asList(level(Skill.AGILITY, 50), level(Skill.HERBLORE, 45)),
				Collections.emptyList(), 107, 0, true, -1),
			new GameData.Quest(DRAGON_SLAYER_II, "Dragon Slayer II", true, Arrays.asList(level(Skill.MAGIC, 75), level(Skill.SMITHING, 70)),
				Arrays.asList(LEGENDS, DRAGON_SLAYER_I), 200, 0, false, -1),
			new GameData.Quest(RFD, "Recipe for Disaster", true, Collections.emptyList(), Arrays.asList(COOKS_ASSISTANT), 0, 0, false, -1),
			new GameData.Quest(RFD_COOK, "Recipe for Disaster - Another Cook's Quest", true, Arrays.asList(level(Skill.COOKING, 10)),
				Arrays.asList(COOKS_ASSISTANT), 0, 0, false, RFD)));
	}

	static GameData.Quest quest(int id, String name)
	{
		return new GameData.Quest(id, name, false, Collections.emptyList(), Collections.emptyList(), 0, 0, false, -1);
	}

	static GameData.SkillLevel level(Skill skill, int level)
	{
		return new GameData.SkillLevel(skill, level);
	}

	static class Player implements PlayerState
	{
		final Map<Skill, Integer> real = new EnumMap<>(Skill.class);
		final Map<Skill, Integer> boosted = new EnumMap<>(Skill.class);
		final Map<Integer, QuestState> quests = new HashMap<>();
		int questPoints;
		int combat = 3;

		@Override
		public int realLevel(Skill skill)
		{
			return real.getOrDefault(skill, 1);
		}

		@Override
		public int boostedLevel(Skill skill)
		{
			return boosted.getOrDefault(skill, realLevel(skill));
		}

		@Override
		public QuestState questState(int quest)
		{
			return quests.getOrDefault(quest, QuestState.NOT_STARTED);
		}

		@Override
		public int questPoints()
		{
			return questPoints;
		}

		@Override
		public int combatLevel()
		{
			return combat;
		}
	}
}
