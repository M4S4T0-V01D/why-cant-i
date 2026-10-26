package com.whycanti;

import java.util.Locale;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
import net.runelite.client.plugins.cluescrolls.clues.CipherClue;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.CoordinateClue;
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue;
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.plugins.cluescrolls.clues.FairyRingClue;
import net.runelite.client.plugins.cluescrolls.clues.FaloTheBardClue;
import net.runelite.client.plugins.cluescrolls.clues.HotColdClue;
import net.runelite.client.plugins.cluescrolls.clues.MapClue;
import net.runelite.client.plugins.cluescrolls.clues.MusicClue;
import net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue;
import net.runelite.client.plugins.cluescrolls.clues.ThreeStepCrypticClue;
import net.runelite.client.util.Text;

/**
 * Works out a clue step from its text or item, using RuneLite's built-in clue data, so the Clue Scroll plugin needn't
 * be on. Follows the same order that plugin uses.
 */
@Slf4j
final class ClueFinder
{
	private ClueFinder()
	{
	}

	/** The step a clue scroll item is, for clues whose item says which step they are; otherwise null. */
	static ClueScroll forItemId(int itemId)
	{
		if (itemId <= 0)
		{
			// steps without their own item are stored as -1
			return null;
		}
		return first(
			() -> MapClue.forItemId(itemId),
			() -> CoordinateClue.forItemId(itemId),
			() -> EmoteClue.forItemId(itemId),
			() -> CrypticClue.forItemId(itemId),
			() -> CipherClue.forItemId(itemId),
			() -> AnagramClue.forItemId(itemId),
			() -> MusicClue.forItemId(itemId),
			() -> FairyRingClue.forItemId(itemId));
	}

	/** The step a clue's text describes, or null. */
	static ClueScroll forText(String raw)
	{
		String text = Text.sanitizeMultilineText(raw).toLowerCase(Locale.ROOT);
		if (text.startsWith("i'd like to hear some music."))
		{
			return MusicClue.forText(raw);
		}
		if (text.contains("degrees") && text.contains("minutes"))
		{
			WorldPoint point = coordinates(text);
			return point == null ? null : CoordinateClue.forLocation(point);
		}
		return first(
			() -> AnagramClue.forText(null, text),
			() -> CipherClue.forText(text),
			() -> CrypticClue.forText(text),
			() -> EmoteClue.forText(text),
			() -> FairyRingClue.forText(text),
			() -> FaloTheBardClue.forText(text),
			() -> HotColdClue.forText(text, -1),
			() -> SkillChallengeClue.forText(text, raw),
			() -> ThreeStepCrypticClue.forText(text, raw));
	}

	/** "00 degrees 05 minutes north 01 degrees 13 minutes east" as a tile; the scale's origin is the Observatory. */
	static WorldPoint coordinates(String text)
	{
		String[] words = text.replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+");
		if (words.length < 10 || !words[1].startsWith("degree") || !words[3].startsWith("minute"))
		{
			return null;
		}
		try
		{
			int y = Integer.parseInt(words[0]) * 32 + (int) Math.round(Integer.parseInt(words[2]) / 1.875);
			int x = Integer.parseInt(words[5]) * 32 + (int) Math.round(Integer.parseInt(words[7]) / 1.875);
			return new WorldPoint(2440 + ("west".equals(words[9]) ? -x : x), 3161 + ("south".equals(words[4]) ? -y : y), 0);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	@SafeVarargs
	private static ClueScroll first(Supplier<? extends ClueScroll>... lookups)
	{
		for (Supplier<? extends ClueScroll> lookup : lookups)
		{
			try
			{
				ClueScroll clue = lookup.get();
				if (clue != null)
				{
					return clue;
				}
			}
			catch (RuntimeException e)
			{
				// some lookups expect the Clue Scroll plugin; without it they can't match anyway
				log.debug("Clue lookup failed", e);
			}
		}
		return null;
	}
}
