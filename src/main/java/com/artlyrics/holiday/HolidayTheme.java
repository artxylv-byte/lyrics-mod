package com.artlyrics.holiday;

import com.artlyrics.config.Palette;

import java.util.Locale;

/**
 * How a holiday looks: the colors used for lyrics, the song card and the menu, and the little decorations that
 * float around the lyrics and over the card. Flag-day themes ({@link #NATIONAL}) take the region's flag colors.
 */
public enum HolidayTheme {
	CHRISTMAS("Christmas", Palette.bands(0xFFD36B, 0xE8343F, 0xF4F4F4, 0x3DBE6B), Motion.FALL,
			new String[] {"❄", "❅", "❆", "✦"}, new int[] {0xFFFFFF, 0xDDEEFF, 0xFFD36B}),
	NEW_YEAR("New Year", new Palette(0xFFD36B, 0xE8ECF5, 0x9FC4FF, false), Motion.BURST,
			new String[] {"✦", "✧", "•"}, new int[] {0xFFD36B, 0xF4F4FF, 0xFF8AC8, 0x8FC2FF}),
	LUNAR_NEW_YEAR("Lunar New Year", new Palette(0xE5302A, 0xFFC94A, 0xFFE7A0, false), Motion.RISE,
			new String[] {"●", "✦"}, new int[] {0xE5302A, 0xFF7A2E, 0xFFC94A}),
	VALENTINE("Valentine's", new Palette(0xFF7AB6, 0xE0284F, 0xFFC2DA, false), Motion.RISE,
			new String[] {"♥", "❤"}, new int[] {0xFF7AB6, 0xE0284F, 0xFFC2DA}),
	EASTER("Easter", new Palette(0xFFE27A, 0x9BE38B, 0xC9A2FF, false), Motion.FALL,
			new String[] {"✿", "❀", "⬮"}, new int[] {0xFFB3D9, 0xFFE27A, 0xC9A2FF, 0x9BE38B}),
	MIDSUMMER("Midsummer", new Palette(0x7BD36A, 0xFFD86B, 0xFFF3A8, false), Motion.TWINKLE,
			new String[] {"•", "✿", "•"}, new int[] {0xFFF3A8, 0xD8FF8A, 0xFFFFFF}),
	HALLOWEEN("Halloween", new Palette(0xFF8A1F, 0x9B55E6, 0xB6FF6B, false), Motion.RISE,
			new String[] {"●", "✦", "☾"}, new int[] {0xFF8A1F, 0x9B55E6, 0xB6FF6B}),
	AUTUMN("Harvest", new Palette(0xF0A030, 0xC2452D, 0xFFD27A, false), Motion.FALL,
			new String[] {"❧", "❦"}, new int[] {0xF0A030, 0xC2452D, 0xE0702A, 0x9A5A2A}),
	MOON("Harvest moon", new Palette(0x8C9BFF, 0xFFE08A, 0xFFF4C9, false), Motion.TWINKLE,
			new String[] {"✦", "•", "☾"}, new int[] {0xFFE08A, 0xFFF4C9, 0xB8C2FF}),
	STARS("Star festival", new Palette(0x7FB2FF, 0xD59BFF, 0xFFF2A8, false), Motion.TWINKLE,
			new String[] {"★", "✦", "✧"}, new int[] {0xFFF2A8, 0xBFD8FF, 0xE6C2FF}),
	LANTERNS("Lanterns", new Palette(0xFF6B8B, 0xFFC46B, 0xFFF0B3, false), Motion.RISE,
			new String[] {"●"}, new int[] {0xFF6B8B, 0xFFC46B, 0xFFE66B, 0x8BE38B, 0x8FC2FF}),
	NATIONAL("Flag day", null, Motion.BURST,
			new String[] {"✦", "▪", "•"}, null),
	ST_PATRICK("St Patrick's", new Palette(0x2FBF5A, 0xB4F08C, 0xFFD36B, false), Motion.FALL,
			new String[] {"☘"}, new int[] {0x2FBF5A, 0x7FDB6A, 0xB4F08C}),
	DAY_OF_THE_DEAD("Day of the Dead", new Palette(0xFF9F1C, 0xE0457B, 0xFFD36B, false), Motion.FALL,
			new String[] {"✿", "❀"}, new int[] {0xFF9F1C, 0xFFC93F, 0xE0457B}),
	DIWALI("Diwali", new Palette(0xFFB13B, 0xFF6A3D, 0xFFF0A0, false), Motion.TWINKLE,
			new String[] {"●", "✦"}, new int[] {0xFFB13B, 0xFFE07A, 0xFF7A3D}),
	HOLI("Holi", Palette.bands(0xFFFFFF, 0xFF4FA3, 0xFFD23F, 0x3FD0FF, 0x7CFF6B, 0xB36BFF), Motion.BURST,
			new String[] {"●", "•"}, new int[] {0xFF4FA3, 0xFFD23F, 0x3FD0FF, 0x7CFF6B, 0xB36BFF}),
	CARNIVAL("Carnival", Palette.bands(0xFFFFFF, 0xFF4F6D, 0xFFC93F, 0x4FD1FF, 0x8BFF6B, 0xC77DFF), Motion.FALL,
			new String[] {"▪", "✦"}, new int[] {0xFF4F6D, 0xFFC93F, 0x4FD1FF, 0x8BFF6B, 0xC77DFF}),
	BLOSSOM("Spring blossoms", new Palette(0xFF9EC7, 0xFFE6F0, 0x9BE38B, false), Motion.FALL,
			new String[] {"✿", "❀"}, new int[] {0xFFB7D5, 0xFFE6F0, 0xFF8FBF}),
	RED_AND_WHITE("Spring tokens", Palette.bands(0xFFFFFF, 0xE8343F, 0xF4F4F4), Motion.FALL,
			new String[] {"✿", "•"}, new int[] {0xE8343F, 0xF4F4F4}),
	BONFIRE("Bonfire night", new Palette(0xFF8A1F, 0xFFD36B, 0xFF5A3C, false), Motion.BURST,
			new String[] {"✦", "•"}, new int[] {0xFF8A1F, 0xFFD36B, 0xFF5A3C, 0xFFFFFF}),
	DRAGON_BOAT("Dragon Boat", new Palette(0x33C9A0, 0x3F8FE8, 0xFFD36B, false), Motion.RISE,
			new String[] {"•", "✦"}, new int[] {0x8FE8D8, 0x3F8FE8, 0xFFD36B});

