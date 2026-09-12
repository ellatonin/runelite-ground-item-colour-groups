package com.ella.grounditemscolourgroups;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.grounditems.GroundItemsConfig;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
@PluginDescriptor(
	name = "Ground Item Colour Groups",
	description = "Adds a side panel listing every item you've given a custom colour in the Ground Items "
		+ "plugin, grouped by that colour, and lets you add, recolour or remove entries from the panel",
	tags = { "ground", "items", "colour", "color", "highlight", "panel", "grounditems" }
)
public class GroundItemColourGroupsPlugin extends Plugin implements PanelCallbacks
{
	/**
	 * Mirrors the private HIGHLIGHT_COLOR_PREFIX constant in the built-in GroundItemsPlugin, which stores each
	 * manually-coloured item's colour under the config key "highlight_" + itemId within GroundItemsConfig.GROUP.
	 * That constant isn't public, so it's duplicated here rather than depended on.
	 */
	private static final String HIGHLIGHT_KEY_PREFIX = "highlight_";

	/**
	 * This plugin's own config group, used only to remember the colour of items whose whole colour
	 * group has been bulk-disabled from the panel. Disabling a group unsets its items' entries from
	 * GroundItemsConfig (so the game stops highlighting them) but stashes the colour here under
	 * DISABLED_KEY_PREFIX + itemId, so re-enabling the group can restore the exact same colour.
	 */
	private static final String OWN_CONFIG_GROUP = "grounditemcolourgroups";
	private static final String DISABLED_KEY_PREFIX = "disabled_";

	@Inject
	private ConfigManager configManager;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ColorPickerManager colourPickerManager;

	private GroundItemColourGroupsPanel panel;
	private NavigationButton navButton;

	/**
	 * Every item known to the client, tradeable or not, keyed by its canonical (un-noted,
	 * un-placeholdered) id. Built lazily on first use since it requires a full scan of the item
	 * cache on the client thread; the result doesn't change during a session so it's cached here.
	 * Volatile since it's written on the client thread and read on the EDT.
	 */
	private volatile List<SearchItem> itemIndex;

	@Override
	protected void startUp()
	{
		panel = new GroundItemColourGroupsPanel(this);

		navButton = NavigationButton.builder()
			.tooltip("Ground Item Colour Groups")
			.icon(createIcon())
			.priority(9)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		refresh();
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		panel = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (GroundItemsConfig.GROUP.equals(event.getGroup()))
		{
			refresh();
		}
	}

	@Override
	public void refresh()
	{
		clientThread.invoke(() ->
		{
			List<ColourGroup> groups = buildGroups();
			SwingUtilities.invokeLater(() -> panel.showGroups(groups));
		});
	}

	@Override
	public void addNewGroup()
	{
		RuneliteColorPicker picker = colourPickerManager.create(panel, Color.WHITE, "New colour group", false);
		picker.setOnClose(colour ->
		{
			if (colour != null)
			{
				openItemSearch(colour);
			}
		});
		picker.setVisible(true);
	}

	@Override
	public void addToGroup(Color groupColour)
	{
		openItemSearch(groupColour);
	}

	@Override
	public void removeItem(int itemId)
	{
		configManager.unsetConfiguration(GroundItemsConfig.GROUP, HIGHLIGHT_KEY_PREFIX + itemId);
		configManager.unsetConfiguration(OWN_CONFIG_GROUP, DISABLED_KEY_PREFIX + itemId);
	}

	@Override
	public void changeItemColour(int itemId, Color currentColour)
	{
		RuneliteColorPicker picker = colourPickerManager.create(panel, currentColour, "Item colour", false);
		picker.setOnClose(colour ->
		{
			if (colour != null)
			{
				boolean disabled = configManager.getConfiguration(OWN_CONFIG_GROUP, DISABLED_KEY_PREFIX + itemId, Color.class) != null;
				if (disabled)
				{
					configManager.setConfiguration(OWN_CONFIG_GROUP, DISABLED_KEY_PREFIX + itemId, colour);
				}
				else
				{
					configManager.setConfiguration(GroundItemsConfig.GROUP, HIGHLIGHT_KEY_PREFIX + itemId, colour);
				}
			}
		});
		picker.setVisible(true);
	}

