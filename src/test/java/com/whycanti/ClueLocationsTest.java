package com.whycanti;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.List;
import net.runelite.api.Item;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class ClueLocationsTest
{
	@Test
	public void mogreCampNeedsDivingGearWorn()
	{
		List<ClueLocations.Entry> found = ClueLocations.find(
			"In the Mogre Camp, near Port Khazard. You require a Diving Apparatus and a Fishbowl Helmet", null);
		assertEquals(1, found.size());
		assertTrue(found.get(0).wear);
		assertEquals(2, found.get(0).items.size());
	}

	@Test
	public void coordinateCluesBySpot()
	{
		assertEquals(2, ClueLocations.find(null, new WorldPoint(3822, 3562, 0)).size());
		assertTrue(ClueLocations.find(null, new WorldPoint(3000, 3000, 0)).isEmpty());
		assertTrue(ClueLocations.find("Dig next to the Varrock fountain", null).isEmpty());
	}

	@Test
	public void quantityAddsUpAcrossStacks()
	{
		ClueLocations.Ids tokens = new ClueLocations.Ids("Ecto-token", 25, ItemID.ECTOTOKEN);
		assertFalse(tokens.fulfilledBy(new Item[]{new Item(ItemID.ECTOTOKEN, 20)}));
		assertTrue(tokens.fulfilledBy(new Item[]{new Item(ItemID.ECTOTOKEN, 20), null, new Item(ItemID.ECTOTOKEN, 5)}));
	}
}
