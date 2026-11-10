package com.artlyrics.config;

import java.util.Locale;

/**
 * Color palettes, named after Minecraft materials. Words are tinted along a gradient from {@code from} to
 * {@code to} across the line; the word being sung leans towards {@code accent}.
 */
public enum LyricStyle {
	AMETHYST(0xA97CF8, 0xE3C6FF, 0xFFF2FF, false, false),
	CHERRY(0xFFC6DC, 0xFF7FB2, 0xFFFFFF, false, true),
	EMBER(0xFFB04A, 0xFF4F30, 0xFFF0C8, true, false),
	PRISMARINE(0x8DEBDA, 0x4C9FC4, 0xEFFFFF, false, false),
	HONEY(0xFFE28C, 0xF2A21E, 0xFFFBE8, false, false),
	MOSS(0xCDEB8E, 0x5DB565, 0xF4FFE6, false, false),
	QUARTZ(0xFFFFFF, 0xE9E3D6, 0xFFCF70, false, false),
	/** Slowly shifting colors. */
	AURORA(0x66FFC2, 0x8A7CFF, 0xFFFFFF, true, false);

	public final int from;
	public final int to;
	public final int accent;
	public final boolean bold;
	public final boolean italic;

	LyricStyle(int from, int to, int accent, boolean bold, boolean italic) {
		this.from = from;
		this.to = to;
		this.accent = accent;
		this.bold = bold;
		this.italic = italic;
	}

	public String translationKey() {
		return "artlyrics.style." + name().toLowerCase(Locale.ROOT);
	}

	/** RGB color for a word at position {@code t} (0..1) along the line; {@code timeSeconds} animates Aurora. */
	public int colorAt(float t, double timeSeconds) {
		if (this == AURORA) {
			// Drift between green, teal and violet like northern lights, rather than a full rainbow.
			double phase = Math.sin(timeSeconds * 0.35 + t * 2.2) * 0.5 + 0.5;
			float hue = (float) (0.42 + phase * 0.33);
			return hsv(hue, 0.5f, 1.0f);
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

	static int hsv(float h, float s, float v) {
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
}
