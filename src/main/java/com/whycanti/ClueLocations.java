package com.whycanti;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;

/**
 * Items needed to reach clue locations. The Clue Scroll plugin only mentions these in its solution text ("You require a
 * Diving Apparatus and a Fishbowl Helmet"), so they're listed here, found by that text or by a coordinate clue's spot.
 */
final class ClueLocations
{
	/** Any of some item ids, at least a quantity of them in one place. */
	static final class Ids implements ItemRequirement
	{
		private final String name;
		private final int quantity;
		private final int[] ids;

		Ids(String name, int quantity, int... ids)
		{
			this.name = name;
			this.quantity = quantity;
			this.ids = ids;
		}

		@Override
		public boolean fulfilledBy(int itemId)
		{
			return Arrays.stream(ids).anyMatch(id -> id == itemId);
		}

		@Override
		public boolean fulfilledBy(Item[] items)
		{
			int count = 0;
			for (Item item : items)
			{
				if (item != null && fulfilledBy(item.getId()))
				{
					count += item.getQuantity();
				}
			}
			return count >= quantity;
		}

		@Override
		public String getCollectiveName(Client client)
		{
			return quantity > 1 ? quantity + " x " + name : name;
		}
	}

	/** One location's needs. If the player has the skill level, the items aren't needed. */
	static final class Entry
	{
		final String phrase;
		final WorldPoint point;
		final boolean wear;
		final List<Ids> items;
		final Skill skill;
		final int level;

		private Entry(String phrase, WorldPoint point, boolean wear, Skill skill, int level, Ids... items)
		{
			this.phrase = phrase;
			this.point = point;
			this.wear = wear;
			this.skill = skill;
			this.level = level;
			this.items = Arrays.asList(items);
		}
	}

	private static final Ids PET_ROCK = new Ids("Pet rock", 1, ItemID.VT_USELESS_ROCK);
	private static final Ids RUNE_THROWNAXE = new Ids("Rune thrownaxe", 1, ItemID.RUNE_THROWNAXE);

	private static final List<Entry> ENTRIES = Arrays.asList(
		new Entry("mogre camp", null, true, null, 0,
			new Ids("Fishbowl helmet", 1, ItemID.HUNDRED_PIRATE_DIVING_HELMET),
			new Ids("Diving apparatus", 1, ItemID.HUNDRED_PIRATE_DIVING_BACKPACK)),
		new Entry("shadow dungeon", null, true, null, 0,
			new Ids("Ring of visibility or ring of shadows", 1, ItemID.FD_RING_VISIBILITY, ItemID.RING_OF_SHADOWS)),
		new Entry("bring a sled", null, false, null, 0,
			new Ids("Sled", 1, ItemID.TROLLROMANCE_TOBOGGON, ItemID.TROLLROMANCE_TOBOGGON_WAXED)),
		new Entry("head chef in cooks' guild", null, true, null, 0,
			new Ids("Chef's hat, Varrock armour 3 or 4, or Cooking cape", 1, ItemID.CHEFS_HAT,
				ItemID.VARROCK_ARMOUR_HARD, ItemID.VARROCK_ARMOUR_ELITE, ItemID.SKILLCAPE_COOKING, ItemID.SKILLCAPE_COOKING_TRIMMED)),
		new Entry("metal key from tourist trap", null, false, null, 0,
			new Ids("Metal key", 1, ItemID.METAL_KEY)),
		new Entry("viyeldi caves", null, false, Skill.AGILITY, 96,
			new Ids("Unpowered orb", 1, ItemID.STAFFORB),
			new Ids("Lockpick", 1, ItemID.LOCKPICK)),
		new Entry("nardah rug merchant", null, false, null, 0,
			new Ids("Rope", 1, ItemID.ROPE)),
		new Entry("dagannoth kings room", null, false, Skill.AGILITY, 85, PET_ROCK, RUNE_THROWNAXE),
		new Entry(null, new WorldPoint(2538, 3739, 0), false, Skill.AGILITY, 85, PET_ROCK, RUNE_THROWNAXE),
		new Entry(null, new WorldPoint(3822, 3562, 0), true, null, 0,
			new Ids("Ghostspeak amulet", 1, ItemID.AMULET_OF_GHOSTSPEAK, ItemID.AMULET_OF_GHOSTSPEAK_ENCHANTED)),
		new Entry(null, new WorldPoint(3822, 3562, 0), false, null, 0,
			new Ids("Ecto-token", 25, ItemID.ECTOTOKEN))
	);

	private ClueLocations()
	{
	}

	/** The entries for a clue's solution text and spot; either may be null. */
	static List<Entry> find(String text, WorldPoint point)
	{
		String lower = text == null ? null : BlockParser.clean(text).toLowerCase(Locale.ROOT).replace('’', '\'');
		return ENTRIES.stream()
			.filter(e -> (e.phrase != null && lower != null && lower.contains(e.phrase)) || (e.point != null && e.point.equals(point)))
			.collect(Collectors.toList());
	}
}
