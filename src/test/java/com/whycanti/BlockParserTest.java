package com.whycanti;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.Skill;
import org.junit.Test;

public class BlockParserTest
{
	private final BlockParser parser = new BlockParser(TestData.data());

	@Test
	public void skillLevelOf()
	{
		BlockParser.Block b = parser.parse("You need a Mining level of 15 to mine this rock.");
		assertEquals(Collections.singletonList(TestData.level(Skill.MINING, 15)), b.getSkills());
		assertEquals(Collections.emptyList(), b.getQuests());
	}

	@Test
	public void otherSkillWordings()
	{
		assertEquals(Collections.singletonList(TestData.level(Skill.AGILITY, 50)),
			parser.parse("You need an Agility level of at least 50 to use this shortcut.").getSkills());
		assertEquals(Collections.singletonList(TestData.level(Skill.MAGIC, 75)),
			parser.parse("You need level 75 Magic to cast this spell.").getSkills());
		assertEquals(Collections.singletonList(TestData.level(Skill.RUNECRAFT, 44)),
			parser.parse("You need at least 44 Runecrafting to craft these runes.").getSkills());
		assertEquals(Collections.singletonList(TestData.level(Skill.ATTACK, 70)),
			parser.parse("You need a level of 70 in Attack to wield this.").getSkills());
	}

	@Test
	public void severalSkills()
	{
		BlockParser.Block b = parser.parse("You need a Crafting level of 10 and a <col=ff0000>Smithing</col> level of 20.");
		assertEquals(Arrays.asList(TestData.level(Skill.CRAFTING, 10), TestData.level(Skill.SMITHING, 20)), b.getSkills());
	}

	@Test
	public void quest()
	{
		BlockParser.Block b = parser.parse("You need to have completed Dragon Slayer I to wear this.");
		assertEquals(Collections.singletonList(TestData.DRAGON_SLAYER_I), b.getQuests());
	}

	@Test
	public void longestQuestNameWins()
	{
		BlockParser.Block b = parser.parse("You must complete Dragon Slayer II<br>before entering.");
		assertEquals(Collections.singletonList(TestData.DRAGON_SLAYER_II), b.getQuests());
	}

	@Test
	public void questNamesAreWholeWords()
	{
		assertNull(parser.parse("You need Dragon Slayer Isle access."));
	}

	@Test
	public void questPointsAndCombat()
	{
		BlockParser.Block b = parser.parse("You need at least 32 quest points and a combat level of 40.");
		assertEquals(32, b.getQuestPoints());
		assertEquals(40, b.getCombat());
	}

	@Test
	public void notABlock()
	{
		assertNull(parser.parse("Congratulations, you've just advanced your Mining level. You are now level 15."));
		assertNull(parser.parse("You have completed Cook's Assistant!"));
		assertNull(parser.parse("You need to wait a moment."));
	}

	@Test
	public void combinedLevelsAreNotOneSkill()
	{
		assertNull(parser.parse("You need a combined Attack and Strength level of 130 to enter."));
	}

	@Test
	public void gated()
	{
		assertEquals(true, BlockParser.gated("You are not a high enough level to use this item."));
		assertEquals(false, BlockParser.gated("You equip the Abyssal whip."));
	}
}
