package com.ella.grounditemscolourgroups;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.ui.PluginPanel;

class GroundItemColourGroupsPanel extends PluginPanel
{
	private final PanelCallbacks callbacks;
	private final JPanel groupsContainer = new JPanel();

	/**
	 * Colours whose group is currently collapsed - purely a display preference, so it's kept here
	 * rather than round-tripped through the plugin/config, and toggling it just re-renders the last
	 * groups the plugin gave us instead of asking for a fresh refresh.
	 */
	private final Set<Color> collapsedGroups = new HashSet<>();
	private List<ColourGroup> lastGroups = Collections.emptyList();

	/**
	 * The group and collapsed state each colour's currently-displayed JPanel was last built from,
	 * so a refresh can tell whether a colour actually changed since last time and, if not, reuse its
	 * existing row components instead of tearing down and rebuilding them - the difference between a
	 * group of a couple of items and one of several hundred otherwise redoing the same, potentially
	 * very expensive, Swing construction on every single refresh, no matter which group it was that
	 * actually changed.
	 */
	private final Map<Color, ColourGroup> renderedGroupByColour = new HashMap<>();
	private final Map<Color, Boolean> renderedCollapsedByColour = new HashMap<>();
	private final Map<Color, JPanel> renderedPanelByColour = new HashMap<>();

	GroundItemColourGroupsPanel(PanelCallbacks callbacks)
	{
		super();
		this.callbacks = callbacks;

		JLabel title = new JLabel("Ground Item Colour Groups");
		title.setForeground(Color.WHITE);
		title.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

		JButton addButton = new JButton("+ Add");
		addButton.setToolTipText("Assign a new item to a colour");
		addButton.addActionListener(e -> callbacks.addNewGroup());

		JButton addPatternButton = new JButton("+ Pattern");
		addPatternButton.setToolTipText("<html><body style='width:220px'>Start a new colour group from a wildcard "
			+ "name pattern (e.g. \"Clue scroll*\") instead of one item at a time - handy for large families like "
			+ "clue rewards.</body></html>");
		addPatternButton.addActionListener(e -> callbacks.addPatternGroup());

		JButton refreshButton = new JButton("Refresh");
		refreshButton.addActionListener(e -> callbacks.refresh());

		JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		headerButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		headerButtons.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		headerButtons.add(addButton);
		headerButtons.add(addPatternButton);
		headerButtons.add(refreshButton);

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.add(title);
		header.add(Box.createVerticalStrut(4));
		header.add(headerButtons);

		groupsContainer.setLayout(new BoxLayout(groupsContainer, BoxLayout.Y_AXIS));
		groupsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(header);
		add(groupsContainer);

		showEmptyMessage();
	}

	void showGroups(List<ColourGroup> groups)
	{
		lastGroups = groups;
		renderGroups();
	}

	/**
	 * Rebuilds the group list from {@code lastGroups} without asking the plugin to recompute
	 * anything - used both when the plugin hands us a fresh list and when a purely local display
	 * change (collapsing a group) needs to be reflected immediately.
	 */
	private void renderGroups()
	{
		groupsContainer.removeAll();

		if (lastGroups.isEmpty())
		{
			showEmptyMessage();
		}
		else
		{
			Set<Color> stillPresent = new HashSet<>();
			for (ColourGroup group : lastGroups)
			{
				Color colour = group.getColour();
				stillPresent.add(colour);
				boolean collapsed = collapsedGroups.contains(colour);

				JPanel groupPanel;
				if (group.equals(renderedGroupByColour.get(colour))
					&& Boolean.valueOf(collapsed).equals(renderedCollapsedByColour.get(colour)))
				{
					// Nothing about this colour changed since it was last built - reuse its rows
					// rather than redoing potentially hundreds of them for no reason.
					groupPanel = renderedPanelByColour.get(colour);
				}
				else
				{
					groupPanel = buildGroupPanel(group, collapsed);
					renderedGroupByColour.put(colour, group);
					renderedCollapsedByColour.put(colour, collapsed);
					renderedPanelByColour.put(colour, groupPanel);
				}

				groupsContainer.add(groupPanel);
				groupsContainer.add(Box.createVerticalStrut(6));
			}

			renderedGroupByColour.keySet().retainAll(stillPresent);
			renderedCollapsedByColour.keySet().retainAll(stillPresent);
			renderedPanelByColour.keySet().retainAll(stillPresent);
		}

		groupsContainer.revalidate();
		groupsContainer.repaint();
	}