	/** How decorations move. */
	public enum Motion {
		/** Drift down and sway (snow, leaves, petals, confetti). */
		FALL,
		/** Float up and fade (lanterns, hearts, embers). */
		RISE,
		/** Stay put and twinkle (stars, fireflies, lamps). */
		TWINKLE,
		/** Burst outwards like fireworks. */
		BURST
	}

	private final String label;
	private final Palette palette;
	private final Motion motion;
	private final String[] glyphs;
	private final int[] colors;

	HolidayTheme(String label, Palette palette, Motion motion, String[] glyphs, int[] colors) {
		this.label = label;
		this.palette = palette;
		this.motion = motion;
		this.glyphs = glyphs;
		this.colors = colors;
	}

	public String label() {
		return label;
	}

	public String translationKey() {
		return "artlyrics.holiday_theme." + name().toLowerCase(Locale.ROOT);
	}

	/** Smaller celebrations, which give way to a bigger holiday nearby. */
	public boolean minor() {
		return switch (this) {
			case VALENTINE, CARNIVAL, BLOSSOM, RED_AND_WHITE, BONFIRE, STARS, ST_PATRICK -> true;
			default -> false;
		};
	}

	public Motion motion() {
		return motion;
	}

	public String[] glyphs() {
		return glyphs.clone();
	}

	/** The colors for this theme; flag days use the region's flag. */
	public Palette palette(Region region) {
		if (this == NATIONAL) {
			int[] flag = region.flag();
			return Palette.bands(flag.length > 1 ? flag[1] : 0xFFFFFF, flag);
		}
		return palette;
	}

	/** Decoration colors; flag days use the flag's colors. */
	public int[] decorationColors(Region region) {
		return this == NATIONAL ? region.flag() : colors.clone();
	}
}
