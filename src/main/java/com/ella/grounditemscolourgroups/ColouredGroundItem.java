package com.ella.grounditemscolourgroups;

import lombok.Getter;
import net.runelite.client.util.AsyncBufferedImage;

@Getter
class ColouredGroundItem
{
	private final int itemId;
	private final String name;
	private final AsyncBufferedImage image;

	ColouredGroundItem(int itemId, String name, AsyncBufferedImage image)
	{
		this.itemId = itemId;
		this.name = name;
		this.image = image;
	}
}
