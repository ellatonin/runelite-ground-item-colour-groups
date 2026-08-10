package com.ella.grounditemscolourgroups;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GroundItemColourGroupsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GroundItemColourGroupsPlugin.class);
		RuneLite.main(args);
	}
}
