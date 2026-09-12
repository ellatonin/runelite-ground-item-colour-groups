package com.ella.grounditemscolourgroups;

import java.awt.Color;

interface PanelCallbacks
{
	void refresh();

	void addNewGroup();

	void addToGroup(Color groupColour);

	void addPatternGroup();

	void editPatterns(Color groupColour, String currentPatternsCsv);

	void removePattern(Color groupColour, String pattern);

	void changePatternColour(Color currentColour, String pattern);

	void removeItem(int itemId);

	void changeItemColour(int itemId, Color currentColour);

	void setGroupEnabled(Color groupColour, boolean enabled);
}
