package com.whycanti;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/** Turns what a block asks for into a tree of requirements, ticked off against the player's state. */
class RequirementTree
{
	/** How skill requirements treat boosted levels. */
	enum Boosts
	{
		/** Boosted levels count, like quests flagged as boostable. */
		COUNT,
		/** Only real levels count, like quests without the flag and wearing items. */
		IGNORED,
		/** Not known, like levels named in a message: real levels meet them, a boost alone only partly. */
		UNKNOWN
	}

	private final GameData data;
	private final PlayerState player;
	/** Quests already listed in full, so a quest needed twice says "see above" the second time. */
	private final Set<Integer> listed = new HashSet<>();

	RequirementTree(GameData data, PlayerState player)
	{
		this.data = data;
		this.player = player;
	}

	/** The full explanation of a block, with the item's wear requirements when it came from equipping one. */
	Explanation explain(String message, BlockParser.Block block, String itemName, List<GameData.SkillLevel> itemSkills)
	{
		List<Requirement> list = new ArrayList<>();
		if (itemName != null)
		{
			List<Requirement> children = new ArrayList<>();
			List<GameData.SkillLevel> skills = new ArrayList<>(itemSkills);
			for (GameData.SkillLevel s : block.getSkills())
			{
				if (skills.stream().noneMatch(k -> k.getSkill() == s.getSkill()))
				{
					skills.add(s);
				}
			}
			for (GameData.SkillLevel s : skills)
			{
				children.add(skill(s, Boosts.IGNORED));
			}
			for (int quest : block.getQuests())
			{
				children.add(quest(quest));
			}
			Requirement item = new Requirement(Requirement.Kind.ITEM, itemName,
				children.stream().anyMatch(Requirement::anyUnmet) ? Requirement.Status.NOT_MET : Requirement.Status.MET, null, -1);
			item.getChildren().addAll(children);
			list.add(item);
		}
		else
		{
			for (GameData.SkillLevel s : block.getSkills())
			{
				list.add(skill(s, Boosts.UNKNOWN));
			}
			for (int quest : block.getQuests())
			{
				list.add(quest(quest));
			}
		}
		if (block.getQuestPoints() > 0)
		{
			list.add(questPoints(block.getQuestPoints()));
		}
		if (block.getCombat() > 0)
		{
			list.add(combat(block.getCombat()));
		}
		return new Explanation(message, list);
	}

	/** A quest and, unless it's done or started, everything it needs. */
	Requirement quest(int id)
	{
		GameData.Quest quest = data.quest(id);
		String name = quest == null ? "Quest " + id : quest.getName();
		QuestState state = player.questState(id);
		if (state == QuestState.FINISHED)
		{
			return new Requirement(Requirement.Kind.QUEST, name, Requirement.Status.MET, null, id);
		}
		if (state == QuestState.IN_PROGRESS)
		{
			// starting it needed its requirements, so only its unfinished subquests are left to list
			Requirement started = new Requirement(Requirement.Kind.QUEST, name, Requirement.Status.PARTLY, "in progress", id);
			if (listed.add(id))
			{
				for (int sub : data.subquests(id))
				{
					started.getChildren().add(quest(sub));
				}
			}
			return started;
		}
		if (quest == null || !listed.add(id))
		{
			return new Requirement(Requirement.Kind.QUEST, name, Requirement.Status.NOT_MET, quest == null ? null : "see above", id);
		}

		List<Requirement> children = new ArrayList<>();
		Boosts boosts = quest.isBoostable() ? Boosts.COUNT : Boosts.IGNORED;
		for (GameData.SkillLevel s : quest.getStats())
		{
			children.add(skill(s, boosts));
		}
		if (quest.getQuestPoints() > 0)
		{
			children.add(questPoints(quest.getQuestPoints()));
		}
		if (quest.getCombat() > 0)
		{
			children.add(combat(quest.getCombat()));
		}
		for (int required : quest.getQuests())
		{
			children.add(quest(required));
		}
		boolean ready = children.stream().noneMatch(Requirement::anyUnmet);
		for (int sub : data.subquests(id))
		{
			children.add(quest(sub));
		}
		Requirement node = new Requirement(Requirement.Kind.QUEST, name, Requirement.Status.NOT_MET,
			ready ? "ready to start" : "not started", id);
		node.getChildren().addAll(children);
		return node;
	}

	Requirement skill(GameData.SkillLevel need, Boosts boosts)
	{
		Skill skill = need.getSkill();
		int real = player.realLevel(skill);
		int boosted = player.boostedLevel(skill);
		String label = need.getLevel() + " " + skill.getName();
		Requirement.Status status;
		String note;
		if (real >= need.getLevel())
		{
			status = Requirement.Status.MET;
			note = null;
		}
		else if (boosted >= need.getLevel())
		{
			status = boosts == Boosts.COUNT ? Requirement.Status.MET
				: boosts == Boosts.UNKNOWN ? Requirement.Status.PARTLY : Requirement.Status.NOT_MET;
			note = "you: " + real + ", boosted " + boosted + (boosts == Boosts.IGNORED ? ", boosts don't count" : "");
		}
		else
		{
			status = Requirement.Status.NOT_MET;
			note = "you: " + real + (boosts == Boosts.COUNT ? ", boosts count" : "");
		}
		return new Requirement(Requirement.Kind.SKILL, label, status, note, -1);
	}

	Requirement questPoints(int need)
	{
		int have = player.questPoints();
		return new Requirement(Requirement.Kind.QUEST_POINTS, need + " quest points",
			have >= need ? Requirement.Status.MET : Requirement.Status.NOT_MET, have >= need ? null : "you: " + have, -1);
	}

	Requirement combat(int need)
	{
		int have = player.combatLevel();
		return new Requirement(Requirement.Kind.COMBAT, "Combat level " + need,
			have >= need ? Requirement.Status.MET : Requirement.Status.NOT_MET, have >= need ? null : "you: " + have, -1);
	}
}
