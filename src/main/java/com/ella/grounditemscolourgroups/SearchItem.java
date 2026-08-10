package com.ella.grounditemscolourgroups;

import lombok.Getter;

@Getter
class SearchItem
{
	private final int id;
	private final String name;

	SearchItem(int id, String name)
	{
		this.id = id;
		this.name = name;
	}
}
