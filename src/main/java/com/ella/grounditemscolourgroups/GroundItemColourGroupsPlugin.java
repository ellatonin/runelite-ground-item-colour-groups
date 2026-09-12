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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.swing.JOptionPane;
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
import net.runelite.client.util.WildcardMatcher;

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
	 * This plugin's own config group, used for state the built-in Ground Items plugin has no notion
	 * of: remembering the colour of bulk-disabled, individually-added items (DISABLED_KEY_PREFIX +
	 * itemId), which whole colour groups are currently toggled off (DISABLED_GROUPS_KEY), and each
	 * colour's wildcard name patterns (PATTERN_KEY_PREFIX + colour hex).
	 */
	private static final String OWN_CONFIG_GROUP = "grounditemcolourgroups";
	private static final String DISABLED_KEY_PREFIX = "disabled_";
	private static final String DISABLED_GROUPS_KEY = "disabledGroupColours";
	private static final String PATTERN_KEY_PREFIX = "patterns_";

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

	/**
	 * Set for the duration of a bulk config write (moving a whole group between enabled/disabled,
	 * or auto-applying a wildcard pattern to newly-matching items) so the resulting flood of
	 * ConfigChanged events - one per item - doesn't each trigger their own full panel refresh. The
	 * caller of the bulk write still triggers exactly one refresh() itself once it's done.
	 */
	private volatile boolean suppressConfigEvents;

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
		if (suppressConfigEvents)
		{
			return;
		}

		// Only react to our own highlight_ keys changing (e.g. via the game's right-click Ground
		// Items menu) - not every other, unrelated Ground Items setting, which would otherwise
		// force a full rebuild (including refetching every displayed item's name/icon) on every
		// keystroke in that plugin's config panel.
		if (GroundItemsConfig.GROUP.equals(event.getGroup()) && event.getKey() != null
			&& event.getKey().startsWith(HIGHLIGHT_KEY_PREFIX))
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
	public void addPatternGroup()
	{
		RuneliteColorPicker picker = colourPickerManager.create(panel, Color.WHITE, "New colour group", false);
		picker.setOnClose(colour ->
		{
			if (colour != null)
			{
				promptForPatterns(colour, "");
			}
		});
		picker.setVisible(true);
	}

	@Override
	public void editPatterns(Color groupColour, String currentPatternsCsv)
	{
		promptForPatterns(groupColour, currentPatternsCsv);
	}

	@Override
	public void removePattern(Color groupColour, String pattern)
	{
		clientThread.invoke(() ->
		{
			String hex = colourHex(groupColour);
			List<String> remainingPatterns = splitPatterns(configManager.getConfiguration(OWN_CONFIG_GROUP, PATTERN_KEY_PREFIX + hex));
			remainingPatterns.remove(pattern);

			applyPatternDiff(groupColour, Collections.singletonList(pattern), remainingPatterns);

			if (remainingPatterns.isEmpty())
			{
				configManager.unsetConfiguration(OWN_CONFIG_GROUP, PATTERN_KEY_PREFIX + hex);
			}
			else
			{
				configManager.setConfiguration(OWN_CONFIG_GROUP, PATTERN_KEY_PREFIX + hex, String.join(", ", remainingPatterns));
			}
			refresh();
		});
	}

	@Override
	public void changePatternColour(Color currentColour, String pattern)
	{
		RuneliteColorPicker picker = colourPickerManager.create(panel, currentColour, "Pattern colour", false);
		picker.setOnClose(newColour ->
		{
			if (newColour != null && !newColour.equals(currentColour))
			{
				movePattern(currentColour, pattern, newColour);
			}
		});
		picker.setVisible(true);
	}

	/**
	 * Moves one pattern from {@code oldColour}'s list to {@code newColour}'s, cleaning up the old
	 * colour's now-orphaned matches the same way removing a pattern does. The matches themselves
	 * pick up {@code newColour} on their own in the very next refresh's pattern reconciliation, since
	 * by then they have no highlight_ entry and the pattern is listed under the new colour.
	 */
	private void movePattern(Color oldColour, String pattern, Color newColour)
	{
		clientThread.invoke(() ->
		{
			String oldHex = colourHex(oldColour);
			List<String> oldColourRemaining = splitPatterns(configManager.getConfiguration(OWN_CONFIG_GROUP, PATTERN_KEY_PREFIX + oldHex));
			oldColourRemaining.remove(pattern);

			applyPatternDiff(oldColour, Collections.singletonList(pattern), oldColourRemaining);

			if (oldColourRemaining.isEmpty())
			{
				configManager.unsetConfiguration(OWN_CONFIG_GROUP, PATTERN_KEY_PREFIX + oldHex);
			}
			else
			{
				configManager.setConfiguration(OWN_CONFIG_GROUP, PATTERN_KEY_PREFIX + oldHex, String.join(", ", oldColourRemaining));
			}

			String newHex = colourHex(newColour);
			List<String> newColourPatterns = splitPatterns(configManager.getConfiguration(OWN_CONFIG_GROUP, PATTERN_KEY_PREFIX + newHex));
			if (!newColourPatterns.contains(pattern))
			{
				newColourPatterns.add(pattern);
			}
			configManager.setConfiguration(OWN_CONFIG_GROUP, PATTERN_KEY_PREFIX + newHex, String.join(", ", newColourPatterns));

			refresh();
		});
	}

	/**
	 * Unsets the highlight_ entry for every item that matched one of {@code oldPatterns} but none of
	 * {@code newPatterns}, so removing or narrowing a pattern doesn't leave its former matches with a
	 * highlight_ entry indistinguishable from a manually-added item - it would otherwise keep
	 * showing up, just as its own individually-managed row, forever. Only touches items still
	 * coloured with this exact colour, so a manual recolour since is left alone; a manually-added
	 * item whose name happens to also match the old pattern is still affected here though - storage
	 * can't tell the two apart, the same trade-off already made when patterns skip re-hydrating
	 * matched items.
	 */
	private void applyPatternDiff(Color colour, List<String> oldPatterns, List<String> newPatterns)
	{
		if (oldPatterns.isEmpty())
		{
			return;
		}

		ensureItemIndexBuilt();
		List<SearchItem> index = itemIndex;
		if (index == null)
		{
			return;
		}

		suppressConfigEvents = true;
		try
		{
			for (SearchItem candidate : index)
			{
				if (!matchesAny(candidate.getName(), oldPatterns) || matchesAny(candidate.getName(), newPatterns))
				{
					continue;
				}

				Color current = configManager.getConfiguration(GroundItemsConfig.GROUP, HIGHLIGHT_KEY_PREFIX + candidate.getId(), Color.class);
				if (colour.equals(current))
				{
					configManager.unsetConfiguration(GroundItemsConfig.GROUP, HIGHLIGHT_KEY_PREFIX + candidate.getId());
				}
			}
		}
		finally
		{
			suppressConfigEvents = false;
		}
	}

	@Override
	public void removeItem(int itemId)
	{
		// Suppressed and refreshed explicitly rather than left to onConfigChanged: if the item is
		// currently in the disabled store, only OWN_CONFIG_GROUP changes, which that listener
		// doesn't watch, and the panel would never notice the removal.
		suppressConfigEvents = true;
		try
		{
			configManager.unsetConfiguration(GroundItemsConfig.GROUP, HIGHLIGHT_KEY_PREFIX + itemId);
			configManager.unsetConfiguration(OWN_CONFIG_GROUP, DISABLED_KEY_PREFIX + itemId);
		}
		finally
		{
			suppressConfigEvents = false;
		}
		refresh();
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
				suppressConfigEvents = true;
				try
				{
					if (disabled)
					{
						configManager.setConfiguration(OWN_CONFIG_GROUP, DISABLED_KEY_PREFIX + itemId, colour);
					}
					else
					{
						configManager.setConfiguration(GroundItemsConfig.GROUP, HIGHLIGHT_KEY_PREFIX + itemId, colour);
					}
				}
				finally
				{
					suppressConfigEvents = false;
				}
				refresh();
			}
		});
		picker.setVisible(true);
	}

	@Override
	public void setGroupEnabled(Color groupColour, boolean enabled)
	{
		clientThread.invoke(() ->
		{
			if (enabled)
			{
				removeDisabledGroupMarker(groupColour);
				// Every disabled_ entry is manually-added (patterns are never stored there, see
				// disableGroup below), so all of them are restored.
				moveGroup(groupColour, OWN_CONFIG_GROUP, DISABLED_KEY_PREFIX, GroundItemsConfig.GROUP,
					HIGHLIGHT_KEY_PREFIX, Collections.emptySet());
			}
			else
			{
				addDisabledGroupMarker(groupColour);
				disableGroup(groupColour);
			}
			refresh();
		});
	}

	private void promptForPatterns(Color colour, String initialText)
	{
		Object input = JOptionPane.showInputDialog(panel,
			"<html><body style='width:260px'>Comma-separated wildcard patterns (use * and ?), matched "
				+ "case-insensitively against item names - e.g. \"Clue scroll*\". Matching items get this "
				+ "colour automatically (shown as a count, not listed individually), including items added to "
				+ "the game later. Leave blank to remove all patterns from this group.</body></html>",
			"Wildcard patterns", JOptionPane.PLAIN_MESSAGE, null, null, initialText);

		if (input != null)
		{
			List<String> oldPatterns = splitPatterns(initialText);
			List<String> newPatterns = splitPatterns(input.toString());

			clientThread.invoke(() ->
			{
				applyPatternDiff(colour, oldPatterns, newPatterns);

				String hex = colourHex(colour);
				if (newPatterns.isEmpty())
				{
					configManager.unsetConfiguration(OWN_CONFIG_GROUP, PATTERN_KEY_PREFIX + hex);
				}
				else
				{
					configManager.setConfiguration(OWN_CONFIG_GROUP, PATTERN_KEY_PREFIX + hex, String.join(", ", newPatterns));
				}
				refresh();
			});
		}
	}

	/**
	 * Disabling a colour group has to actually remove every one of its items' highlight_ entries so
	 * the game stops showing them, but only the manually-added ones need remembering in the
	 * disabled_ store to be restored later - a pattern-matched item's colour is just re-derived from
	 * the pattern the next time the group is re-enabled, so it's simply unset here instead of also
	 * being written to the (potentially hundreds-of-keys) disabled_ store.
	 */
	private void disableGroup(Color colour)
	{
		moveGroup(colour, GroundItemsConfig.GROUP, HIGHLIGHT_KEY_PREFIX, OWN_CONFIG_GROUP, DISABLED_KEY_PREFIX,
			currentPatternMatchIds(colour));
	}

	/**
	 * Moves every item whose colour under {@code fromGroup}/{@code fromPrefix} equals
	 * {@code colour} over to {@code toGroup}/{@code toPrefix}, except ids in {@code unsetOnly} which
	 * are just unset from {@code fromGroup} without being written anywhere - used to drop
	 * pattern-matched items without spending a config key remembering each one individually. The
	 * whole loop is one suppressed burst so it costs a single refresh, not one per item moved.
	 */
	private void moveGroup(Color colour, String fromGroup, String fromPrefix, String toGroup, String toPrefix,
		Set<Integer> unsetOnly)
	{
		String prefix = fromGroup + "." + fromPrefix;
		List<String> keys = configManager.getConfigurationKeys(prefix);

		suppressConfigEvents = true;
		try
		{
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

				if (!unsetOnly.contains(itemId))
				{
					configManager.setConfiguration(toGroup, toPrefix + itemId, itemColour);
				}
				configManager.unsetConfiguration(fromGroup, fromPrefix + itemId);
			}
		}
		finally
		{
			suppressConfigEvents = false;
		}
	}

	private Set<String> readDisabledGroupHexes()
	{
		String csv = configManager.getConfiguration(OWN_CONFIG_GROUP, DISABLED_GROUPS_KEY);
		Set<String> hexes = new HashSet<>();
		if (csv != null)
		{
			for (String hex : csv.split(","))
			{
				String trimmed = hex.trim();
				if (!trimmed.isEmpty())
				{
					hexes.add(trimmed);
				}
			}
		}
		return hexes;
	}

	private void addDisabledGroupMarker(Color colour)
	{
		Set<String> hexes = readDisabledGroupHexes();
		if (hexes.add(colourHex(colour)))
		{
			configManager.setConfiguration(OWN_CONFIG_GROUP, DISABLED_GROUPS_KEY, String.join(",", hexes));
		}
	}

	private void removeDisabledGroupMarker(Color colour)
	{
		Set<String> hexes = readDisabledGroupHexes();
		if (hexes.remove(colourHex(colour)))
		{
			if (hexes.isEmpty())
			{
				configManager.unsetConfiguration(OWN_CONFIG_GROUP, DISABLED_GROUPS_KEY);
			}
			else
			{
				configManager.setConfiguration(OWN_CONFIG_GROUP, DISABLED_GROUPS_KEY, String.join(",", hexes));
			}
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

	private void ensureItemIndexBuilt()
	{
		if (itemIndex == null)
		{
			itemIndex = buildItemIndex();
		}
	}

	/**
	 * Every item id currently matching {@code colour}'s stored wildcard patterns. Cheap: just a
	 * name-matching scan of the already-cached item index, no per-item config reads or icon fetches.
	 */
	private Set<Integer> currentPatternMatchIds(Color colour)
	{
		List<String> patterns = splitPatterns(configManager.getConfiguration(OWN_CONFIG_GROUP, PATTERN_KEY_PREFIX + colourHex(colour)));
		if (patterns.isEmpty())
		{
			return Collections.emptySet();
		}

		ensureItemIndexBuilt();
		List<SearchItem> index = itemIndex;
		if (index == null)
		{
			return Collections.emptySet();
		}

		Set<Integer> ids = new HashSet<>();
		for (SearchItem candidate : index)
		{
			if (matchesAny(candidate.getName(), patterns))
			{
				ids.add(candidate.getId());
			}
		}
		return ids;
	}

	private List<ColourGroup> buildGroups()
	{
		Map<Color, List<Integer>> enabledIds = collectItemIds(GroundItemsConfig.GROUP, HIGHLIGHT_KEY_PREFIX);
		Map<Color, List<Integer>> disabledIds = collectItemIds(OWN_CONFIG_GROUP, DISABLED_KEY_PREFIX);
		Map<Color, List<String>> patternsByColour = collectPatterns();
		Set<String> disabledGroupHexes = readDisabledGroupHexes();

		Map<Color, Set<Integer>> patternMatchIds = resolvePatternMatches(patternsByColour, disabledGroupHexes, enabledIds);

		if (!patternsByColour.isEmpty())
		{
			ensureItemIndexBuilt();
		}
		List<SearchItem> index = itemIndex;

		Map<Color, Boolean> enabledByColour = new TreeMap<>(GroundItemColourGroupsPlugin::compareByHue);
		enabledIds.keySet().forEach(colour -> enabledByColour.put(colour, true));
		disabledIds.keySet().forEach(colour -> enabledByColour.putIfAbsent(colour, false));
		patternsByColour.keySet().forEach(colour ->
			enabledByColour.putIfAbsent(colour, !disabledGroupHexes.contains(colourHex(colour))));

		List<ColourGroup> groups = new ArrayList<>();
		for (Map.Entry<Color, Boolean> entry : enabledByColour.entrySet())
		{
			Color colour = entry.getKey();
			Set<Integer> patternIds = patternMatchIds.getOrDefault(colour, Collections.emptySet());

			// Individually-managed rows only: an id that's a pattern match is represented by its
			// pattern's single summary row instead (below), never hydrated with a name/icon lookup
			// or given its own row - the whole point of a wildcard pattern is to avoid that cost.
			List<ColouredGroundItem> items = new ArrayList<>();
			for (int id : enabledIds.getOrDefault(colour, Collections.emptyList()))
			{
				if (!patternIds.contains(id))
				{
					items.add(hydrate(id));
				}
			}
			for (int id : disabledIds.getOrDefault(colour, Collections.emptyList()))
			{
				items.add(hydrate(id));
			}
			items.sort(Comparator.comparing(ColouredGroundItem::getName, String.CASE_INSENSITIVE_ORDER));

			List<PatternMatch> patternMatches = new ArrayList<>();
			for (String pattern : patternsByColour.getOrDefault(colour, Collections.emptyList()))
			{
				int count = index == null ? 0 : countMatches(pattern, index);
				patternMatches.add(new PatternMatch(pattern, count));
			}

			groups.add(new ColourGroup(colour, entry.getValue(), items, patternMatches));
		}

		return groups;
	}

	private ColouredGroundItem hydrate(int itemId)
	{
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
		return new ColouredGroundItem(itemId, name, image);
	}

	private static int countMatches(String pattern, List<SearchItem> index)
	{
		int count = 0;
		for (SearchItem candidate : index)
		{
			if (WildcardMatcher.matches(pattern, candidate.getName()))
			{
				count++;
			}
		}
		return count;
	}

	/**
	 * Reads every {@code keyPrefix + itemId} entry under {@code configGroup}, resolving each item's
	 * colour and grouping the ids by it. Used for both the live GroundItemsConfig entries and this
	 * plugin's own store of bulk-disabled items. Deliberately cheap - no name/icon lookups - since
	 * most of these ids may turn out to be pattern matches that are never displayed individually.
	 */
	private Map<Color, List<Integer>> collectItemIds(String configGroup, String keyPrefix)
	{
		String prefix = configGroup + "." + keyPrefix;
		List<String> keys = configManager.getConfigurationKeys(prefix);

		Map<Color, List<Integer>> grouped = new LinkedHashMap<>();

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

			grouped.computeIfAbsent(colour, c -> new ArrayList<>()).add(itemId);
		}

		return grouped;
	}

	/**
	 * Reads every colour's stored wildcard pattern list (OWN_CONFIG_GROUP, PATTERN_KEY_PREFIX +
	 * colour hex, a comma-separated string), keyed by the colour it applies to.
	 */
	private Map<Color, List<String>> collectPatterns()
	{
		String prefix = OWN_CONFIG_GROUP + "." + PATTERN_KEY_PREFIX;
		List<String> keys = configManager.getConfigurationKeys(prefix);

		Map<Color, List<String>> patternsByColour = new LinkedHashMap<>();
		for (String wholeKey : keys)
		{
			String hex = wholeKey.substring(prefix.length());
			Color colour = parseColourHex(hex);
			if (colour == null)
			{
				continue;
			}

			List<String> patterns = splitPatterns(configManager.getConfiguration(OWN_CONFIG_GROUP, PATTERN_KEY_PREFIX + hex));
			if (!patterns.isEmpty())
			{
				patternsByColour.put(colour, patterns);
			}
		}

		return patternsByColour;
	}

	/**
	 * For every colour with active (non-disabled) wildcard patterns, matches those patterns against
	 * the full item catalogue (already cached in {@code itemIndex}, so this is just name-matching -
	 * no per-item itemManager calls) and returns the matching item ids per colour. Any match that
	 * isn't already highlighted (with any colour) gets its highlight_ entry written here so the game
	 * actually shows it. Never touches an item that's already coloured (by hand or by a different
	 * pattern), so narrowing or editing a pattern never strips an existing colour.
	 */
	private Map<Color, Set<Integer>> resolvePatternMatches(Map<Color, List<String>> patternsByColour,
		Set<String> disabledGroupHexes, Map<Color, List<Integer>> enabledIds)
	{
		Map<Color, Set<Integer>> matchIdsByColour = new LinkedHashMap<>();
		if (patternsByColour.isEmpty())
		{
			return matchIdsByColour;
		}

		ensureItemIndexBuilt();
		List<SearchItem> index = itemIndex;
		if (index == null)
		{
			return matchIdsByColour;
		}

		for (Map.Entry<Color, List<String>> entry : patternsByColour.entrySet())
		{
			Color colour = entry.getKey();
			if (disabledGroupHexes.contains(colourHex(colour)))
			{
				// Group is bulk-disabled - leave pattern matching paused until it's re-enabled.
				continue;
			}

			List<String> patterns = entry.getValue();
			Set<Integer> alreadyHighlighted = new HashSet<>(enabledIds.getOrDefault(colour, Collections.emptyList()));

			Set<Integer> matchIds = new HashSet<>();
			List<Integer> newMatches = new ArrayList<>();
			for (SearchItem candidate : index)
			{
				if (!matchesAny(candidate.getName(), patterns))
				{
					continue;
				}

				int id = candidate.getId();
				matchIds.add(id);
				if (!alreadyHighlighted.contains(id)
					&& configManager.getConfiguration(GroundItemsConfig.GROUP, HIGHLIGHT_KEY_PREFIX + id, Color.class) == null)
				{
					newMatches.add(id);
				}
			}

			if (!newMatches.isEmpty())
			{
				suppressConfigEvents = true;
				try
				{
					for (int id : newMatches)
					{
						configManager.setConfiguration(GroundItemsConfig.GROUP, HIGHLIGHT_KEY_PREFIX + id, colour);
					}
				}
				finally
				{
					suppressConfigEvents = false;
				}
			}

			matchIdsByColour.put(colour, matchIds);
		}

		return matchIdsByColour;
	}

	private static boolean matchesAny(String name, List<String> patterns)
	{
		for (String pattern : patterns)
		{
			if (WildcardMatcher.matches(pattern, name))
			{
				return true;
			}
		}
		return false;
	}

	private static List<String> splitPatterns(String csv)
	{
		List<String> patterns = new ArrayList<>();
		if (csv != null)
		{
			for (String part : csv.split(","))
			{
				String trimmed = part.trim();
				if (!trimmed.isEmpty())
				{
					patterns.add(trimmed);
				}
			}
		}
		return patterns;
	}

	private static String colourHex(Color colour)
	{
		return String.format("%08x", colour.getRGB());
	}

	private static Color parseColourHex(String hex)
	{
		try
		{
			return new Color(Integer.parseUnsignedInt(hex, 16), true);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
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
