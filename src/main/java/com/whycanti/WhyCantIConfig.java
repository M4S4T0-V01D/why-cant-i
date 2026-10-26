package com.whycanti;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(WhyCantIConfig.GROUP)
public interface WhyCantIConfig extends Config
{
	String GROUP = "whycanti";

	@ConfigSection(name = "Detection", description = "What counts as being blocked", position = 0)
	String detection = "detection";

	@ConfigSection(name = "Popup", description = "The popup above the chatbox", position = 1)
	String popup = "popup";

	@ConfigSection(name = "Colours", description = "Colours for met and missing requirements", position = 2)
	String colours = "colours";

	@ConfigItem(keyName = "detectMessages", name = "Game messages", description = "Explain game messages like \"You need a Mining level of 15\"", section = detection, position = 0)
	default boolean detectMessages()
	{
		return true;
	}

	@ConfigItem(keyName = "detectDialogs", name = "Dialogue boxes", description = "Explain message boxes and NPC dialogue that say you need a quest or level", section = detection, position = 1)
	default boolean detectDialogs()
	{
		return true;
	}

	@ConfigItem(keyName = "detectEquip", name = "Items you can't wear", description = "When you can't wear or wield an item, list every level it needs", section = detection, position = 2)
	default boolean detectEquip()
	{
		return true;
	}

	@ConfigItem(keyName = "detectClues", name = "Clue scroll items", description = "When you read a clue that needs items (emote clues, skill challenges, Falo the Bard, digging, places that need gear), list them and which you have", section = detection, position = 3)
	default boolean detectClues()
	{
		return true;
	}

	@ConfigItem(keyName = "itemMenu", name = "Right-click items", description = "Add \"Why can't I?\" to items' right-click menus: pins what a clue scroll's step needs, or the levels, materials and tools an item needs to wear or make", section = detection, position = 4)
	default boolean itemMenu()
	{
		return true;
	}

	@ConfigItem(keyName = "objectMenu", name = "Right-click objects and NPCs", description = "Add \"Why can't I?\" to stalls, people to pickpocket, chests, rocks, trees, fishing spots, agility shortcuts and courses, shipwrecks and the like: pins the level each needs and what it gives", section = detection, position = 5)
	default boolean objectMenu()
	{
		return true;
	}

	@ConfigItem(keyName = "itemMenuOnlyLacking", name = "Only when needed", description = "Only add the right-click option to clue scrolls, items you don't have the levels to wear or make, and objects and NPCs you don't have the level for; off shows it wherever there's something to say", section = detection, position = 6)
	default boolean itemMenuNeeded()
	{
		return false;
	}

	@ConfigItem(keyName = "showPopup", name = "Show popup", description = "Show the requirements above the chatbox when you're blocked", section = popup, position = 0)
	default boolean showPopup()
	{
		return true;
	}

	@Units(Units.SECONDS)
	@Range(min = 0, max = 300)
	@ConfigItem(keyName = "popupSeconds", name = "Hide after", description = "Seconds before the popup hides, 0 to keep it until dismissed", section = popup, position = 1)
	default int popupSeconds()
	{
		return 20;
	}

	@Range(min = 3, max = 40)
	@ConfigItem(keyName = "popupLines", name = "Max lines", description = "Most requirement lines in the popup; the side panel shows them all", section = popup, position = 2)
	default int popupLines()
	{
		return 12;
	}

	@ConfigItem(keyName = "hideMet", name = "Hide met requirements", description = "Leave out requirements you already have in the popup", section = popup, position = 3)
	default boolean hideMet()
	{
		return false;
	}

	@ConfigItem(keyName = "questHelperHint", name = "Quest Helper hint", description = "When Quest Helper is installed, say how to start a guide for a missing quest", position = 3)
	default boolean questHelperHint()
	{
		return true;
	}

	@ConfigItem(keyName = "metColour", name = "Met", description = "Requirements you have", section = colours, position = 0)
	default Color metColour()
	{
		return new Color(0x3ec43e);
	}

	@ConfigItem(keyName = "missingColour", name = "Missing", description = "Requirements you don't have", section = colours, position = 1)
	default Color missingColour()
	{
		return new Color(0xe0453a);
	}

	@ConfigItem(keyName = "partlyColour", name = "Partly", description = "Quests in progress and levels only reached with a boost", section = colours, position = 2)
	default Color partlyColour()
	{
		return new Color(0xf0c030);
	}
}
