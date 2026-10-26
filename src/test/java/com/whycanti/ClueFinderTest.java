package com.whycanti;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class ClueFinderTest
{
	@Test
	public void coordinatesNorthEast()
	{
		// 5 minutes is 2.67 tiles, 13 minutes 6.93
		assertEquals(new WorldPoint(2479, 3164, 0),
			ClueFinder.coordinates("00 degrees 05 minutes north 01 degrees 13 minutes east"));
	}

	@Test
	public void coordinatesSouthWest()
	{
		assertEquals(new WorldPoint(2440 - 231, 3161 - 72, 0),
			ClueFinder.coordinates("02 degrees 15 minutes south 07 degrees 13 minutes west"));
	}

	@Test
	public void coordinatesFromWidgetText()
	{
		assertEquals(new WorldPoint(2479, 3164, 0),
			ClueFinder.coordinates("00 degrees 05 minutes north<br>01 degrees 13 minutes east"
				.replace("<br>", " ")));
	}

	@Test
	public void notCoordinates()
	{
		assertNull(ClueFinder.coordinates("dig near some degrees and minutes"));
		assertNull(ClueFinder.forItemId(-1));
	}
}
