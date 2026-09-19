package com.ella.grounditemscolourgroups;

import java.awt.Color;

interface PanelCallbacks
{
	void forceRefresh();

	void addNewGroup();

	void addToGroup(Color groupColour);

	void addPatternGroup();

	void addPattern(Color groupColour);

	void removePattern(Color groupColour, String pattern);

	void changePatternColour(Color currentColour, String pattern);

	void renameGroup(Color groupColour);

	void deleteGroup(Color groupColour);

	void removeItem(int itemId);

	void changeItemColour(int itemId, Color currentColour);

	void setGroupEnabled(Color groupColour, boolean enabled);
}
