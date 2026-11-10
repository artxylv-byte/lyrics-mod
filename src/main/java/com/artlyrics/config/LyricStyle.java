package com.artlyrics.config;

/**
 * A color look for the lyrics. Words are colored along a gradient from {@code from} to {@code to}
 * across the line; the word being sung blends towards {@code highlight}.
 */
public enum LyricStyle {
	LAVENDER(0xC8A2FF, 0xF2B5FF, 0xFFFFFF, false, false),
	DREAMY(0xFFB3DE, 0x9FD8FF, 0xFFFFFF, false, true),
	SUNSET(0xFFD36E, 0xFF7A5C, 0xFFF4D6, false, false),
	ICE(0xD8FBFF, 0x7EC8FF, 0xFFFFFF, false, false),
	ROYAL(0xFFD75E, 0xC08CFF, 0xFFF8E0, true, false),
	MINT(0xB5FFD9, 0x5FE3E0, 0xFFFFFF, false, false),
	CLASSIC(0xFFFFFF, 0xFFFFFF, 0xFFE066, false, false),
	RAINBOW(0xFF6B6B, 0x6BB5FF, 0xFFFFFF, true, false);

	public final int from;
	public final int to;
	public final int highlight;
	public final boolean bold;
	public final boolean italic;

	LyricStyle(int from, int to, int highlight, boolean bold, boolean italic) {
		this.from = from;
		this.to = to;
		this.highlight = highlight;
		this.bold = bold;
		this.italic = italic;
	}

	public String translationKey() {
		return "artlyrics.style." + name().toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * RGB color for a word at position {@code t} (0..1) along the line. {@code timeSeconds} animates the
	 * rainbow style.
	 */
	public int colorAt(float t, double timeSeconds) {
		if (this == RAINBOW) {
			float hue = (float) ((t * 0.6 + timeSeconds * 0.08) % 1.0);
			return hsv(hue, 0.55f, 1.0f);
		}
		return lerpRgb(from, to, t);
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

	public static int lerpRgb(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
