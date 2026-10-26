package com.whycanti;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Item;
import net.runelite.api.Skill;
import org.junit.Test;

public class RecipesTest
{
	@Test
	public void parsesALine()
	{
		Recipes.Recipe r = Recipes.parse("Rune platebody\tSmithing:99\t5*Runite bar\tHammer\tAnvil");
		assertEquals("Rune platebody", r.output);
		assertEquals(Skill.SMITHING, r.skills.get(0).getSkill());
		assertEquals(99, r.skills.get(0).getLevel());
		assertEquals(5, r.materials.get(0).quantity);
		assertEquals("Runite bar", r.materials.get(0).name);
		assertEquals(Collections.singletonList("Hammer"), r.tools);
		assertEquals("Anvil", r.facility);
		assertNull(Recipes.parse("too\tfew"));
	}

	@Test
	public void matchesAnyDose()
	{
		assertEquals("super attack", Recipes.base("Super attack(4)"));
		List<Recipes.Recipe> recipes = Recipes.forItem("Super attack(4)");
		assertFalse(recipes.isEmpty());
		assertEquals(45, recipes.get(0).highest());
		assertFalse(Recipes.forItem("Rune platebody").isEmpty());
		assertTrue(Recipes.forItem("Not an item").isEmpty());
	}

	@Test
	public void cantMakeWhenEveryWayLacksALevel()
	{
		List<Recipes.Recipe> recipes = Arrays.asList(
			Recipes.parse("X\tSmithing:50\t1*A\t\t"),
			Recipes.parse("X\tCrafting:70\t1*B\t\t"));
		assertTrue(Recipes.cantMake(recipes, s -> 1));
		assertFalse(Recipes.cantMake(recipes, s -> s == Skill.CRAFTING ? 70 : 1));
		assertEquals("B", Recipes.best(recipes, s -> s == Skill.CRAFTING ? 70 : 1).materials.get(0).name);
		assertFalse(Recipes.cantMake(Collections.emptyList(), s -> 1));
	}

	@Test
	public void toolsMatchTheirKinds()
	{
		String[] names = {"", "Rune pickaxe", "Runite bar", "Hammer"};
		Recipes.Named pickaxe = new Recipes.Named("Pickaxe", 1, true, id -> names[id]);
		assertTrue(pickaxe.fulfilledBy(1));
		assertFalse(new Recipes.Named("Pickaxe", 1, false, id -> names[id]).fulfilledBy(1));
		Recipes.Named bars = new Recipes.Named("Runite bar", 5, false, id -> names[id]);
		assertFalse(bars.fulfilledBy(new Item[]{new Item(2, 3)}));
		assertTrue(bars.fulfilledBy(new Item[]{new Item(2, 3), new Item(2, 2)}));
	}

	@Test
	public void growsHerbsFromSeeds()
	{
		Recipes.Recipe grow = Recipes.forItem("Grimy ranarr weed").get(0);
		assertEquals("grow", grow.how);
		assertEquals("Grow it", grow.heading());
		assertEquals(32, grow.highest());
		assertEquals("Ranarr seed", grow.materials.get(0).name);
		assertTrue(Recipes.uses("Ranarr seed").contains(grow));
		assertTrue(Recipes.uses("Grimy ranarr weed").stream().anyMatch(r -> r.output.equals("Ranarr weed")));
		assertTrue(Recipes.uses("Ranarr weed").stream().anyMatch(r -> r.output.equals("Ranarr potion (unf)")));
	}

	@Test
	public void toolsAreUsedByTheirKinds()
	{
		assertTrue(Recipes.uses("Rune axe").stream().anyMatch(r -> r.how.equals("chop") && r.output.equals("Yew logs")));
		assertTrue(Recipes.uses("Willow logs").stream().anyMatch(r -> r.use().equals("Burn it")));
		assertFalse(Recipes.forItem("Magic logs").isEmpty());
		assertTrue(Recipes.uses("Not an item").isEmpty());
	}

	@Test
	public void longUseListsKeepTheNextFewLevels()
	{
		List<Recipes.Recipe> uses = new java.util.ArrayList<>();
		for (int level = 1; level <= 30; level++)
		{
			uses.add(Recipes.parse("Item " + level + "\tSmithing:" + level + "\t1*Bar\t\t"));
		}
		Requirement group = Recipes.usedFor(uses, s -> 10);
		assertEquals("Used for (10 of 30)", group.getLabel());
		assertEquals("Make Item 6", group.getChildren().get(0).getLabel());
		assertEquals("Smithing 6", group.getChildren().get(0).getNote());
		assertTrue(group.getChildren().get(4).isMet());
		assertFalse(group.getChildren().get(5).isMet());
		assertEquals("Make Item 15", group.getChildren().get(9).getLabel());
		assertEquals("Cast Wind Strike", Recipes.parse("\tMagic:1\t1*Air rune\t\t\tcast Wind Strike").use());
	}
}
