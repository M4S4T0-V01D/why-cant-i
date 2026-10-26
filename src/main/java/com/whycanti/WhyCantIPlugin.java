package com.whycanti;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.AgilityShortcut;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Why Can't I?",
	description = "When the game blocks you, shows every requirement you're missing: quests, subquests, skill levels and quest points",
	tags = {"quest", "requirement", "level", "skill", "locked", "blocked", "explain", "why", "thieving", "agility", "drops", "sailing"}
)
public class WhyCantIPlugin extends Plugin
{
	/** Dialogue and message box text widgets that can tell you what you need. */
	private static final int[] DIALOG_TEXT = {
		InterfaceID.Messagebox.TEXT, InterfaceID.Objectbox.TEXT, InterfaceID.ObjectboxDouble.TEXT,
		InterfaceID.Graphicbox.TEXT, InterfaceID.ChatLeft.TEXT,
	};
	/** How many ticks after clicking Wear or Wield a block message is taken to be about that item. */
	private static final int EQUIP_TICKS = 3;
	/** The same text from a chat message and its dialogue box only counts once within this many ticks. */
	private static final int REPEAT_TICKS = 2;
	private static final String MENU_OPTION = "Why can't I?";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private WhyCantIConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ExplainerOverlay overlay;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private PluginManager pluginManager;

	private ExplainerPanel panel;
	private NavigationButton navButton;
	private GameData data;
	private BlockParser parser;
	private final Map<Integer, String> dialogText = new HashMap<>();
	private int equipItem = -1;
	private int equipTick;
	private String lastText;
	private int lastTick;
	private ClueScroll lastClue;
	/** The clue step worked out by this plugin, from the clue's text or item, for when the Clue Scroll plugin is off. */
	private ClueScroll ownClue;
	/** The clue scroll item in the inventory that ownClue belongs to, or -1. */
	private int ownClueItem = -1;
	/** The clue explanation in the popup, kept up to date as items go on and come off. */
	private Explanation clueShown;
	/** The bank's items, once it's been opened this session. */
	private Item[] bank;

