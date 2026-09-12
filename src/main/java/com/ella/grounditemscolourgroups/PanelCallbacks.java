package com.ella.grounditemscolourgroups;

import java.awt.Color;

interface PanelCallbacks
{
	void refresh();

	void addNewGroup();

	void addToGroup(Color groupColour);

	void removeItem(int itemId);

	void changeItemColour(int itemId, Color currentColour);

	void setGroupEnabled(Color groupColour, boolean enabled);
}
