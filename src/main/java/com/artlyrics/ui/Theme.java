package com.artlyrics.ui;

/** Colors of the settings screen and song card. The accent comes from the chosen palette. */
public final class Theme {
	private Theme() {
	}

	public static final int SCRIM = 0x99050409;
	public static final int PANEL = 0xF2120F1A;
	public static final int SIDEBAR = 0xFF0C0A12;
	public static final int CARD = 0xFF1B1826;
	public static final int CARD_HOVER = 0xFF242033;
	public static final int CONTROL = 0xFF25212F;
	public static final int CONTROL_HOVER = 0xFF2F2A3D;
	public static final int LINE = 0x22FFFFFF;
	public static final int TEXT = 0xFFF2F0FA;
	public static final int MUTED = 0xFF9893AE;
	public static final int FAINT = 0xFF5F5A73;
	public static final int OFF = 0xFF3A3648;
	public static final int GOOD = 0xFF7FE0A4;
	public static final int WARN = 0xFFFFC26B;
	public static final int BAD = 0xFFFF8080;

	/** A readable accent derived from the palette color (keeps very dark custom colors visible). */
	public static int accent(int rgb) {
		float[] hsv = com.artlyrics.config.Palette.toHsv(rgb);
		float v = Math.max(hsv[2], 0.75f);
		return 0xFF000000 | com.artlyrics.config.Palette.hsv(hsv[0], Math.min(hsv[1], 0.75f), v);
	}
}
