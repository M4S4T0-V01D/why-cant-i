package com.whycanti;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.api.Skill;

/** Reads what a game message or dialogue says you're missing: quests, skill levels, quest points, combat level. */
class BlockParser
{
	@Value
	static class Block
	{
		List<Integer> quests;
		List<GameData.SkillLevel> skills;
		int questPoints;
		int combat;

		boolean isEmpty()
		{
			return quests.isEmpty() && skills.isEmpty() && questPoints == 0 && combat == 0;
		}
	}

	/** Words that make a message about something you can't do yet, rather than just mentioning a quest or skill. */
	private static final Pattern GATED = Pattern.compile(
		"\\b(need|needs|must|require|requires|required|requirement|have to|not high enough|not a high enough|unable|can't|cannot|haven't|before you can|only (?:those|people|players))\\b");
	private static final Map<String, Skill> SKILLS = new HashMap<>();
	private static final String SKILL;

	static
	{
		for (Skill skill : Skill.values())
		{
			if (skill.ordinal() <= Skill.SAILING.ordinal())
			{
				SKILLS.put(skill.getName().toLowerCase(Locale.ROOT), skill);
			}
		}
		SKILLS.put("runecrafting", Skill.RUNECRAFT);
		SKILLS.put("range", Skill.RANGED);
		SKILLS.put("defense", Skill.DEFENCE);
		SKILL = "(" + String.join("|", SKILLS.keySet()) + ")";
	}

	/** Skill and level patterns, each with the group numbers of the skill and the level. */
	private static final Object[][] SKILL_PATTERNS = {
		// "a Mining level of 15", "an Agility level of at least 50", "Magic level 75"
		{Pattern.compile("\\b" + SKILL + " level (?:of )?(?:at least )?(\\d{1,3})\\b"), 1, 2},
		// "level 50 Agility", "level-50 Agility"
		{Pattern.compile("\\blevel[ -](\\d{1,3}) " + SKILL + "\\b"), 2, 1},
		// "a level of 70 in Attack"
		{Pattern.compile("\\blevel of (?:at least )?(\\d{1,3}) in " + SKILL + "\\b"), 2, 1},
		// "75 Magic", "at least 60 Thieving"
		{Pattern.compile("\\b(\\d{1,3}) " + SKILL + "\\b"), 2, 1},
	};
	private static final Pattern QUEST_POINTS = Pattern.compile("\\b(\\d{1,3}) quest points?\\b");
	private static final Pattern COMBAT = Pattern.compile("\\bcombat level (?:of )?(?:at least )?(\\d{1,3})\\b|\\blevel[ -](\\d{1,3}) combat\\b");
	/** "a combined Attack and Strength level of 130" is a total, not one skill's level. */
	private static final Pattern COMBINED = Pattern.compile("\\b(combined|total)\\b");

	private final GameData data;

	BlockParser(GameData data)
	{
		this.data = data;
	}

	/** Strips tags and line breaks from game text. */
	static String clean(String text)
	{
		return text.replaceAll("(?i)<br>", " ").replaceAll("<[^>]*>", "").replace(' ', ' ').replaceAll("\\s+", " ").trim();
	}

	/** Whether a message reads like the game stopping you from doing something. */
	static boolean gated(String message)
	{
		return GATED.matcher(normalise(message)).find();
	}

	private static String normalise(String message)
	{
		return clean(message).toLowerCase(Locale.ROOT).replace('’', '\'');
	}

	/** What a message says is missing, or null if it isn't a block or names nothing. */
	Block parse(String message)
	{
		String text = normalise(message);
		if (!GATED.matcher(text).find())
		{
			return null;
		}

		List<Integer> quests = new ArrayList<>();
		StringBuilder rest = new StringBuilder(text);
		for (GameData.Quest quest : data.questsByName())
		{
			String name = quest.getName().toLowerCase(Locale.ROOT);
			int at = find(rest, name);
			if (at >= 0)
			{
				quests.add(quest.getId());
				// blank it out so "Dragon Slayer II" doesn't also count as "Dragon Slayer I", and its words aren't read as skills
				for (int i = at; i < at + name.length(); i++)
				{
					rest.setCharAt(i, ' ');
				}
			}
		}
		text = rest.toString();

		List<GameData.SkillLevel> skills = new ArrayList<>();
		if (!COMBINED.matcher(text).find())
		{
			for (Object[] p : SKILL_PATTERNS)
			{
				Matcher m = ((Pattern) p[0]).matcher(text);
				while (m.find())
				{
					Skill skill = SKILLS.get(m.group((int) p[1]));
					int level = Integer.parseInt(m.group((int) p[2]));
					if (level > 1 && level <= 99 && skills.stream().noneMatch(s -> s.getSkill() == skill))
					{
						skills.add(new GameData.SkillLevel(skill, level));
					}
				}
			}
		}

		int questPoints = 0;
		Matcher qp = QUEST_POINTS.matcher(text);
		if (qp.find())
		{
			questPoints = Integer.parseInt(qp.group(1));
		}
		int combat = 0;
		Matcher cb = COMBAT.matcher(text);
		if (cb.find())
		{
			combat = Integer.parseInt(cb.group(1) != null ? cb.group(1) : cb.group(2));
		}

		Block block = new Block(quests, skills, questPoints, combat);
		return block.isEmpty() ? null : block;
	}

	/** Where a name appears in the text as whole words, or -1. */
	private static int find(CharSequence text, String name)
	{
		String s = text.toString();
		for (int at = s.indexOf(name); at >= 0; at = s.indexOf(name, at + 1))
		{
			int end = at + name.length();
			if ((at == 0 || !Character.isLetterOrDigit(s.charAt(at - 1))) && (end == s.length() || !Character.isLetterOrDigit(s.charAt(end))))
			{
				return at;
			}
		}
		return -1;
	}
}
