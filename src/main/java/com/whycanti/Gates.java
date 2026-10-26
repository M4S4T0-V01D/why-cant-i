package com.whycanti;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import net.runelite.api.Skill;

/** Ladders, doors and gates that only let you through with a level, a quest, quest points, something worn or an unlock. */
final class Gates
{
	static final class Gate
	{
		final String name;
		final List<GameData.SkillLevel> skills = new ArrayList<>();
		final List<String> quests = new ArrayList<>();
		int questPoints;
		/** What you need to wear, by lower case item name, and how to say it; any max cape or hood counts too. */
		Set<String> wear = Collections.emptySet();
		String wearLabel;
		/** Warriors' Guild: Attack and Strength adding up to 130, or either at 99. */
		boolean warriors;
		/** For an unlock the object shows by which version it is: true unlocked, false not, null no unlock. */
		Boolean unlocked;
		String unlockLabel;

		Gate(String name)
		{
			this.name = name;
		}

		Gate skill(Skill skill, int level)
		{
			skills.add(new GameData.SkillLevel(skill, level));
			return this;
		}

		Gate quest(String quest)
		{
			quests.add(quest);
			return this;
		}
	}

	private static final Map<Integer, Gate> GATES = new HashMap<>();

	static
	{
		gate(new Gate("Mining Guild").skill(Skill.MINING, 60), 30367, 30364);
		gate(new Gate("Fishing Guild").skill(Skill.FISHING, 68), 20925);
		gate(new Gate("Woodcutting Guild").skill(Skill.WOODCUTTING, 60), 28851, 28852);
		gate(new Gate("Ranging Guild").skill(Skill.RANGED, 40), 11665);
		gate(new Gate("Wizards' Guild").skill(Skill.MAGIC, 66), 1732, 1733);
		Gate crafting = new Gate("Crafting Guild").skill(Skill.CRAFTING, 40);
		crafting.wear = Set.of("brown apron", "golden apron", "crafting cape", "crafting cape(t)");
		crafting.wearLabel = "Wear a brown apron or Crafting cape";
		gate(crafting, 14910);
		Gate cooks = new Gate("Cooks' Guild").skill(Skill.COOKING, 32);
		cooks.wear = Set.of("chef's hat", "golden chef's hat", "cooking cape", "cooking cape(t)", "cooking hood",
			"varrock armour 3", "varrock armour 4");
		cooks.wearLabel = "Wear a chef's hat, Cooking cape or Varrock armour 3";
		gate(cooks, 10045);
		Gate champions = new Gate("Champions' Guild");
		champions.questPoints = 32;
		gate(champions, 1805);
		gate(new Gate("Heroes' Guild").quest("Heroes' Quest"), 2624, 2625);
		gate(new Gate("Legends' Guild").quest("Legends' Quest"), 2391, 2392, 2896, 2897);
		Gate warriors = new Gate("Warriors' Guild");
		warriors.warriors = true;
		gate(warriors, 24318);
		gate(new Gate("Wilderness Agility Course").skill(Skill.AGILITY, 52), 23552, 23554, 23555);
		// the upper level's ladder is a different object once Prospector Percy has unlocked it
		gate(motherlode(true), 19047, 19049);
		gate(motherlode(false), 19048, 19050);
	}

	private Gates()
	{
	}

	private static Gate motherlode(boolean unlocked)
	{
		Gate gate = new Gate("Motherlode Mine upper level");
		gate.unlocked = unlocked;
		gate.unlockLabel = "Unlock it from Prospector Percy for 100 golden nuggets";
		return gate;
	}

	private static void gate(Gate gate, int... ids)
	{
		for (int id : ids)
		{
			GATES.put(id, gate);
		}
	}

	/** What an object needs to get through, or null. */
	static Gate gate(int objectId)
	{
		return GATES.get(objectId);
	}

	/** What a gate needs and whether you have it. Client thread. */
	static List<Requirement> explain(Gate gate, GameData data, RequirementTree tree, PlayerState player, int[] worn,
		IntFunction<String> itemName)
	{
		List<Integer> quests = new ArrayList<>();
		for (String q : gate.quests)
		{
			GameData.Quest quest = data.questNamed(q);
			if (quest != null)
			{
				quests.add(quest.getId());
			}
		}
		BlockParser.Block block = new BlockParser.Block(quests, gate.skills, gate.questPoints, 0);
		List<Requirement> list = new ArrayList<>(tree.explain(null, block, null, Collections.emptyList()).getRequirements());
		if (gate.warriors)
		{
			int attack = player.realLevel(Skill.ATTACK);
			int strength = player.realLevel(Skill.STRENGTH);
			list.add(new Requirement(Requirement.Kind.SKILL, "130 Attack and Strength added up, or 99 in either",
				warriors(attack, strength) ? Requirement.Status.MET : Requirement.Status.NOT_MET,
				"you: " + attack + " + " + strength + " = " + (attack + strength), -1));
		}
		if (!gate.wear.isEmpty())
		{
			boolean wearing = wearing(gate, worn, itemName);
			list.add(new Requirement(Requirement.Kind.ITEM, gate.wearLabel,
				wearing ? Requirement.Status.MET : Requirement.Status.NOT_MET, wearing ? null : "or any max cape", -1));
		}
		if (gate.unlocked != null)
		{
			list.add(new Requirement(Requirement.Kind.ITEM, gate.unlockLabel,
				gate.unlocked ? Requirement.Status.MET : Requirement.Status.NOT_MET, null, -1));
		}
		return list;
	}

	static boolean warriors(int attack, int strength)
	{
		return attack + strength >= 130 || attack >= 99 || strength >= 99;
	}

	static boolean wearing(Gate gate, int[] worn, IntFunction<String> itemName)
	{
		for (int id : worn == null ? new int[0] : worn)
		{
			String name = id < 0 ? null : itemName.apply(id);
			if (name != null && wears(gate, name))
			{
				return true;
			}
		}
		return false;
	}

	static boolean wears(Gate gate, String itemName)
	{
		String name = itemName.toLowerCase(Locale.ROOT);
		return gate.wear.contains(name) || name.endsWith("max cape") || name.endsWith("max hood");
	}
}
