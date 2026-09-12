package com.ella.grounditemscolourgroups;

import java.awt.Color;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * All individually-added items sharing one highlight colour, plus whether that colour is
 * currently active in the built-in Ground Items config or has been bulk-disabled (and is only
 * remembered in this plugin's own config, see {@link GroundItemColourGroupsPlugin}), any
 * wildcard name patterns that automatically pull in matching items - each shown as a single
 * summary row (see {@link PatternMatch}) rather than one row per matching item - and the group's
 * display name, which defaults to the colour's hex code until the user renames it.
 *
 * <p>Value-based equals/hashCode (relying on {@code items} holding the same cached
 * {@link ColouredGroundItem} instances - see {@link GroundItemColourGroupsPlugin}'s hydration
 * cache - whenever nothing actually changed for a colour) so the panel can recognise a group as
 * unchanged since its last render and skip rebuilding its (potentially hundreds of) rows.
 */
@Getter
@EqualsAndHashCode
class ColourGroup
{
	private final Color colour;
	private final String name;
	private final boolean enabled;
	private final List<ColouredGroundItem> items;
	private final List<PatternMatch> patternMatches;

	ColourGroup(Color colour, String name, boolean enabled, List<ColouredGroundItem> items, List<PatternMatch> patternMatches)
	{
		this.colour = colour;
		this.name = name;
		this.enabled = enabled;
		this.items = items;
		this.patternMatches = patternMatches;
	}
}
