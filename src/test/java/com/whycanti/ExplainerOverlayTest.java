package com.whycanti;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import javax.swing.JPanel;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import org.junit.Test;

public class ExplainerOverlayTest
{
	private static List<String> options(ExplainerOverlay overlay)
	{
		return overlay.getMenuEntries().stream().map(OverlayMenuEntry::getOption).collect(Collectors.toList());
	}

	@Test
	public void pinnedTipOffersUnpin()
	{
		ExplainerOverlay overlay = new ExplainerOverlay(null, null);
		assertEquals(List.of("Dismiss", "Pin", "Open panel"), options(overlay));

		overlay.pin(null, null);
		overlay.syncMenu();
		assertEquals(List.of("Dismiss", "Unpin", "Open panel"), options(overlay));

		overlay.setPinned(false);
		overlay.syncMenu();
		assertEquals(List.of("Dismiss", "Pin", "Open panel"), options(overlay));
	}

	@Test
	public void closeButtonDismissesPinnedTip()
	{
		ExplainerOverlay overlay = new ExplainerOverlay(null, new WhyCantIConfig()
		{
		});
		overlay.pin(new Explanation("Rune platebody", Collections.singletonList(
			new Requirement(Requirement.Kind.GROUP, "Defence 40", Requirement.Status.NOT_MET, null, -1))), null);
		Graphics2D g = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
		Dimension size = overlay.render(g);
		assertNotNull(size);

		// A click in the middle of the popup leaves it, one on the close button in its top-right corner dismisses it.
		assertEquals(false, click(overlay, 20, size.height - 8).isConsumed());
		assertNotNull(overlay.current());
		assertEquals(true, click(overlay, size.width - 10, 10).isConsumed());
		assertNull(overlay.current());
	}

	private static MouseEvent click(ExplainerOverlay overlay, int x, int y)
	{
		MouseEvent event = new MouseEvent(new JPanel(), MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1);
		return overlay.mouse.mousePressed(event);
	}
}
