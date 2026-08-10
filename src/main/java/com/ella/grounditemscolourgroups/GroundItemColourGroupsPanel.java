package com.ella.grounditemscolourgroups;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

class GroundItemColourGroupsPanel extends PluginPanel
{
	private final PanelCallbacks callbacks;
	private final JPanel groupsContainer = new JPanel();

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

		JButton refreshButton = new JButton("Refresh");
		refreshButton.addActionListener(e -> callbacks.refresh());

		JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		headerButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		headerButtons.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		headerButtons.add(addButton);
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

	void showGroups(Map<Color, List<ColouredGroundItem>> grouped)
	{
		groupsContainer.removeAll();

		if (grouped.isEmpty())
		{
			showEmptyMessage();
		}
		else
		{
			for (Map.Entry<Color, List<ColouredGroundItem>> entry : grouped.entrySet())
			{
				groupsContainer.add(buildGroupPanel(entry.getKey(), entry.getValue()));
				groupsContainer.add(Box.createVerticalStrut(6));
			}
		}

		groupsContainer.revalidate();
		groupsContainer.repaint();
	}

	private void showEmptyMessage()
	{
		JLabel empty = new JLabel("<html><body style='width: 180px'>No items have a custom Ground Items colour "
			+ "yet. Click \"+ Add\" above, or right-click an item on the ground and use the Ground Items "
			+ "highlight option - it will show up here.</body></html>");
		empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		groupsContainer.add(empty);
	}

	private JPanel buildGroupPanel(Color colour, List<ColouredGroundItem> items)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createLineBorder(colour, 2));

		JLabel swatchLabel = new JLabel(String.format("#%06X  (%d item%s)",
			colour.getRGB() & 0xFFFFFF, items.size(), items.size() == 1 ? "" : "s"));
		swatchLabel.setOpaque(true);
		swatchLabel.setBackground(colour);
		swatchLabel.setForeground(readableTextColour(colour));
		swatchLabel.setHorizontalAlignment(SwingConstants.CENTER);

		JButton addToGroupButton = new JButton("+");
		addToGroupButton.setToolTipText("Add another item to this colour");
		addToGroupButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
		addToGroupButton.addActionListener(e -> callbacks.addToGroup(colour));

		JPanel swatchRow = new JPanel(new BorderLayout());
		swatchRow.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		swatchRow.setBackground(colour);
		swatchRow.add(swatchLabel, BorderLayout.CENTER);
		swatchRow.add(addToGroupButton, BorderLayout.EAST);
		panel.add(swatchRow);

		for (ColouredGroundItem item : items)
		{
			panel.add(buildItemRow(colour, item));
		}

		return panel;
	}

	private JPanel buildItemRow(Color groupColour, ColouredGroundItem item)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(20, 20));
		item.getImage().addTo(iconLabel);

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
}
