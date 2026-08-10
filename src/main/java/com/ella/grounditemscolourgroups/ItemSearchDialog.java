package com.ella.grounditemscolourgroups;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;

/**
 * Filters a pre-built {@code items} catalogue (see
 * {@link GroundItemColourGroupsPlugin#buildItemIndex()}) that covers every item known to the
 * client, tradeable or not, so quest items, clue scroll rewards, etc. can be found here too.
 */
class ItemSearchDialog extends JDialog
{
	ItemSearchDialog(Window owner, List<SearchItem> items, Consumer<SearchItem> onSelect)
	{
		super(owner, "Add item to colour group", ModalityType.APPLICATION_MODAL);
		setSize(280, 380);
		setLocationRelativeTo(owner);
		setLayout(new BorderLayout(6, 6));
		getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JTextField searchField = new JTextField();

		DefaultListModel<SearchItem> model = new DefaultListModel<>();
		JList<SearchItem> list = new JList<>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		list.setCellRenderer((jList, value, index, isSelected, cellHasFocus) ->
		{
			JLabel label = new JLabel(value.getName());
			label.setOpaque(true);
			label.setBackground(isSelected ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			label.setForeground(Color.WHITE);
			label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
			return label;
		});

		JLabel hint = new JLabel("<html><body style='width:230px'>Type at least 2 characters.</body></html>");
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel top = new JPanel(new BorderLayout(4, 4));
		top.add(searchField, BorderLayout.NORTH);
		top.add(hint, BorderLayout.SOUTH);

		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				update();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				update();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				update();
			}

			private void update()
			{
				String query = searchField.getText().trim().toLowerCase();
				model.clear();
				if (query.length() < 2)
				{
					return;
				}

				// items is already sorted by name, so this preserves order without re-sorting
				items.stream()
					.filter(item -> item.getName().toLowerCase().contains(query))
					.limit(50)
					.forEach(model::addElement);
			}
		});

		Runnable select = () ->
		{
			SearchItem selected = list.getSelectedValue();
			if (selected != null)
			{
				onSelect.accept(selected);
				dispose();
			}
		};

		list.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2)
				{
					select.run();
				}
			}
		});

		JButton addButton = new JButton("Add");
		addButton.addActionListener(e -> select.run());

		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e -> dispose());

		JPanel buttons = new JPanel();
		buttons.add(addButton);
		buttons.add(cancelButton);

		add(top, BorderLayout.NORTH);
		add(new JScrollPane(list), BorderLayout.CENTER);
		add(buttons, BorderLayout.SOUTH);

		searchField.requestFocusInWindow();
	}
}