	@Override
	public void setGroupEnabled(Color groupColour, boolean enabled)
	{
		if (enabled)
		{
			moveGroup(groupColour, OWN_CONFIG_GROUP, DISABLED_KEY_PREFIX, GroundItemsConfig.GROUP, HIGHLIGHT_KEY_PREFIX);
		}
		else
		{
			moveGroup(groupColour, GroundItemsConfig.GROUP, HIGHLIGHT_KEY_PREFIX, OWN_CONFIG_GROUP, DISABLED_KEY_PREFIX);
		}
		refresh();
	}

	/**
	 * Moves every item whose colour under {@code fromGroup}/{@code fromPrefix} equals
	 * {@code colour} over to {@code toGroup}/{@code toPrefix}. Used to bulk enable/disable a whole
	 * colour group at once while keeping track of which colour each item should return to.
	 */
	private void moveGroup(Color colour, String fromGroup, String fromPrefix, String toGroup, String toPrefix)
	{
		String prefix = fromGroup + "." + fromPrefix;
		List<String> keys = configManager.getConfigurationKeys(prefix);

		for (String wholeKey : keys)
		{
			int itemId = parseItemId(wholeKey, prefix);
			if (itemId < 0)
			{
				continue;
			}

			Color itemColour = configManager.getConfiguration(fromGroup, fromPrefix + itemId, Color.class);
			if (itemColour == null || !itemColour.equals(colour))
			{
				continue;
			}

			configManager.setConfiguration(toGroup, toPrefix + itemId, itemColour);
			configManager.unsetConfiguration(fromGroup, fromPrefix + itemId);
		}
	}

	private void openItemSearch(Color colour)
	{
		List<SearchItem> cached = itemIndex;
		if (cached != null)
		{
			showItemSearchDialog(colour, cached);
			return;
		}

		panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		clientThread.invoke(() ->
		{
			List<SearchItem> items = buildItemIndex();
			itemIndex = items;

			SwingUtilities.invokeLater(() ->
			{
				panel.setCursor(Cursor.getDefaultCursor());
				showItemSearchDialog(colour, items);
			});
		});
	}

	private void showItemSearchDialog(Color colour, List<SearchItem> items)
	{
		Window owner = SwingUtilities.windowForComponent(panel);
		ItemSearchDialog dialog = new ItemSearchDialog(owner, items, selected ->
			configManager.setConfiguration(GroundItemsConfig.GROUP, HIGHLIGHT_KEY_PREFIX + selected.getId(), colour));
		dialog.setVisible(true);
	}

	/**
	 * Scans every item id known to the client - tradeable or not - collapsing noted/placeholder
	 * variants to their canonical item via {@link ItemManager#canonicalize}, the same approach
	 * RuneLite's own item search chatbox uses. Must run on the client thread since item
	 * compositions are read from the game's item cache.
	 */
	private List<SearchItem> buildItemIndex()
	{
		Map<Integer, String> byId = new LinkedHashMap<>();

		int count = client.getItemCount();
		for (int id = 0; id < count; id++)
		{
			int canonicalId = itemManager.canonicalize(id);
			if (byId.containsKey(canonicalId))
			{
				continue;
			}

			String name = itemManager.getItemComposition(canonicalId).getName();
			if (name == null || name.equalsIgnoreCase("null"))
			{
				continue;
			}

			byId.put(canonicalId, name);
		}

		List<SearchItem> items = new ArrayList<>(byId.size());
		byId.forEach((id, name) -> items.add(new SearchItem(id, name)));
		items.sort(Comparator.comparing(SearchItem::getName, String.CASE_INSENSITIVE_ORDER));
		return items;
	}

