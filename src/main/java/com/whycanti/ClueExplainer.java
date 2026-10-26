package com.whycanti;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.CoordinateClue;
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue;
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.plugins.cluescrolls.clues.FaloTheBardClue;
import net.runelite.client.plugins.cluescrolls.clues.HotColdClue;
import net.runelite.client.plugins.cluescrolls.clues.MapClue;
import net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue;
import net.runelite.client.plugins.cluescrolls.clues.ThreeStepCrypticClue;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;
import net.runelite.client.plugins.cluescrolls.clues.item.SlotLimitationRequirement;

/**
 * What the current clue step needs, from RuneLite's clue data: the items for emote clues, skill challenges and
 * Falo the Bard, and a spade for digging, ticked off against what's worn, carried and banked.
 */
class ClueExplainer
{
	/** Where the player's items are. The bank is null until it's been opened this session. */
	static class Items
	{
		final Item[] worn;
		final Item[] inventory;
		final Item[] bank;

		Items(Item[] worn, Item[] inventory, Item[] bank)
		{
			this.worn = worn == null ? new Item[0] : worn;
			this.inventory = inventory == null ? new Item[0] : inventory;
			this.bank = bank;
		}
	}

	private static final ItemRequirement SPADE = new ClueLocations.Ids("Spade", 1, ItemID.SPADE);

	/** The explanation for a clue step, or null if it doesn't need anything we can check. */
	static Explanation explain(ClueScroll clue, ClueScrollPlugin plugin, Client client, Items items)
	{
		String text;
		ItemRequirement[] requirements;
		boolean wear;
		if (clue instanceof EmoteClue)
		{
			EmoteClue emote = (EmoteClue) clue;
			text = emote.getText();
			requirements = emote.getItemRequirements();
			wear = true;
		}
		else if (clue instanceof SkillChallengeClue)
		{
			SkillChallengeClue challenge = (SkillChallengeClue) clue;
			text = challenge.getChallenge();
			requirements = challenge.getItemRequirements();
			wear = challenge.isRequireEquip();
		}
		else if (clue instanceof FaloTheBardClue)
		{
			FaloTheBardClue falo = (FaloTheBardClue) clue;
			text = falo.getText();
			requirements = falo.getItemRequirements();
			wear = false;
		}
		else if (clue != null)
		{
			return location(clue, plugin, client, items);
		}
		else
		{
			return null;
		}

		List<Requirement> list = new ArrayList<>();
		for (ItemRequirement r : requirements == null ? new ItemRequirement[0] : requirements)
		{
			list.add(item(r.getCollectiveName(client), r, wear, items));
		}
		return list.isEmpty() ? null : new Explanation("Clue: " + BlockParser.clean(text), list);
	}

	/** Map, coordinate, cryptic and hot-cold steps: a spade if it's a dig, and whatever the location needs. */
	private static Explanation location(ClueScroll clue, ClueScrollPlugin plugin, Client client, Items items)
	{
		StringBuilder solution = new StringBuilder();
		WorldPoint point = null;
		if (clue instanceof MapClue)
		{
			solution.append(((MapClue) clue).getDescription());
		}
		else if (clue instanceof CrypticClue)
		{
			solution.append(solution((CrypticClue) clue, plugin));
		}
		else if (clue instanceof ThreeStepCrypticClue)
		{
			for (Map.Entry<CrypticClue, Boolean> step : ((ThreeStepCrypticClue) clue).getClueSteps())
			{
				if (!step.getValue())
				{
					solution.append(solution(step.getKey(), plugin)).append(' ');
				}
			}
		}
		else if (clue instanceof HotColdClue)
		{
			solution.append(((HotColdClue) clue).getSolution());
		}
		else if (clue instanceof CoordinateClue)
		{
			point = ((CoordinateClue) clue).getLocation(plugin);
		}
		String text = solution.toString().trim();

		List<Requirement> list = new ArrayList<>();
		if (clue.isRequiresSpade())
		{
			list.add(item("Spade", SPADE, false, items));
		}
		for (ClueLocations.Entry entry : ClueLocations.find(text, point))
		{
			int level = entry.skill == null ? 0 : client.getRealSkillLevel(entry.skill);
			if (entry.skill != null && level >= entry.level)
			{
				continue;
			}
			for (ClueLocations.Ids ids : entry.items)
			{
				list.add(item(ids.getCollectiveName(client), ids, entry.wear, items));
			}
			if (entry.skill != null)
			{
				list.add(new Requirement(Requirement.Kind.SKILL, "or " + entry.level + " " + entry.skill.getName() + " instead",
					Requirement.Status.NOT_MET, "you: " + level, -1));
			}
		}
		if (list.isEmpty())
		{
			return null;
		}
		String message = !text.isEmpty() ? text : point != null ? "the spot for your coordinate clue" : "dig for your clue";
		return new Explanation("Clue: " + BlockParser.clean(message), list);
	}

	/** A cryptic step's answer. A few answers come from the Clue Scroll plugin's state, which is missing when it's off. */
	private static String solution(CrypticClue clue, ClueScrollPlugin plugin)
	{
		try
		{
			String solution = clue.getSolution(plugin);
			return solution == null ? "" : solution;
		}
		catch (RuntimeException e)
		{
			return "";
		}
	}

	/** One item requirement, ticked off: worn (or carried, if it needn't be worn), carried, banked or missing. */
	static Requirement item(String name, ItemRequirement r, boolean wear, Items items)
	{
		return item(Requirement.Kind.CLUE_ITEM, name, r, wear, items);
	}

	static Requirement item(Requirement.Kind kind, String name, ItemRequirement r, boolean wear, Items items)
	{
		Requirement.Status status;
		String note;
		if (r instanceof SlotLimitationRequirement)
		{
			// "Nothing on your head" and the like; only the worn items' slots mean anything
			boolean met = r.fulfilledBy(items.worn);
			return new Requirement(kind, name, met ? Requirement.Status.MET : Requirement.Status.NOT_MET,
				met ? null : "take it off", -1);
		}
		if (r.fulfilledBy(items.worn))
		{
			status = Requirement.Status.MET;
			note = wear ? "worn" : null;
		}
		else if (r.fulfilledBy(items.inventory))
		{
			status = wear ? Requirement.Status.PARTLY : Requirement.Status.MET;
			note = wear ? "in your inventory, wear it" : null;
		}
		else if (items.bank != null && r.fulfilledBy(items.bank))
		{
			status = Requirement.Status.PARTLY;
			note = "in your bank";
		}
		else
		{
			status = Requirement.Status.NOT_MET;
			note = items.bank == null ? "not carried; open your bank to check it too" : "you don't have it";
		}
		return new Requirement(kind, name, status, note, -1);
	}
}