	private void showEmptyMessage()
	{
		JLabel empty = new JLabel("<html><body style='width: 180px'>No items have a custom Ground Items colour "
			+ "yet. Click \"+ Add\" to pick one item, \"+ Pattern\" to match many by name (e.g. clue rewards), or "
			+ "right-click an item on the ground and use the Ground Items highlight option - it will show up "
			+ "here.</body></html>");
		empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		groupsContainer.add(empty);
	}

	private JPanel buildGroupPanel(ColourGroup group, boolean collapsed)
	{
		Color colour = group.getColour();
		List<ColouredGroundItem> items = group.getItems();
		List<PatternMatch> patternMatches = group.getPatternMatches();
		boolean enabled = group.isEnabled();

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createLineBorder(enabled ? colour : ColorScheme.MEDIUM_GRAY_COLOR, 2));

		JLabel swatchLabel = new JLabel(group.getName() + (enabled ? "" : " — off"));
		swatchLabel.setOpaque(true);
		swatchLabel.setBackground(colour);
		swatchLabel.setForeground(readableTextColour(colour));
		swatchLabel.setHorizontalAlignment(SwingConstants.CENTER);
		swatchLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		swatchLabel.setToolTipText("Click to rename this colour group");
		swatchLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				callbacks.renameGroup(colour);
			}
		});

		JButton collapseButton = new JButton(collapsed ? "▶" : "▼");
		collapseButton.setToolTipText(collapsed ? "Expand this colour group" : "Collapse this colour group");
		collapseButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
		collapseButton.addActionListener(e ->
		{
			if (collapsed)
			{
				collapsedGroups.remove(colour);
			}
			else
			{
				collapsedGroups.add(colour);
			}
			renderGroups();
		});

		JCheckBox enabledCheckbox = new JCheckBox();
		enabledCheckbox.setSelected(enabled);
		enabledCheckbox.setOpaque(false);
		enabledCheckbox.setToolTipText(enabled
			? "Turn off this colour group (removes the highlight from every item in it, but remembers the colour "
				+ "and pauses any wildcard patterns)"
			: "Turn this colour group back on (restores the highlight for every item in it, and resumes any "
				+ "wildcard patterns)");
		enabledCheckbox.addActionListener(e -> callbacks.setGroupEnabled(colour, enabledCheckbox.isSelected()));

		JPanel swatchLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		swatchLeft.setBackground(colour);
		swatchLeft.add(collapseButton);
		swatchLeft.add(enabledCheckbox);

		JButton addToGroupButton = new JButton("+");
		addToGroupButton.setToolTipText("Add another item to this colour");
		addToGroupButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
		addToGroupButton.addActionListener(e -> callbacks.addToGroup(colour));

		List<String> patterns = new ArrayList<>();
		for (PatternMatch patternMatch : patternMatches)
		{
			patterns.add(patternMatch.getPattern());
		}

		JButton patternsButton = new JButton("*");
		patternsButton.setToolTipText(patterns.isEmpty()
			? "Add a wildcard name pattern (e.g. \"Clue scroll*\") so matching items join this colour automatically"
			: "<html><body style='width:220px'>Add another wildcard pattern to this colour (already has: "
				+ String.join(", ", patterns) + ")</body></html>");
		patternsButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
		patternsButton.addActionListener(e -> callbacks.addPattern(colour));

		JButton deleteButton = new JButton("Delete");
		deleteButton.setToolTipText("Delete this whole colour group: every item's highlight and every wildcard "
			+ "pattern in it");
		deleteButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
		deleteButton.addActionListener(e -> callbacks.deleteGroup(colour));

		JPanel swatchButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		swatchButtons.setBackground(colour);
		swatchButtons.add(patternsButton);
		swatchButtons.add(addToGroupButton);
		swatchButtons.add(deleteButton);

		JPanel swatchRow = new JPanel(new BorderLayout());
		swatchRow.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		swatchRow.setBackground(colour);
		swatchRow.add(swatchLeft, BorderLayout.WEST);
		swatchRow.add(swatchLabel, BorderLayout.CENTER);
		swatchRow.add(swatchButtons, BorderLayout.EAST);
		panel.add(swatchRow);

		if (!collapsed)
		{
			for (PatternMatch patternMatch : patternMatches)
			{
				panel.add(buildPatternRow(colour, patternMatch));
			}

			for (ColouredGroundItem item : items)
			{
				panel.add(buildItemRow(colour, item));
			}
		}

		return panel;
	}

	/**
	 * One row per wildcard pattern - not one per matching item, however many there are - showing
	 * the pattern text itself and a live count of how many items currently match it.
	 */
	private JPanel buildPatternRow(Color groupColour, PatternMatch patternMatch)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(20, 20));
		iconLabel.setOpaque(true);
		iconLabel.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		iconLabel.setBorder(BorderFactory.createLineBorder(ColorScheme.LIGHT_GRAY_COLOR, 1));
		if (patternMatch.getSampleImage() != null)
		{
			// Still keeps the box background/border above - just gives it a real face (the first
			// current match, alphabetically) instead of leaving it blank when there's one to show.
			setScaledIcon(iconLabel, patternMatch.getSampleImage());
		}

		JLabel nameLabel = new JLabel(String.format("%s  (%d item%s)", patternMatch.getPattern(),
			patternMatch.getMatchCount(), patternMatch.getMatchCount() == 1 ? "" : "s"));
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setToolTipText("<html><body style='width:220px'>Wildcard pattern - every item whose name matches "
			+ "this gets this group's colour automatically, including items added to the game later.</body></html>");

		JButton recolourButton = new JButton();
		recolourButton.setToolTipText("Move this pattern (and its matches) to a different colour group");
		recolourButton.setBackground(groupColour);
		recolourButton.setPreferredSize(new Dimension(16, 16));
		recolourButton.addActionListener(e -> callbacks.changePatternColour(groupColour, patternMatch.getPattern()));

		JButton removeButton = new JButton("×");
		removeButton.setToolTipText("Remove this wildcard pattern");
		removeButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
		removeButton.addActionListener(e -> callbacks.removePattern(groupColour, patternMatch.getPattern()));

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		actions.add(recolourButton);
		actions.add(removeButton);

		row.add(iconLabel, BorderLayout.WEST);
		row.add(nameLabel, BorderLayout.CENTER);
		row.add(actions, BorderLayout.EAST);

		return row;
	}

	private JPanel buildItemRow(Color groupColour, ColouredGroundItem item)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(20, 20));
		setScaledIcon(iconLabel, item.getImage());

		JLabel nameLabel = new JLabel(item.getName());
		nameLabel.setForeground(Color.WHITE);

		JButton recolourButton = new JButton();
		recolourButton.setToolTipText("Change this item's colour");
		recolourButton.setBackground(groupColour);
		recolourButton.setPreferredSize(new Dimension(16, 16));
		recolourButton.addActionListener(e -> callbacks.changeItemColour(item.getItemId(), groupColour));

		JButton removeButton = new JButton("×");
		removeButton.setToolTipText("Remove this item's custom colour");
		removeButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
		removeButton.addActionListener(e -> callbacks.removeItem(item.getItemId()));

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		actions.add(recolourButton);
		actions.add(removeButton);

		row.add(iconLabel, BorderLayout.WEST);
		row.add(nameLabel, BorderLayout.CENTER);
		row.add(actions, BorderLayout.EAST);

		return row;
	}

	private static Color readableTextColour(Color background)
	{
		double luminance = (0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue()) / 255;
		return luminance > 0.55 ? Color.BLACK : Color.WHITE;
	}

	private static final int ICON_BOX_SIZE = 20;
	private static final double ICON_SCALE = 0.7;

	/**
	 * Shows {@code image} shrunk to {@link #ICON_SCALE} of its natural size and centred in a
	 * {@link #ICON_BOX_SIZE}-square box, repainting once the async image actually finishes loading -
	 * the same "notify when ready" behaviour {@link AsyncBufferedImage#addTo} provides, just with
	 * scaling applied instead of drawing at full (often oversized) native resolution.
	 */
	private static void setScaledIcon(JLabel label, AsyncBufferedImage image)
	{
		label.setIcon(new ScaledIcon(image, ICON_BOX_SIZE, ICON_SCALE));
		image.onLoaded(label::repaint);
	}
}
