package com.whycanti;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;

/** The popup above the chatbox listing what a block needs. */
class ExplainerOverlay extends Overlay
{
	private static final int PAD = 6;
	private static final int INDENT = 12;
	private static final int GLYPH = 9;
	private static final int MAX_WIDTH = 330;
	/** The close button's size, in the title's top-right corner. */
	private static final int CLOSE = 9;
	private static final Color TITLE = new Color(0xff981f);
	private static final Color NOTE = new Color(0xa0a0a0);

	/** A tree line flattened for drawing. */
	private static class Line
	{
		final Requirement requirement;
		final int depth;

		Line(Requirement requirement, int depth)
		{
			this.requirement = requirement;
			this.depth = depth;
		}
	}

	private static final String TARGET = "Why Can't I?";

	private final WhyCantIPlugin plugin;
	private final WhyCantIConfig config;
	private volatile Explanation explanation;
	private volatile String hint;
	private volatile Instant shown;
	private volatile boolean pinned;
	/** Whether the right-click menu reads Unpin, or null before it's built. */
	private Boolean menuPinned;
	/** Where the close button was last drawn, relative to the popup; null when nothing is showing. */
	private volatile Rectangle closeBox;
	private volatile boolean closeHovered;

	/** Dismisses the popup when its close button is clicked, without the click reaching the game. */
	final MouseAdapter mouse = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			if (event.getButton() == MouseEvent.BUTTON1 && overClose(event))
			{
				dismiss();
				event.consume();
			}
			return event;
		}

		@Override
		public MouseEvent mouseMoved(MouseEvent event)
		{
			closeHovered = overClose(event);
			return event;
		}
	};

	@Inject
	ExplainerOverlay(WhyCantIPlugin plugin, WhyCantIConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
		setPriority(PRIORITY_HIGH);
		setPinned(false);
		syncMenu();
	}

	void show(Explanation explanation, String hint)
	{
		this.explanation = explanation;
		this.hint = hint;
		this.shown = Instant.now();
	}

	/** Shows an explanation that stays until it's dismissed or unpinned. */
	void pin(Explanation explanation, String hint)
	{
		show(explanation, hint);
		setPinned(true);
	}

	/** Pins or unpins what's showing; unpinning restarts the timer so it fades like a fresh popup. */
	void setPinned(boolean pinned)
	{
		this.pinned = pinned;
		if (!pinned)
		{
			shown = Instant.now();
		}
	}

	/** Rebuilds the right-click menu to read Pin or Unpin, since its labels can't change. Client thread, where it's read. */
	void syncMenu()
	{
		boolean pinned = this.pinned;
		if (menuPinned != null && menuPinned == pinned)
		{
			return;
		}
		menuPinned = pinned;
		getMenuEntries().clear();
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Dismiss", TARGET, e -> dismiss());
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, pinned ? "Unpin" : "Pin", TARGET, e -> setPinned(!this.pinned));
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Open panel", TARGET, e -> plugin.openPanel());
	}

	/** What's showing, or null. */
	Explanation current()
	{
		return explanation;
	}

	/** Replaces what's showing, like the ticks changing as items go on, without restarting the timer. */
	void update(Explanation explanation)
	{
		if (this.explanation != null)
		{
			this.explanation = explanation;
		}
	}

	void dismiss()
	{
		explanation = null;
		setPinned(false);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		syncMenu();
		Explanation e = explanation;
		if (e == null || !config.showPopup())
		{
			closeBox = null;
			return null;
		}
		int seconds = config.popupSeconds();
		if (!pinned && seconds > 0 && Duration.between(shown, Instant.now()).getSeconds() >= seconds)
		{
			explanation = null;
			closeBox = null;
			return null;
		}

		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int lineHeight = fm.getHeight();

		List<Line> lines = new ArrayList<>();
		for (Requirement r : e.getRequirements())
		{
			flatten(r, 0, lines);
		}
		int max = config.popupLines();
		int hidden = Math.max(0, lines.size() - max);
		if (hidden > 0)
		{
			lines = lines.subList(0, max);
		}

		List<String> title = wrap("Why can't I? " + e.getMessage(), fm, MAX_WIDTH - CLOSE - PAD);
		List<String> hintLines = hint == null ? new ArrayList<>() : wrap(hint, fm, MAX_WIDTH);
		int width = 0;
		for (String t : title)
		{
			width = Math.max(width, fm.stringWidth(t) + CLOSE + PAD);
		}
		for (String t : hintLines)
		{
			width = Math.max(width, fm.stringWidth(t));
		}
		for (Line l : lines)
		{
			width = Math.max(width, l.depth * INDENT + GLYPH + 4 + fm.stringWidth(text(l.requirement)));
		}
		int rows = title.size() + lines.size() + hintLines.size() + (hidden > 0 ? 1 : 0);
		int w = width + PAD * 2;
		int h = rows * lineHeight + PAD * 2 + 2;

		g.setColor(ComponentConstants.STANDARD_BACKGROUND_COLOR);
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0x40, 0x3a, 0x30));
		g.drawRect(0, 0, w - 1, h - 1);
		Rectangle close = new Rectangle(w - PAD - CLOSE, PAD + (lineHeight - CLOSE) / 2, CLOSE, CLOSE);
		closeBox = close;
		closeButton(g, close, closeHovered);

		int y = PAD + fm.getAscent();
		for (String t : title)
		{
			shadowed(g, t, PAD, y, TITLE);
			y += lineHeight;
		}
		y += 2;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		for (Line l : lines)
		{
			Requirement r = l.requirement;
			int x = PAD + l.depth * INDENT;
			Color colour = colour(r.getStatus());
			glyph(g, r.getStatus(), x, y - fm.getAscent() + (lineHeight - GLYPH) / 2, colour);
			x += GLYPH + 4;
			shadowed(g, r.getLabel(), x, y, r.isMet() ? colour : Color.WHITE);
			if (r.getNote() != null)
			{
				shadowed(g, " (" + r.getNote() + ")", x + fm.stringWidth(r.getLabel()), y, NOTE);
			}
			y += lineHeight;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		if (hidden > 0)
		{
			shadowed(g, "+" + hidden + " more in the side panel", PAD, y, NOTE);
			y += lineHeight;
		}
		for (String t : hintLines)
		{
			shadowed(g, t, PAD, y, NOTE);
			y += lineHeight;
		}
		return new Dimension(w, h);
	}

	private void flatten(Requirement r, int depth, List<Line> out)
	{
		if (config.hideMet() && !r.anyUnmet())
		{
			return;
		}
		out.add(new Line(r, depth));
		for (Requirement child : r.getChildren())
		{
			flatten(child, depth + 1, out);
		}
	}

	private static String text(Requirement r)
	{
		return r.getNote() == null ? r.getLabel() : r.getLabel() + " (" + r.getNote() + ")";
	}

	private Color colour(Requirement.Status status)
	{
		switch (status)
		{
			case MET:
				return config.metColour();
			case PARTLY:
				return config.partlyColour();
			default:
				return config.missingColour();
		}
	}

	/** Whether a mouse event is on the close button of a showing popup. */
	private boolean overClose(MouseEvent event)
	{
		Rectangle close = closeBox;
		Rectangle bounds = getBounds();
		if (explanation == null || close == null || bounds == null)
		{
			return false;
		}
		return close.contains(event.getX() - bounds.x, event.getY() - bounds.y);
	}

	private static void closeButton(Graphics2D g, Rectangle r, boolean hovered)
	{
		Stroke stroke = g.getStroke();
		Object antialias = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(hovered ? Color.WHITE : NOTE);
		g.drawLine(r.x + 1, r.y + 1, r.x + r.width - 2, r.y + r.height - 2);
		g.drawLine(r.x + r.width - 2, r.y + 1, r.x + 1, r.y + r.height - 2);
		g.setStroke(stroke);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
	}

	/** A tick, cross or dot, drawn since the game font has no such symbols. */
	private static void glyph(Graphics2D g, Requirement.Status status, int x, int y, Color colour)
	{
		Stroke stroke = g.getStroke();
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(colour);
		switch (status)
		{
			case MET:
				g.drawPolyline(new int[]{x + 1, x + 3, x + 8}, new int[]{y + 5, y + 8, y + 1}, 3);
				break;
			case PARTLY:
				g.fillOval(x + 2, y + 2, GLYPH - 4, GLYPH - 4);
				break;
			default:
				g.drawLine(x + 1, y + 1, x + 7, y + 7);
				g.drawLine(x + 7, y + 1, x + 1, y + 7);
		}
		g.setStroke(stroke);
	}

	private static void shadowed(Graphics2D g, String s, int x, int y, Color colour)
	{
		g.setColor(Color.BLACK);
		g.drawString(s, x + 1, y + 1);
		g.setColor(colour);
		g.drawString(s, x, y);
	}

	private static List<String> wrap(String s, FontMetrics fm, int width)
	{
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : s.split(" "))
		{
			if (line.length() > 0 && fm.stringWidth(line + " " + word) > width)
			{
				out.add(line.toString());
				line.setLength(0);
			}
			if (line.length() > 0)
			{
				line.append(' ');
			}
			line.append(word);
		}
		if (line.length() > 0)
		{
			out.add(line.toString());
		}
		return out;
	}
}
