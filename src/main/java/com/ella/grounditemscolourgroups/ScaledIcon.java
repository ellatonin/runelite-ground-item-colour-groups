package com.ella.grounditemscolourgroups;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import javax.swing.Icon;

/**
 * Draws {@code image} at {@code scale} of its natural size, centred within a
 * {@code boxSize} x {@code boxSize} footprint. Item sprites vary in native size (some OSRS icons
 * are noticeably bigger than others), and Swing doesn't scale a plain Icon down to fit a smaller
 * JLabel on its own - it just paints at full size, regardless of the label's preferred size - so
 * without this, bigger sprites overflow their row instead of shrinking to match it.
 */
class ScaledIcon implements Icon
{
	private final Image image;
	private final int boxSize;
	private final double scale;

	ScaledIcon(Image image, int boxSize, double scale)
	{
		this.image = image;
		this.boxSize = boxSize;
		this.scale = scale;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		int naturalWidth = image.getWidth(c);
		int naturalHeight = image.getHeight(c);
		if (naturalWidth <= 0 || naturalHeight <= 0)
		{
			return;
		}

		int drawWidth = (int) Math.round(naturalWidth * scale);
		int drawHeight = (int) Math.round(naturalHeight * scale);
		int offsetX = x + (boxSize - drawWidth) / 2;
		int offsetY = y + (boxSize - drawHeight) / 2;
		g.drawImage(image, offsetX, offsetY, drawWidth, drawHeight, c);
	}

	@Override
	public int getIconWidth()
	{
		return boxSize;
	}

	@Override
	public int getIconHeight()
	{
		return boxSize;
	}
}
