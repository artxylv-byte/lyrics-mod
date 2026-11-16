package com.artlyrics.ui;

import com.artlyrics.config.ArtlyricsConfig.TextFont;

/**
 * The few drawing operations the settings screen and song card need. In the game this is backed by Minecraft's
 * {@code GuiGraphics}; in tests by Java2D, so layouts can be previewed without running the game.
 * Coordinates are GUI pixels. Colors are ARGB and get multiplied by the current {@link #alpha()}.
 */
public interface Canvas {
	int width();

	int height();

	void fill(int x0, int y0, int x1, int y1, int argb);

	void gradientV(int x0, int y0, int x1, int y1, int topArgb, int bottomArgb);

	/** Draws text with its top-left at (x, y). {@code font} null means Minecraft's default font. */
	void text(String text, TextFont font, int x, int y, int argb, boolean shadow);

	int textWidth(String text, TextFont font);

	/** A 10x10 icon from {@code assets/artlyrics/textures/gui/sprites/icon/<name>.png}, tinted. */
	void icon(String name, int x, int y, int argb);

	/**
	 * Draws a cover picture into the square (x, y, size): the frame for {@code elapsedMs} when it's animated.
	 * Returns false if it can't be drawn yet.
	 */
	boolean cover(com.artlyrics.cover.CoverImage image, long elapsedMs, int x, int y, int size);

	void push();

	void translate(float x, float y);

	void scale(float s);

	void pop();

	/** Restricts drawing to a rectangle (in current coordinates without transforms). */
	void clip(int x0, int y0, int x1, int y1);

	void unclip();

	/** Multiplier applied to the alpha of everything drawn. */
	float alpha();

	void setAlpha(float alpha);
}
