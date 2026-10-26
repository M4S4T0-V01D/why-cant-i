package com.whycanti;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;

/**
 * The logged in player's state. Client thread only, and outside script callbacks, since quest states are read by
 * running the game's quest status script. Quest states are cached, so make a new one for each explanation.
 */
class ClientPlayerState implements PlayerState
{
	/** [proc,quest_status_get](dbrow), the script RuneLite's own Quest.getState runs. */
	private static final int QUEST_STATUS_GET = 4029;

	private final Client client;
	private final Map<Integer, QuestState> states = new HashMap<>();

	ClientPlayerState(Client client)
	{
		this.client = client;
	}

	@Override
	public int realLevel(Skill skill)
	{
		return client.getRealSkillLevel(skill);
	}

	@Override
	public int boostedLevel(Skill skill)
	{
		return client.getBoostedSkillLevel(skill);
	}

	@Override
	public QuestState questState(int quest)
	{
		return states.computeIfAbsent(quest, q ->
		{
			client.runScript(QUEST_STATUS_GET, q);
			switch (client.getIntStack()[0])
			{
				case 2:
					return QuestState.FINISHED;
				case 1:
					return QuestState.NOT_STARTED;
				default:
					return QuestState.IN_PROGRESS;
			}
		});
	}

	@Override
	public int questPoints()
	{
		return client.getVarpValue(VarPlayerID.QP);
	}

	@Override
	public int combatLevel()
	{
		Player player = client.getLocalPlayer();
		return player == null ? 0 : player.getCombatLevel();
	}
}
