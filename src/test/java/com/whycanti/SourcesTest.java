package com.whycanti;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Skill;
import org.junit.Test;

public class SourcesTest
{
	@Test
	public void gemsComeFromGemRocks()
	{
		List<Sources.Source> sources = Sources.forItem("Uncut sapphire");
		Sources.Source rocks = sources.stream().filter(s -> s.from.equals("Gem rocks")).findFirst().orElse(null);
		assertNotNull(rocks);
		assertEquals("Mine Gem rocks", rocks.label());
		assertEquals(Skill.MINING, rocks.skill.getSkill());
		assertEquals(40, rocks.skill.getLevel());
	}

	@Test
	public void getItTicksLevelsAndCountsMonsters()
	{
		Requirement group = Sources.getIt("Uncut sapphire", s -> s == Skill.MINING ? 40 : 1);
		Requirement rocks = group.getChildren().stream().filter(r -> r.getLabel().equals("Mine Gem rocks")).findFirst().get();
		assertEquals(Requirement.Status.MET, rocks.getStatus());
		Requirement stall = group.getChildren().stream().filter(r -> r.getLabel().equals("Steal from Gem stall")).findFirst().get();
		assertEquals(Requirement.Status.NOT_MET, stall.getStatus());
		assertTrue(group.getChildren().stream().anyMatch(r -> r.getLabel().matches("And \\d+ more monsters")));
	}

	@Test
	public void findsObjectsById()
	{
		List<Sources.Thing> gems = Sources.find(false, 11380, "Rocks", null);
		assertEquals("Gem rocks", gems.get(0).name);
		assertEquals("mine", gems.get(0).verb);
		assertEquals(40, gems.get(0).skill.getLevel());
		assertEquals("Pickaxe", gems.get(0).tool());
		assertTrue(gems.get(0).loot.stream().anyMatch(l -> l[0].equals("Uncut opal")));

		Sources.Thing stall = Sources.find(false, 7053, "Seed stall", null).get(0);
		assertEquals("steal from", stall.verb);
		assertEquals(27, stall.skill.getLevel());

		assertEquals("Fishing spot (small net, bait)", Sources.find(true, 1530, "Fishing spot", null).get(0).name);
	}

	@Test
	public void findsByNameWhenTheWikiHasNoId()
	{
		List<Sources.Thing> man = Sources.find(true, 99999, "Man", new String[]{"Talk-to", "Attack", "Pickpocket"});
		assertEquals(1, man.size());
		assertEquals("pickpocket", man.get(0).verb);
		List<Sources.Thing> guard = Sources.find(true, 99999, "Guard", null);
		assertEquals(1, guard.size());
		assertEquals("Guard", guard.get(0).name);
		assertTrue(Sources.find(false, 99999, "Nothing like this", null).isEmpty());
	}

	@Test
	public void salvageCanBeSorted()
	{
		List<Sources.Thing> salvage = Sources.opened("Small salvage");
		assertEquals(1, salvage.size());
		assertEquals("sort", salvage.get(0).verb);
		assertEquals(Skill.SAILING, salvage.get(0).skill.getSkill());
	}

	@Test
	public void agilityCoursesAndShortcuts()
	{
		assertEquals(40, Agility.course(13878, 1, "Gap", new String[]{"Jump", null}).level);
		assertNull(Agility.course(13878, 1, "Door", new String[]{"Open", "Close"}));
		assertNull(Agility.course(1, 1, "Gap", new String[]{"Jump"}));
		assertNull(Agility.course(12084, 30367, "Ladder", new String[]{"Climb-down"}));
		assertNull(Agility.course(12084, 1, "Staircase", new String[]{"Climb-up"}));
		assertEquals(75, Agility.course(12895, 36221, "Ladder", new String[]{"Climb"}).level);
		assertEquals(60, Gates.gate(30367).skills.get(0).getLevel());
		assertNull(Gates.gate(1));
		assertEquals(32, Gates.gate(1805).questPoints);
		assertEquals(java.util.List.of("Legends' Quest"), Gates.gate(2392).quests);
		assertTrue(Gates.gate(19047).unlocked);
		assertFalse(Gates.gate(19048).unlocked);
		assertTrue(Gates.warriors(65, 65));
		assertTrue(Gates.warriors(99, 1));
		assertFalse(Gates.warriors(70, 59));
		assertTrue(Gates.wears(Gates.gate(14910), "Brown apron"));
		assertTrue(Gates.wears(Gates.gate(10045), "Infernal max cape"));
		assertFalse(Gates.wears(Gates.gate(10045), "Brown apron"));
		List<Integer> levels = java.util.Arrays.stream(net.runelite.client.game.AgilityShortcut.values())
			.map(net.runelite.client.game.AgilityShortcut::getLevel).collect(Collectors.toList());
		assertTrue(levels.contains(1));
	}

	@Test
	public void keysDropBrackets()
	{
		assertEquals("fishing spot", Sources.key("Fishing spot (small net, bait)"));
		assertEquals("Mining:40", "Mining:" + Sources.skill("Mining:40").getLevel());
		assertNull(Sources.skill(""));
	}
}
