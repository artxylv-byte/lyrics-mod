package com.artlyrics.ui;

import com.artlyrics.config.ArtlyricsConfig.TextFont;
import com.artlyrics.config.Palette;

/** Shapes built from plain rectangles, so they look the same in the game and in previews. */
public final class Draw {
	private Draw() {
	}

	/** A rectangle with rounded corners of radius {@code r}. */
	public static void roundRect(Canvas c, int x0, int y0, int x1, int y1, int r, int argb) {
		r = Math.max(0, Math.min(r, Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
		if (r == 0) {
			c.fill(x0, y0, x1, y1, argb);
			return;
		}
		for (int i = 0; i < r; i++) {
			int inset = inset(r, i);
			c.fill(x0 + inset, y0 + i, x1 - inset, y0 + i + 1, argb);
			c.fill(x0 + inset, y1 - i - 1, x1 - inset, y1 - i, argb);
		}
		c.fill(x0, y0 + r, x1, y1 - r, argb);
	}

	/** Only the left corners rounded. */
	public static void roundRectLeft(Canvas c, int x0, int y0, int x1, int y1, int r, int argb) {
		for (int i = 0; i < r; i++) {
			int inset = inset(r, i);
			c.fill(x0 + inset, y0 + i, x1, y0 + i + 1, argb);
			c.fill(x0 + inset, y1 - i - 1, x1, y1 - i, argb);
		}
		c.fill(x0, y0 + r, x1, y1 - r, argb);
	}

	/** A 1px rounded outline that follows the same corner shape as {@link #roundRect}. */
	public static void roundOutline(Canvas c, int x0, int y0, int x1, int y1, int r, int argb) {
		r = Math.max(0, Math.min(r, Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
		int[] a = new int[r + 1];
		for (int i = 0; i < r; i++) {
			a[i] = inset(r, i);
		}
		// Top and bottom edges run between the corners' first rows.
		c.fill(x0 + a[0], y0, x1 - a[0], y0 + 1, argb);
		c.fill(x0 + a[0], y1 - 1, x1 - a[0], y1, argb);
		// Corners: on each row, cover from this row's edge to where the row above ended, so the curve is closed.
		for (int i = 1; i <= r; i++) {
			int from = a[i];
			int to = Math.max(from + 1, a[i - 1]);
			c.fill(x0 + from, y0 + i, x0 + to, y0 + i + 1, argb);
			c.fill(x1 - to, y0 + i, x1 - from, y0 + i + 1, argb);
			c.fill(x0 + from, y1 - i - 1, x0 + to, y1 - i, argb);
			c.fill(x1 - to, y1 - i - 1, x1 - from, y1 - i, argb);
		}
		// Straight sides between the corners.
		c.fill(x0, y0 + r + 1, x0 + 1, y1 - r - 1, argb);
		c.fill(x1 - 1, y0 + r + 1, x1, y1 - r - 1, argb);
	}

	/** A filled circle (approximated with rows). */
	public static void circle(Canvas c, float cx, float cy, float radius, int argb) {
		int r = Math.round(radius);
		int x0 = Math.round(cx - radius);
		int y0 = Math.round(cy - radius);
		roundRect(c, x0, y0, x0 + 2 * r, y0 + 2 * r, r, argb);
	}

	/** A horizontal gradient made of thin vertical strips. */
	public static void gradientH(Canvas c, int x0, int y0, int x1, int y1, int leftArgb, int rightArgb) {
		int w = x1 - x0;
		if (w <= 0) {
			return;
		}
		int step = w > 160 ? 2 : 1;
		for (int x = x0; x < x1; x += step) {
			float t = (x - x0) / (float) Math.max(1, w - 1);
			c.fill(x, y0, Math.min(x1, x + step), y1, lerpArgb(leftArgb, rightArgb, t));
		}
	}

	/** A small arrow pointing left or right, 3x5 pixels. */
	public static void chevron(Canvas c, int x, int y, boolean right, int argb) {
		for (int i = 0; i < 3; i++) {
			int dx = right ? i : 2 - i;
			c.fill(x + dx, y + i, x + dx + 1, y + i + 1, argb);
			c.fill(x + dx, y + 4 - i, x + dx + 1, y + 5 - i, argb);
		}
	}

	/** Shortens {@code text} with an ellipsis so it fits {@code maxWidth}. */
	public static String ellipsize(Canvas c, String text, TextFont font, int maxWidth) {
		if (c.textWidth(text, font) <= maxWidth) {
			return text;
		}
		String dots = "...";
		int end = text.length();
		while (end > 0 && c.textWidth(text.substring(0, end) + dots, font) > maxWidth) {
			end--;
		}
		return text.substring(0, end).stripTrailing() + dots;
	}

	public static int lerpArgb(int a, int b, float t) {
		int alpha = Math.round(((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * Math.max(0, Math.min(1, t)));
		return (alpha << 24) | Palette.lerpRgb(a & 0xFFFFFF, b & 0xFFFFFF, t);
	}

	public static int withAlpha(int rgb, float alpha) {
		int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	private static int inset(int r, int row) {
		double dy = r - row - 0.5;
		return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
	}
}
