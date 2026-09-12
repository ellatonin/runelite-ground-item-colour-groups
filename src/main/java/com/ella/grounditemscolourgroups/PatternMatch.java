package com.ella.grounditemscolourgroups;

import lombok.Getter;

/**
 * One of a colour group's wildcard patterns, plus how many items in the game currently match it.
 * Shown as a single summary row in the panel rather than expanding out every matching item.
 */
@Getter
class PatternMatch
{
	private final String pattern;
	private final int matchCount;

	PatternMatch(String pattern, int matchCount)
	{
		this.pattern = pattern;
		this.matchCount = matchCount;
	}
}
