package com.ella.grounditemscolourgroups;

import java.awt.Color;
import java.util.List;
import lombok.Getter;

/**
 * All individually-added items sharing one highlight colour, plus whether that colour is
 * currently active in the built-in Ground Items config or has been bulk-disabled (and is only
 * remembered in this plugin's own config, see {@link GroundItemColourGroupsPlugin}), and any
 * wildcard name patterns that automatically pull in matching items - each shown as a single
 * summary row (see {@link PatternMatch}) rather than one row per matching item.
 */
@Getter
class ColourGroup
{
	private final Color colour;
	private final boolean enabled;
	private final List<ColouredGroundItem> items;
	private final List<PatternMatch> patternMatches;

	ColourGroup(Color colour, boolean enabled, List<ColouredGroundItem> items, List<PatternMatch> patternMatches)
	{
		this.colour = colour;
		this.enabled = enabled;
		this.items = items;
		this.patternMatches = patternMatches;
	}
}