	@Provides
	WhyCantIConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WhyCantIConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		mouseManager.registerMouseListener(overlay.mouse);
		panel = new ExplainerPanel(this, config);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Why Can't I?")
			.icon(icon)
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		clientThread.invokeLater(this::loadData);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		mouseManager.unregisterMouseListener(overlay.mouse);
		overlay.dismiss();
		clientToolbar.removeNavigation(navButton);
		panel = null;
		data = null;
		parser = null;
		dialogText.clear();
		equipItem = -1;
		lastClue = null;
		ownClue = null;
		ownClueItem = -1;
		clueShown = null;
		bank = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (data == null && (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.LOGGED_IN))
		{
			clientThread.invokeLater(this::loadData);
		}
	}

	private boolean loadData()
	{
		if (data != null)
		{
			return true;
		}
		if (client.getGameState().ordinal() < GameState.LOGIN_SCREEN.ordinal())
		{
			return false;
		}
		data = GameData.load(client);
		parser = new BlockParser(data);
		log.debug("Loaded requirements for {} quests", data.questsByName().size());
		return true;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (config.detectMessages() && (type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.SPAM
			|| type == ChatMessageType.ENGINE || type == ChatMessageType.MESBOX))
		{
			blocked(event.getMessage());
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!"Examine".equals(event.getOption()))
		{
			return;
		}
		MenuEntry examine = event.getMenuEntry();
		MenuAction type = examine.getType();
		if (type == MenuAction.EXAMINE_OBJECT || type == MenuAction.EXAMINE_NPC)
		{
			if (config.objectMenu())
			{
				addThingMenu(event, examine, type == MenuAction.EXAMINE_NPC);
			}
			return;
		}
		if (!config.itemMenu())
		{
			return;
		}
		Widget widget = examine.getWidget();
		int itemId = event.getItemId() > 0 ? event.getItemId() : widget != null ? widget.getItemId() : -1;
		if (itemId <= 0 || widget == null || (config.itemMenuNeeded() && !menuNeeded(itemId)))
		{
			return;
		}
		client.getMenu().createMenuEntry(-1)
			.setOption(MENU_OPTION)
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(e -> clientThread.invokeLater(() -> explainItem(itemId)));
	}

	/** What an object or NPC is to this plugin: what you can thieve, mine or salvage from it, or an agility obstacle. */
	private static final class Looked
	{
		String name;
		List<Sources.Thing> things = Collections.emptyList();
		AgilityShortcut shortcut;
		Agility.Course course;
		Gates.Gate gate;

		boolean any()
		{
			return !things.isEmpty() || shortcut != null || course != null || gate != null;
		}

		/** The levels it needs. */
		List<GameData.SkillLevel> levels()
		{
			List<GameData.SkillLevel> levels = new ArrayList<>();
			things.stream().filter(t -> t.skill != null).forEach(t -> levels.add(t.skill));
			if (shortcut != null)
			{
				levels.add(new GameData.SkillLevel(Skill.AGILITY, shortcut.getLevel()));
			}
			if (course != null)
			{
				levels.add(new GameData.SkillLevel(Skill.AGILITY, course.level));
			}
			return levels;
		}
	}

	/** Adds the menu option to an object's or NPC's Examine if this plugin knows anything about it. */
	private void addThingMenu(MenuEntryAdded event, MenuEntry examine, boolean npc)
	{
		int id = npc ? -1 : event.getIdentifier();
		if (npc)
		{
			NPC n = examine.getNpc();
			NPCComposition c = n == null ? null : n.getTransformedComposition();
			id = c == null ? -1 : c.getId();
		}
		if (id < 0)
		{
			return;
		}
		Looked looked = lookAt(npc, id);
		if (looked == null || !looked.any())
		{
			return;
		}
		if (config.itemMenuNeeded() && looked.levels().stream().noneMatch(s -> client.getRealSkillLevel(s.getSkill()) < s.getLevel())
			&& (looked.gate == null || data == null || gate(looked.gate).stream().noneMatch(Requirement::anyUnmet)))
		{
			return;
		}
		boolean isNpc = npc;
		int thingId = id;
		client.getMenu().createMenuEntry(-1)
			.setOption(MENU_OPTION)
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(e -> clientThread.invokeLater(() -> explainThing(isNpc, thingId)));
	}

	/** Looks an object or NPC up by its ID, or its name and options if the wiki doesn't have its ID. Client thread. */
	private Looked lookAt(boolean npc, int id)
	{
		Looked looked = new Looked();
		String[] actions;
		int[] ids;
		if (npc)
		{
			NPCComposition c = client.getNpcDefinition(id);
			if (c == null)
			{
				return null;
			}
			looked.name = c.getName();
			actions = c.getActions();
			ids = new int[]{id};
		}
		else
		{
			ObjectComposition c = client.getObjectDefinition(id);
			if (c == null)
			{
				return null;
			}
			ObjectComposition shown = c.getImpostorIds() != null ? c.getImpostor() : null;
			ObjectComposition use = shown != null ? shown : c;
			looked.name = use.getName();
			actions = use.getActions();
			ids = shown != null ? new int[]{shown.getId(), id} : new int[]{id};
		}
		if (looked.name == null || looked.name.isEmpty() || "null".equals(looked.name))
		{
			return null;
		}
		for (int i : ids)
		{
			looked.things = Sources.find(npc, i, looked.name, actions);
			if (!looked.things.isEmpty())
			{
				break;
			}
		}
		if (!npc)
		{
			Player me = client.getLocalPlayer();
			WorldPoint at = me == null ? null : me.getWorldLocation();
			for (int i : ids)
			{
				looked.shortcut = looked.shortcut != null ? looked.shortcut : Agility.shortcut(i, at);
				looked.gate = looked.gate != null ? looked.gate : Gates.gate(i);
			}
			if (looked.shortcut == null && looked.gate == null && at != null)
			{
				looked.course = Agility.course(at.getRegionID(), ids[0], looked.name, actions);
			}
		}
		return looked;
	}

	/** Pins what an object or NPC needs and gives. Client thread. */
	private void explainThing(boolean npc, int id)
	{
		if (data == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Looked looked = lookAt(npc, id);
		if (looked == null || !looked.any())
		{
			return;
		}
		RequirementTree tree = new RequirementTree(data, new ClientPlayerState(client));
		List<Requirement> list = new ArrayList<>();
		if (looked.shortcut != null)
		{
			list.add(agility("Agility shortcut", looked.shortcut.getLevel(), looked.shortcut.getDescription(), tree));
		}
		if (looked.course != null)
		{
			list.add(agility(looked.course.name, looked.course.level, null, tree));
		}
		if (looked.gate != null)
		{
			List<Requirement> needs = gate(looked.gate);
			Requirement group = new Requirement(Requirement.Kind.GROUP, looked.gate.name,
				needs.stream().anyMatch(Requirement::anyUnmet) ? Requirement.Status.NOT_MET : Requirement.Status.MET, null, -1);
			group.getChildren().addAll(needs);
			list.add(group);
		}
		List<Sources.Thing> things = looked.things;
		for (Sources.Thing t : things.subList(0, Math.min(things.size(), Sources.VARIANTS)))
		{
			list.add(Sources.explain(t, things.size() > 1, tree, items(), this::itemName));
		}
		if (things.size() > Sources.VARIANTS)
		{
			list.add(new Requirement(Requirement.Kind.GROUP, "And " + (things.size() - Sources.VARIANTS) + " more kinds of "
				+ looked.name, Requirement.Status.MET, null, -1));
		}
		Explanation explanation = new Explanation(looked.name, list);
		pin(explanation, hint(explanation));
	}

	/** What a ladder, door or gate needs. Client thread. */
	private List<Requirement> gate(Gates.Gate gate)
	{
		ClientPlayerState player = new ClientPlayerState(client);
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		int[] ids = worn == null ? null : Arrays.stream(worn.getItems()).mapToInt(Item::getId).toArray();
		return Gates.explain(gate, data, new RequirementTree(data, player), player, ids, this::itemName);
	}

	/** An agility shortcut or course and the level it needs. */
	private static Requirement agility(String label, int level, String note, RequirementTree tree)
	{
		BlockParser.Block block = new BlockParser.Block(Collections.emptyList(),
			Collections.singletonList(new GameData.SkillLevel(Skill.AGILITY, level)), 0, 0);
		List<Requirement> needs = tree.explain(null, block, null, Collections.emptyList()).getRequirements();
		Requirement group = new Requirement(Requirement.Kind.GROUP, label,
			needs.stream().anyMatch(Requirement::anyUnmet) ? Requirement.Status.NOT_MET : Requirement.Status.MET, note, -1);
		group.getChildren().addAll(needs);
		return group;
	}

	private String itemName(int id)
	{
		return itemManager.getItemComposition(id).getMembersName();
	}

	/** Whether an item is worth the menu option: a clue scroll, or something you lack a level to wear or make. */
	private boolean menuNeeded(int itemId)
	{
		ItemComposition item = itemManager.getItemComposition(itemManager.canonicalize(itemId));
		String name = item.getMembersName().toLowerCase(Locale.ROOT);
		if (name.contains("clue scroll") || name.contains("challenge scroll"))
		{
			return true;
		}
		for (GameData.SkillLevel s : GameData.wearRequirements(item))
		{
			if (client.getRealSkillLevel(s.getSkill()) < s.getLevel())
			{
				return true;
			}
		}
		return Recipes.cantMake(Recipes.forItem(item.getMembersName()), client::getRealSkillLevel);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		if (event.getItemId() > 0 && ("Wear".equals(option) || "Wield".equals(option) || "Equip".equals(option)))
		{
			equipItem = event.getItemId();
			equipTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		ClueScroll clue = clue();
		if (clue != lastClue)
		{
			lastClue = clue;
			showClue(clue);
		}
		if (!config.detectDialogs())
		{
			return;
		}
		for (int id : DIALOG_TEXT)
		{
			Widget widget = client.getWidget(id);
			String text = widget == null || widget.isHidden() ? null : widget.getText();
			if (text == null || text.isEmpty())
			{
				dialogText.remove(id);
			}
			else if (!text.equals(dialogText.put(id, text)))
			{
				blocked(text);
			}
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.TRAIL_CLUETEXT)
		{
			// reading the clue again shows what it needs again; the text says which step it is
			clientThread.invokeLater(() ->
			{
				readClueText();
				showClue(clue());
			});
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.BANK)
		{
			bank = event.getItemContainer().getItems();
		}
		if (id == InventoryID.INV)
		{
			trackClueItem(event.getItemContainer().getItems());
		}
		else if ((id == InventoryID.WORN || id == InventoryID.INV) && clueShown != null && overlay.current() == clueShown)
		{
			Explanation updated = ClueExplainer.explain(lastClue, cluePlugin(), client, items());
			if (updated != null)
			{
				clueShown = updated;
				overlay.update(updated);
			}
		}
	}

	/** The current clue step: the Clue Scroll plugin's if it's on and knows it, otherwise the one worked out here. */
	private ClueScroll clue()
	{
		if (!config.detectClues())
		{
			return null;
		}
		ClueScrollPlugin plugin = cluePlugin();
		ClueScroll clue = plugin == null ? null : plugin.getClue();
		return clue != null ? clue : ownClue;
	}

	/** Works out the step from the open clue's text. Client thread. */
	private void readClueText()
	{
		Widget text = client.getWidget(InterfaceID.TrailCluetext.TEXT);
		if (text == null || text.getText() == null)
		{
			return;
		}
		ClueScroll clue = ClueFinder.forText(text.getText());
		if (clue != null)
		{
			ownClue = clue;
		}
	}

	/** Forgets the step when the clue scroll leaves the inventory, and learns it from items that say which step they are. */
	private void trackClueItem(Item[] items)
	{
		int found = -1;
		for (Item item : items)
		{
			if (item != null && item.getId() > 0 && isClue(item.getId()))
			{
				found = item.getId();
				break;
			}
		}
		if (found == ownClueItem)
		{
			return;
		}
		ownClueItem = found;
		ownClue = found == -1 ? null : ClueFinder.forItemId(found);
	}

	private boolean isClue(int itemId)
	{
		String name = itemManager.getItemComposition(itemId).getMembersName().toLowerCase(Locale.ROOT);
		return name.startsWith("clue scroll") || name.startsWith("challenge scroll");
	}

	/** RuneLite's Clue Scroll plugin, if it's on; it knows a few more steps, but isn't needed. */
	private ClueScrollPlugin cluePlugin()
	{
		for (Plugin p : pluginManager.getPlugins())
		{
			if (p instanceof ClueScrollPlugin && pluginManager.isPluginEnabled(p))
			{
				return (ClueScrollPlugin) p;
			}
		}
		return null;
	}

	private ClueExplainer.Items items()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		return new ClueExplainer.Items(worn == null ? null : worn.getItems(), inventory == null ? null : inventory.getItems(), bank);
	}

	/** Shows what a clue step needs, if it needs items you aren't wearing or carrying. Client thread. */
	private void showClue(ClueScroll clue)
	{
		if (clue == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Explanation explanation = ClueExplainer.explain(clue, cluePlugin(), client, items());
		if (explanation == null || !explanation.anyUnmet())
		{
			return;
		}
		clueShown = explanation;
		overlay.show(explanation, null);
		ExplainerPanel p = panel;
		SwingUtilities.invokeLater(() ->
		{
			if (p != null)
			{
				p.add(explanation, null);
			}
		});
	}

	/** Pins what an item needs from its right-click option: a clue scroll's current step, or what it takes to wear, make, grow or gather it and what it's used for. Client thread. */
	private void explainItem(int itemId)
	{
		if (data == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		ItemComposition item = itemManager.getItemComposition(itemManager.canonicalize(itemId));
		String name = item.getMembersName();
		String lower = name.toLowerCase(Locale.ROOT);
		Explanation explanation;
		String hint = null;
		if (lower.contains("clue scroll") || lower.contains("challenge scroll"))
		{
			ClueScroll clue = clue();
			if (clue == null && config.detectClues())
			{
				clue = ClueFinder.forItemId(itemId);
			}
			explanation = clue == null ? null : ClueExplainer.explain(clue, cluePlugin(), client, items());
			if (explanation != null)
			{
				clueShown = explanation;
			}
			else
			{
				String why = !config.detectClues() ? "turn on this plugin's clue option to see what it needs"
					: clue == null ? "read it first so the step is known"
					: "this step doesn't need any items";
				explanation = new Explanation(name, Collections.singletonList(
					new Requirement(Requirement.Kind.CLUE_ITEM, why, Requirement.Status.MET, null, -1)));
			}
		}
		else
		{
			List<GameData.SkillLevel> skills = GameData.wearRequirements(item);
			List<Recipes.Recipe> recipes = Recipes.forItem(name);
			List<Recipes.Recipe> uses = Recipes.uses(name);
			RequirementTree tree = new RequirementTree(data, new ClientPlayerState(client));
			BlockParser.Block none = new BlockParser.Block(Collections.emptyList(), Collections.emptyList(), 0, 0);
			boolean found = !Sources.forItem(name).isEmpty();
			List<Sources.Thing> opened = Sources.opened(name);
			if (recipes.isEmpty() && uses.isEmpty() && !found && opened.isEmpty())
			{
				explanation = skills.isEmpty()
					? new Explanation(name, Collections.singletonList(
						new Requirement(Requirement.Kind.GROUP, "No levels needed", Requirement.Status.MET, null, -1)))
					: tree.explain(name, none, name, skills);
			}
			else
			{
				List<Requirement> list = new ArrayList<>();
				if (!skills.isEmpty())
				{
					Requirement worn = tree.explain(name, none, name, skills).getRequirements().get(0);
					Requirement wear = new Requirement(Requirement.Kind.GROUP, "Wear it", worn.getStatus(), null, -1);
					wear.getChildren().addAll(worn.getChildren());
					list.add(wear);
				}
				for (List<Recipes.Recipe> ways : Recipes.byHow(recipes).values())
				{
					list.add(Recipes.explain(Recipes.best(ways, client::getRealSkillLevel), ways.size(), tree, items(),
						this::itemName));
				}
				if (found)
				{
					list.add(Sources.getIt(name, client::getRealSkillLevel));
				}
				for (Sources.Thing t : opened)
				{
					list.add(Sources.explain(t, false, tree, items(), this::itemName));
				}
				if (!uses.isEmpty())
				{
					list.add(Recipes.usedFor(uses, client::getRealSkillLevel));
				}
				explanation = new Explanation(name, list);
			}
			hint = hint(explanation);
		}
		pin(explanation, hint);
	}

	/** Pins an explanation in the popup and adds it to the side panel. */
	private void pin(Explanation explanation, String hint)
	{
		overlay.pin(explanation, hint);
		ExplainerPanel p = panel;
		SwingUtilities.invokeLater(() ->
		{
			if (p != null)
			{
				p.add(explanation, hint);
			}
		});
	}

	/** Handles text that may say you're missing something. Client thread. */
	private void blocked(String text)
	{
		if (parser == null)
		{
			return;
		}
		String message = BlockParser.clean(text);
		int tick = client.getTickCount();
		if (message.equals(lastText) && tick - lastTick <= REPEAT_TICKS)
		{
			return;
		}
		boolean equip = config.detectEquip() && equipItem > 0 && tick - equipTick <= EQUIP_TICKS && BlockParser.gated(message);
		BlockParser.Block block = parser.parse(message);
		if (block == null && !equip)
		{
			return;
		}
		lastText = message;
		lastTick = tick;
		int item = equip ? equipItem : -1;
		if (equip)
		{
			equipItem = -1;
		}
		BlockParser.Block found = block != null ? block : new BlockParser.Block(Collections.emptyList(), Collections.emptyList(), 0, 0);
		// quest states come from running a script, which can't happen inside another script's callbacks
		clientThread.invokeLater(() -> explain(message, found, item));
	}

	private void explain(String message, BlockParser.Block block, int itemId)
	{
		if (data == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		String itemName = null;
		List<GameData.SkillLevel> itemSkills = Collections.emptyList();
		if (itemId > 0)
		{
			ItemComposition item = itemManager.getItemComposition(itemId);
			itemName = item.getMembersName();
			itemSkills = GameData.wearRequirements(item);
		}
		Explanation explanation = new RequirementTree(data, new ClientPlayerState(client)).explain(message, block, itemName, itemSkills);
		if (!explanation.anyUnmet())
		{
			return;
		}
		String hint = hint(explanation);
		overlay.show(explanation, hint);
		ExplainerPanel p = panel;
		SwingUtilities.invokeLater(() ->
		{
			if (p != null)
			{
				p.add(explanation, hint);
			}
		});
	}

	/** Looks up a quest by name for the side panel. Swing thread. */
	void lookUp(String text)
	{
		String query = text.trim().toLowerCase(Locale.ROOT);
		if (query.isEmpty())
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			ExplainerPanel p = panel;
			if (data == null || p == null)
			{
				return;
			}
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				SwingUtilities.invokeLater(() -> p.showMessage("Log in to check quest requirements."));
				return;
			}
			GameData.Quest quest = data.questNamed(query);
			if (quest == null)
			{
				quest = data.questsByName().stream()
					.filter(q -> q.getName().toLowerCase(Locale.ROOT).contains(query))
					.reduce((a, b) -> b) // shortest name, since they're longest first
					.orElse(null);
			}
			if (quest == null)
			{
				SwingUtilities.invokeLater(() -> p.showMessage("No quest called \"" + text.trim() + "\"."));
				return;
			}
			Requirement root = new RequirementTree(data, new ClientPlayerState(client)).quest(quest.getId());
			Explanation explanation = new Explanation(quest.getName(), Collections.singletonList(root));
			String hint = hint(explanation);
			SwingUtilities.invokeLater(() -> p.display(explanation, hint));
		});
	}

	void openPanel()
	{
		SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
	}

	/** How to get help with the missing quests. */
	private String hint(Explanation explanation)
	{
		if (!missingQuest(explanation.getRequirements()))
		{
			return null;
		}
		if (config.questHelperHint() && pluginManager.getPlugins().stream()
			.anyMatch(p -> "Quest Helper".equals(p.getName()) && pluginManager.isPluginEnabled(p)))
		{
			return "Quest Helper: right-click a quest in your quest list and pick Quest Helper to start its guide.";
		}
		return "Click a quest in the side panel to open its wiki page.";
	}

	private static boolean missingQuest(List<Requirement> requirements)
	{
		for (Requirement r : requirements)
		{
			if (r.getKind() == Requirement.Kind.QUEST && !r.isMet() || missingQuest(r.getChildren()))
			{
				return true;
			}
		}
		return false;
	}
}
