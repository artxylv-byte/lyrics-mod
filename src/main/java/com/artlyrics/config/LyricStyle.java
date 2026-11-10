package com.artlyrics.config;

import java.util.Locale;

/** Built-in palettes, named after Minecraft materials, plus {@link #CUSTOM} which uses the user's own colors. */
public enum LyricStyle {
	AMETHYST(0xA97CF8, 0xE3C6FF, 0xFFF2FF),
	CHERRY(0xFFC6DC, 0xFF7FB2, 0xFFFFFF),
	EMBER(0xFFB04A, 0xFF4F30, 0xFFF0C8),
	PRISMARINE(0x8DEBDA, 0x4C9FC4, 0xEFFFFF),
	HONEY(0xFFE28C, 0xF2A21E, 0xFFFBE8),
	MOSS(0xCDEB8E, 0x5DB565, 0xF4FFE6),
	QUARTZ(0xFFFFFF, 0xE9E3D6, 0xFFCF70),
	/** Slowly shifting colors. */
	AURORA(0x66FFC2, 0x8A7CFF, 0xFFFFFF),
	/** Colors picked in the settings. */
	CUSTOM(0x9AD0FF, 0xFF9AE0, 0xFFFFFF);

	public final int from;
	public final int to;
	public final int accent;

	LyricStyle(int from, int to, int accent) {
		this.from = from;
		this.to = to;
		this.accent = accent;
	}

	public String translationKey() {
		return "artlyrics.style." + name().toLowerCase(Locale.ROOT);
	}

	/** The palette of a built-in style (for {@link #CUSTOM} use {@link ArtlyricsConfig#palette()}). */
	public Palette palette() {
		return new Palette(from, to, accent, this == AURORA);
	}
}
