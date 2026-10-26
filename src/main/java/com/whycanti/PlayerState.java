package com.whycanti;

import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/** What the player has, as far as requirements go. */
interface PlayerState
{
	int realLevel(Skill skill);

	int boostedLevel(Skill skill);

	/** The state of a quest by its quest table row id. */
	QuestState questState(int quest);

	int questPoints();

	int combatLevel();
}
