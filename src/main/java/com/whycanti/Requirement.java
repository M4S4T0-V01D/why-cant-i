package com.whycanti;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/** One line of an explanation: a requirement, whether it's met, and what it needs in turn. */
@Getter
class Requirement
{
	enum Kind
	{
		QUEST, SKILL, QUEST_POINTS, COMBAT, ITEM,
		/** An item a clue step needs; often a group like "Any god cape", so it has no wiki page. */
		CLUE_ITEM,
		/** A heading like "Make it" over the lines it needs. */
		GROUP
	}

	enum Status
	{
		MET,
		NOT_MET,
		/** Started quests, and levels only reached with a boost that may not count. */
		PARTLY
	}

	private final Kind kind;
	private final String label;
	private final Status status;
	/** Extra text after the label, like "you: 65" or "in progress", or null. */
	private final String note;
	/** The quest row id for quest lines, otherwise -1. */
	private final int quest;
	private final List<Requirement> children = new ArrayList<>();

	Requirement(Kind kind, String label, Status status, String note, int quest)
	{
		this.kind = kind;
		this.label = label;
		this.status = status;
		this.note = note;
		this.quest = quest;
	}

	boolean isMet()
	{
		return status == Status.MET;
	}

	/** Whether this or anything under it is not met. */
	boolean anyUnmet()
	{
		if (status != Status.MET)
		{
			return true;
		}
		for (Requirement child : children)
		{
			if (child.anyUnmet())
			{
				return true;
			}
		}
		return false;
	}

	/** The wiki page for this line, or null. */
	String wikiPage()
	{
		// materials read "5 x Runite bar"
		return kind == Kind.QUEST || kind == Kind.ITEM ? label.replaceFirst("^\\d+ x ", "") : null;
	}
}
