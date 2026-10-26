package com.whycanti;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.LinkBrowser;

/** The side panel: recent blocks, a quest lookup, and the full requirement tree. */
class ExplainerPanel extends PluginPanel
{
	private static final int HISTORY = 15;
	private static final String WIKI = "https://oldschool.runescape.wiki/w/";

	private final WhyCantIPlugin plugin;
	private final WhyCantIConfig config;
	private final IconTextField search = new IconTextField();
	private final DefaultComboBoxModel<String> historyModel = new DefaultComboBoxModel<>();
	private final JComboBox<String> historyBox = new JComboBox<>(historyModel);
	private final List<Explanation> history = new ArrayList<>();
	private final JLabel message = new JLabel();
	private final JPanel tree = new JPanel();
	private final JLabel hint = new JLabel();
	private boolean updatingHistory;

	ExplainerPanel(WhyCantIPlugin plugin, WhyCantIConfig config)
	{
		super(false);
		this.plugin = plugin;
		this.config = config;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);

		JLabel title = new JLabel("Why Can't I?");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(LEFT_ALIGNMENT);
		top.add(title);
		top.add(gap());

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(PANEL_WIDTH - 16, 30));
		search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		search.setToolTipText("Type a quest name and press Enter to see what it needs");
		search.addActionListener(e -> plugin.lookUp(search.getText()));
		search.setAlignmentX(LEFT_ALIGNMENT);
		top.add(search);
		top.add(gap());

		JLabel recent = small("Recent blocks");
		top.add(recent);
		historyBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		historyBox.setAlignmentX(LEFT_ALIGNMENT);
		historyBox.addActionListener(e ->
		{
			int i = historyBox.getSelectedIndex();
			if (!updatingHistory && i >= 0 && i < history.size())
			{
				display(history.get(i), null);
			}
		});
		top.add(historyBox);
		top.add(gap());

		message.setForeground(new Color(0xff981f));
		message.setAlignmentX(LEFT_ALIGNMENT);
		top.add(message);
		top.add(gap());
		add(top, BorderLayout.NORTH);

		tree.setLayout(new BoxLayout(tree, BoxLayout.Y_AXIS));
		tree.setOpaque(false);
		JPanel treeWrap = new JPanel(new BorderLayout());
		treeWrap.setOpaque(false);
		treeWrap.add(tree, BorderLayout.NORTH);
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hint.setBorder(new EmptyBorder(8, 0, 0, 0));
		treeWrap.add(hint, BorderLayout.CENTER);
		add(treeWrap, BorderLayout.CENTER);

		showEmpty();
	}

	/** Adds a block to the recent list and shows it. Swing thread. */
	void add(Explanation explanation, String hintText)
	{
		if (!history.isEmpty() && history.get(0).getMessage().equals(explanation.getMessage()))
		{
			// the same block again, like reading a clue twice: refresh it rather than listing it twice
			history.set(0, explanation);
		}
		else
		{
			history.add(0, explanation);
		}
		if (history.size() > HISTORY)
		{
			history.remove(history.size() - 1);
		}
		updatingHistory = true;
		historyModel.removeAllElements();
		for (Explanation e : history)
		{
			historyModel.addElement(e.getMessage());
		}
		historyBox.setSelectedIndex(0);
		updatingHistory = false;
		display(explanation, hintText);
	}

	/** Shows an explanation without adding it to the recent list, like a quest lookup. Swing thread. */
	void display(Explanation explanation, String hintText)
	{
		message.setText(html(explanation.getMessage(), PANEL_WIDTH - 20));
		tree.removeAll();
		for (Requirement r : explanation.getRequirements())
		{
			addRows(r, 0);
		}
		hint.setText(hintText == null ? "" : html(hintText, PANEL_WIDTH - 20));
		revalidate();
		repaint();
	}

	void showMessage(String text)
	{
		message.setText(html(text, PANEL_WIDTH - 20));
		tree.removeAll();
		hint.setText("");
		revalidate();
		repaint();
	}

	private void showEmpty()
	{
		message.setText(html("When the game stops you from doing something, what you're missing shows up here.", PANEL_WIDTH - 20));
	}

	private void addRows(Requirement r, int depth)
	{
		JLabel row = new JLabel();
		row.setIcon(new ImageIcon(glyph(r.getStatus())));
		row.setIconTextGap(5);
		row.setHorizontalAlignment(SwingConstants.LEFT);
		row.setBorder(new EmptyBorder(2, 4 + depth * 12, 2, 0));
		row.setAlignmentX(LEFT_ALIGNMENT);
		String note = r.getNote() == null ? "" : " <span style='color:#a0a0a0'>(" + escape(r.getNote()) + ")</span>";
		row.setText("<html>" + escape(r.getLabel()) + note + "</html>");
		row.setForeground(r.isMet() ? colour(r.getStatus()) : Color.WHITE);
		String page = r.wikiPage();
		if (page != null)
		{
			row.setToolTipText("Open " + page + " on the OSRS Wiki");
			row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			row.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					LinkBrowser.browse(WIKI + URLEncoder.encode(page.replace(' ', '_'), StandardCharsets.UTF_8));
				}
			});
		}
		tree.add(row);
		for (Requirement child : r.getChildren())
		{
			addRows(child, depth + 1);
		}
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

	private BufferedImage glyph(Requirement.Status status)
	{
		BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(colour(status));
		switch (status)
		{
			case MET:
				g.drawPolyline(new int[]{1, 4, 9}, new int[]{5, 8, 2}, 3);
				break;
			case PARTLY:
				g.fillOval(2, 2, 6, 6);
				break;
			default:
				g.drawLine(2, 2, 8, 8);
				g.drawLine(8, 2, 2, 8);
		}
		g.dispose();
		return image;
	}

	private static JLabel small(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JPanel gap()
	{
		JPanel gap = new JPanel();
		gap.setOpaque(false);
		gap.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
		gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
		gap.setAlignmentX(LEFT_ALIGNMENT);
		return gap;
	}

	private static String html(String text, int width)
	{
		return "<html><div style='width:" + width + "px'>" + escape(text) + "</div></html>";
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
