package com.ella.grounditemscolourgroups;

import java.awt.Color;
import java.util.List;
import lombok.Getter;

/**
 * All items sharing one highlight colour, plus whether that colour is currently active in the
 * built-in Ground Items config or has been bulk-disabled (and is only remembered in this
 * plugin's own config, see {@link GroundItemColourGroupsPlugin}).
 */
@Getter
class ColourGroup
{
	private final Color colour;
	private final boolean enabled;
	private final List<ColouredGroundItem> items;

	ColourGroup(Color colour, boolean enabled, List<ColouredGroundItem> items)
	{
		this.colour = colour;
		this.enabled = enabled;
		this.items = items;
	}
}
