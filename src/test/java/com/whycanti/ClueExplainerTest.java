package com.whycanti;

import static org.junit.Assert.assertEquals;
import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;
import net.runelite.client.plugins.cluescrolls.clues.item.SingleItemRequirement;
import org.junit.Test;

public class ClueExplainerTest
{
	private static final ItemRequirement WHIP = new SingleItemRequirement(ItemID.ABYSSAL_WHIP);
	private static final Item[] HAS_WHIP = {new Item(ItemID.ABYSSAL_WHIP, 1)};
	private static final Item[] NOTHING = {};

	private static Requirement check(boolean wear, Item[] worn, Item[] inventory, Item[] bank)
	{
		return ClueExplainer.item("Abyssal whip", WHIP, wear, new ClueExplainer.Items(worn, inventory, bank));
	}

	@Test
	public void wornIsMet()
	{
		Requirement r = check(true, HAS_WHIP, NOTHING, null);
		assertEquals(Requirement.Status.MET, r.getStatus());
		assertEquals("worn", r.getNote());
	}

	@Test
	public void emoteItemsMustBeWorn()
	{
		Requirement r = check(true, NOTHING, HAS_WHIP, null);
		assertEquals(Requirement.Status.PARTLY, r.getStatus());
		assertEquals("in your inventory, wear it", r.getNote());
	}

	@Test
	public void carriedIsEnoughWhenNotWorn()
	{
		assertEquals(Requirement.Status.MET, check(false, NOTHING, HAS_WHIP, null).getStatus());
	}

	@Test
	public void bank()
	{
		Requirement r = check(true, NOTHING, NOTHING, HAS_WHIP);
		assertEquals(Requirement.Status.PARTLY, r.getStatus());
		assertEquals("in your bank", r.getNote());
	}

	@Test
	public void missing()
	{
		assertEquals("not carried; open your bank to check it too", check(true, NOTHING, NOTHING, null).getNote());
		Requirement r = check(true, NOTHING, NOTHING, NOTHING);
		assertEquals(Requirement.Status.NOT_MET, r.getStatus());
		assertEquals("you don't have it", r.getNote());
	}
}
