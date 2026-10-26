package com.whycanti;

import java.util.List;
import lombok.Value;

/** Why something was blocked: the game's message and the requirements behind it. */
@Value
class Explanation
{
	/** The game's message, without tags. */
	String message;
	List<Requirement> requirements;

	boolean anyUnmet()
	{
		for (Requirement r : requirements)
		{
			if (r.anyUnmet())
			{
				return true;
			}
		}
		return false;
	}
}
