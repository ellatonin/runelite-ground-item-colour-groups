package com.ella.grounditemscolourgroups;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * One of a colour group's wildcard patterns, how many items in the game currently match it, and
 * one sample match's icon (the first one found, alphabetically) to give the row a recognisable
 * face instead of a blank placeholder - null if nothing currently matches. Shown as a single
 * summary row in the panel rather than expanding out every matching item. Value-based
 * equals/hashCode so the panel can tell whether a freshly rebuilt one is identical to the last one
 * it rendered, and skip re-rendering if so.
 */
@Getter
@EqualsAndHashCode
class PatternMatch
{
	private final String pattern;
	private final int matchCount;
	private final AsyncBufferedImage sampleImage;

	PatternMatch(String pattern, int matchCount, AsyncBufferedImage sampleImage)
	{
		this.pattern = pattern;
		this.matchCount = matchCount;
		this.sampleImage = sampleImage;
	}
}
