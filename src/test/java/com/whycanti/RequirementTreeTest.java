package com.whycanti;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;

public class RequirementTreeTest
{
	private final GameData data = TestData.data();
	private final TestData.Player player = new TestData.Player();

	private Requirement quest(int id)
	{
		return new RequirementTree(data, player).quest(id);
	}

	@Test
	public void fullChain()
	{
		Requirement ds2 = quest(TestData.DRAGON_SLAYER_II);
		assertEquals(Requirement.Status.NOT_MET, ds2.getStatus());
		assertEquals("not started", ds2.getNote());
		List<Requirement> c = ds2.getChildren();
		assertEquals(Arrays.asList("75 Magic", "70 Smithing", "200 quest points", "Legends' Quest", "Dragon Slayer I"),
			Arrays.asList(c.get(0).getLabel(), c.get(1).getLabel(), c.get(2).getLabel(), c.get(3).getLabel(), c.get(4).getLabel()));
		// Legends' Quest lists its own requirements under it
		assertEquals("50 Agility", c.get(3).getChildren().get(0).getLabel());
		assertEquals("107 quest points", c.get(3).getChildren().get(2).getLabel());
	}

	@Test
	public void finishedQuestsCollapse()
	{
		player.quests.put(TestData.LEGENDS, QuestState.FINISHED);
		Requirement legends = quest(TestData.DRAGON_SLAYER_II).getChildren().get(3);
		assertTrue(legends.isMet());
		assertTrue(legends.getChildren().isEmpty());
	}

	@Test
	public void startedQuestsCountAsPartly()
	{
		player.quests.put(TestData.DRAGON_SLAYER_I, QuestState.IN_PROGRESS);
		Requirement ds1 = quest(TestData.DRAGON_SLAYER_II).getChildren().get(4);
		assertEquals(Requirement.Status.PARTLY, ds1.getStatus());
		assertEquals("in progress", ds1.getNote());
		assertTrue(ds1.getChildren().isEmpty());
	}

	@Test
	public void readyToStart()
	{
		player.real.put(Skill.AGILITY, 50);
		player.real.put(Skill.HERBLORE, 45);
		player.questPoints = 107;
		Requirement legends = quest(TestData.LEGENDS);
		assertEquals("ready to start", legends.getNote());
		assertTrue(legends.getChildren().stream().allMatch(Requirement::isMet));
	}

	@Test
	public void boosts()
	{
		player.real.put(Skill.HERBLORE, 42);
		player.boosted.put(Skill.HERBLORE, 45);
		player.real.put(Skill.MAGIC, 72);
		player.boosted.put(Skill.MAGIC, 75);
		Requirement ds2 = quest(TestData.DRAGON_SLAYER_II);
		// Legends' Quest allows boosts, Dragon Slayer II doesn't
		assertTrue(ds2.getChildren().get(3).getChildren().get(1).isMet());
		Requirement magic = ds2.getChildren().get(0);
		assertEquals(Requirement.Status.NOT_MET, magic.getStatus());
		assertEquals("you: 72, boosted 75, boosts don't count", magic.getNote());
		// a level named in a message might allow boosts
		Requirement unknown = new RequirementTree(data, player).skill(TestData.level(Skill.MAGIC, 75), RequirementTree.Boosts.UNKNOWN);
		assertEquals(Requirement.Status.PARTLY, unknown.getStatus());
	}

	@Test
	public void repeatsSaySeeAbove()
	{
		Requirement rfd = quest(TestData.RFD);
		Requirement cooks = rfd.getChildren().get(0);
		assertEquals("ready to start", cooks.getNote());
		Requirement sub = rfd.getChildren().get(1);
		assertEquals("Recipe for Disaster - Another Cook's Quest", sub.getLabel());
		assertEquals("see above", sub.getChildren().get(1).getNote());
	}

	@Test
	public void itemBlock()
	{
		player.real.put(Skill.ATTACK, 65);
		BlockParser.Block block = new BlockParser(data).parse("You need to have completed Dragon Slayer I to wear this.");
		Explanation e = new RequirementTree(data, player).explain("You need to have completed Dragon Slayer I to wear this.", block,
			"Rune platebody", Arrays.asList(TestData.level(Skill.DEFENCE, 40)));
		Requirement item = e.getRequirements().get(0);
		assertEquals("Rune platebody", item.getLabel());
		assertFalse(item.isMet());
		assertEquals(Arrays.asList("40 Defence", "Dragon Slayer I"),
			Arrays.asList(item.getChildren().get(0).getLabel(), item.getChildren().get(1).getLabel()));
	}

	@Test
	public void nothingMissing()
	{
		player.real.put(Skill.MINING, 20);
		Explanation e = new RequirementTree(data, player).explain("x",
			new BlockParser.Block(Collections.emptyList(), Collections.singletonList(TestData.level(Skill.MINING, 15)), 0, 0), null, Collections.emptyList());
		assertFalse(e.anyUnmet());
	}
}