	private List<ColourGroup> buildGroups()
	{
		Map<Color, List<ColouredGroundItem>> enabledItems = collectItems(GroundItemsConfig.GROUP, HIGHLIGHT_KEY_PREFIX);
		Map<Color, List<ColouredGroundItem>> disabledItems = collectItems(OWN_CONFIG_GROUP, DISABLED_KEY_PREFIX);

		Map<Color, Boolean> enabledByColour = new TreeMap<>(GroundItemColourGroupsPlugin::compareByHue);
		enabledItems.keySet().forEach(colour -> enabledByColour.put(colour, true));
		disabledItems.keySet().forEach(colour -> enabledByColour.putIfAbsent(colour, false));

		List<ColourGroup> groups = new ArrayList<>();
		for (Map.Entry<Color, Boolean> entry : enabledByColour.entrySet())
		{
			Color colour = entry.getKey();

			List<ColouredGroundItem> items = new ArrayList<>();
			items.addAll(enabledItems.getOrDefault(colour, Collections.emptyList()));
			items.addAll(disabledItems.getOrDefault(colour, Collections.emptyList()));
			items.sort(Comparator.comparing(ColouredGroundItem::getName, String.CASE_INSENSITIVE_ORDER));

			groups.add(new ColourGroup(colour, entry.getValue(), items));
		}

		return groups;
	}

	/**
	 * Reads every {@code keyPrefix + itemId} entry under {@code configGroup}, resolving each item's
	 * colour and display info, and groups the results by colour. Used for both the live
	 * GroundItemsConfig entries and this plugin's own store of bulk-disabled items.
	 */
	private Map<Color, List<ColouredGroundItem>> collectItems(String configGroup, String keyPrefix)
	{
		String prefix = configGroup + "." + keyPrefix;
		List<String> keys = configManager.getConfigurationKeys(prefix);

		Map<Color, List<ColouredGroundItem>> grouped = new LinkedHashMap<>();

		for (String wholeKey : keys)
		{
			int itemId = parseItemId(wholeKey, prefix);
			if (itemId < 0)
			{
				continue;
			}

			Color colour = configManager.getConfiguration(configGroup, keyPrefix + itemId, Color.class);
			if (colour == null)
			{
				continue;
			}

			String name;
			try
			{
				name = itemManager.getItemComposition(itemId).getName();
			}
			catch (Exception e)
			{
				name = "Item " + itemId;
			}

			AsyncBufferedImage image = itemManager.getImage(itemId);

			grouped.computeIfAbsent(colour, c -> new ArrayList<>())
				.add(new ColouredGroundItem(itemId, name, image));
		}

		return grouped;
	}

	private static int parseItemId(String wholeKey, String prefix)
	{
		try
		{
			return Integer.parseInt(wholeKey.substring(prefix.length()));
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	private static int compareByHue(Color a, Color b)
	{
		float[] hsbA = Color.RGBtoHSB(a.getRed(), a.getGreen(), a.getBlue(), null);
		float[] hsbB = Color.RGBtoHSB(b.getRed(), b.getGreen(), b.getBlue(), null);

		int hue = Float.compare(hsbA[0], hsbB[0]);
		if (hue != 0)
		{
			return hue;
		}

		int saturation = Float.compare(hsbB[1], hsbA[1]);
		if (saturation != 0)
		{
			return saturation;
		}

		int brightness = Float.compare(hsbB[2], hsbA[2]);
		if (brightness != 0)
		{
			return brightness;
		}

		return Integer.compare(a.getRGB(), b.getRGB());
	}

	private static BufferedImage createIcon()
	{
		int size = 16;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int half = size / 2;
		g.setColor(new Color(0xE0, 0x4B, 0x4B));
		g.fillRect(0, 0, half, half);
		g.setColor(new Color(0x4B, 0xAE, 0x4B));
		g.fillRect(half, 0, half, half);
		g.setColor(new Color(0x4B, 0x7B, 0xE0));
		g.fillRect(0, half, half, half);
		g.setColor(new Color(0xE0, 0xC9, 0x4B));
		g.fillRect(half, half, half, half);

		g.dispose();
		return image;
	}
}
