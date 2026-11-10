package com.artlyrics.config;

/**
 * Colors for one line of lyrics: words are tinted along a gradient from {@code from} to {@code to} across the
 * line, and the word being sung leans towards {@code accent}. {@code shifting} palettes slowly cycle their hues.
 */
public record Palette(int from, int to, int accent, boolean shifting) {
	/** RGB color for a word at position {@code t} (0..1) along the line. */
	public int colorAt(float t, double timeSeconds) {
		if (shifting) {
			// Drift between green, teal and violet like northern lights, rather than a full rainbow.
			double phase = Math.sin(timeSeconds * 0.35 + t * 2.2) * 0.5 + 0.5;
			return hsv((float) (0.42 + phase * 0.33), 0.5f, 1.0f);
		}
		return lerpRgb(from, to, t);
	}

	public static int lerpRgb(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	/** HSV (all 0..1) to RGB. */
	public static int hsv(float h, float s, float v) {
		float sector = (h - (float) Math.floor(h)) * 6f;
		int i = (int) sector;
		float f = sector - i;
		float p = v * (1 - s);
		float q = v * (1 - s * f);
		float u = v * (1 - s * (1 - f));
		float r, g, b;
		switch (i) {
			case 0 -> { r = v; g = u; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = u; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = u; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
	}

	/** RGB to {hue, saturation, value}, all 0..1. */
	public static float[] toHsv(int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255f;
		float g = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h;
		if (d == 0) {
			h = 0;
		} else if (max == r) {
			h = ((g - b) / d) % 6f;
		} else if (max == g) {
			h = (b - r) / d + 2f;
		} else {
			h = (r - g) / d + 4f;
		}
		h /= 6f;
		if (h < 0) {
			h += 1;
		}
		return new float[] {h, max == 0 ? 0 : d / max, max};
	}
}
