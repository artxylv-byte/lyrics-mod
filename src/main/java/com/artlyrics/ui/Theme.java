package com.artlyrics.ui;

import com.artlyrics.config.ArtlyricsConfig.MenuTheme;
import com.artlyrics.config.Palette;

/**
 * Colors of the settings screen and song card for the chosen menu theme (dark, light, midnight, glass, ...). The
 * accent comes from the chosen palette, made darker on light themes so it stays readable.
 */
public final class Theme {
	private Theme() {
	}

	public static int SCRIM;
	public static int PANEL;
	public static int SIDEBAR;
	public static int CARD;
	public static int CARD_HOVER;
	public static int CONTROL;
	public static int CONTROL_HOVER;
	public static int LINE;
	public static int TEXT;
	public static int MUTED;
	public static int FAINT;
	public static int OFF;
	public static int GOOD;
	public static int WARN;
	public static int BAD;
	/** RGB used (with low alpha) for hover highlights and thin outlines: white on dark themes, dark on light ones. */
	public static int HIGHLIGHT;
	/** RGB of the song card's background. */
	public static int CARD_BG;
	/** Background behind the card preview. */
	public static int PREVIEW_BG;
	public static boolean LIGHT;

	private static MenuTheme current;

	static {
		apply(MenuTheme.DARK);
	}

	/** Switches to a theme (cheap when it's already the current one). */
	public static void apply(MenuTheme theme) {
		if (theme == current) {
			return;
		}
		current = theme;
		// Defaults: the dark theme.
		SCRIM = 0x99050409;
		PANEL = 0xF2120F1A;
		SIDEBAR = 0xFF0C0A12;
		CARD = 0xFF1B1826;
		CARD_HOVER = 0xFF242033;
		CONTROL = 0xFF25212F;
		CONTROL_HOVER = 0xFF2F2A3D;
		LINE = 0x22FFFFFF;
		TEXT = 0xFFF2F0FA;
		MUTED = 0xFF9893AE;
		FAINT = 0xFF5F5A73;
		OFF = 0xFF3A3648;
		GOOD = 0xFF7FE0A4;
		WARN = 0xFFFFC26B;
		BAD = 0xFFFF8080;
		HIGHLIGHT = 0xFFFFFF;
		CARD_BG = 0x14111C;
		PREVIEW_BG = 0xFF0A0910;
		LIGHT = false;
		switch (theme) {
			case LIGHT -> light(0xF7F7F5FA, 0xFFECEAF2, 0xFFFFFFFF, 0xFFF0EEF6, 0xFFE4E1EC, 0xFFD9D5E3, 0xFF1C1A24, 0xFF5F5A70,
					0xFF9893A8, 0xFFC9C5D4, 0x000000, 0xF4F2F8, 0xFFE6E3EE);
			case SAKURA -> light(0xF7FFF3F7, 0xFFFBE4EE, 0xFFFFFFFF, 0xFFFFEDF4, 0xFFF7DCE7, 0xFFF0CCDA, 0xFF3A1F2B, 0xFF84606F,
					0xFFB894A3, 0xFFE8C3D1, 0x501030, 0xFFF1F6, 0xFFF5E1EA);
			case MIDNIGHT -> dark(0xFA000000, 0xFF000000, 0xFF0E0E12, 0xFF18181E, 0xFF16161C, 0xFF202028, 0xFFF5F5F7,
					0xFF8E8E99, 0xFF55555E, 0xFF2A2A32, 0x000000, 0xFF050507);
			case GLASS -> {
				dark(0xB0120F1A, 0x980C0A12, 0x801B1826, 0x98242033, 0x8025212F, 0x982F2A3D, 0xFFF4F2FC, 0xFFA8A3BE,
						0xFF6F6A83, 0xC03A3648, 0x14111C, 0x700A0910);
				SCRIM = 0x30050409; // the world stays visible behind the menu
			}
			case OCEAN -> dark(0xF20E1726, 0xFF0A111C, 0xFF16223A, 0xFF1D2B48, 0xFF1A2740, 0xFF22324F, 0xFFEEF4FF,
					0xFF8FA3C2, 0xFF5A6C88, 0xFF2E3D58, 0x0E1726, 0xFF070D17);
			case FOREST -> dark(0xF20F1A14, 0xFF0A130E, 0xFF16261C, 0xFF1D3024, 0xFF1A2C21, 0xFF23392B, 0xFFEFF8F1,
					0xFF8FB19A, 0xFF5B7A64, 0xFF2D4234, 0x0F1A14, 0xFF07100A);
			default -> {
			}
		}
	}

	private static void dark(int panel, int sidebar, int card, int cardHover, int control, int controlHover, int text,
							 int muted, int faint, int off, int cardBg, int previewBg) {
		PANEL = panel;
		SIDEBAR = sidebar;
		CARD = card;
		CARD_HOVER = cardHover;
		CONTROL = control;
		CONTROL_HOVER = controlHover;
		TEXT = text;
		MUTED = muted;
		FAINT = faint;
		OFF = off;
		CARD_BG = cardBg;
		PREVIEW_BG = previewBg;
	}

	private static void light(int panel, int sidebar, int card, int cardHover, int control, int controlHover, int text,
							  int muted, int faint, int off, int highlight, int cardBg, int previewBg) {
		dark(panel, sidebar, card, cardHover, control, controlHover, text, muted, faint, off, cardBg, previewBg);
		SCRIM = 0x55101018;
		LINE = 0x22000000;
		GOOD = 0xFF1F9D57;
		WARN = 0xFFB7791F;
		BAD = 0xFFD04545;
		HIGHLIGHT = highlight;
		LIGHT = true;
	}

	/** A readable accent derived from the palette color (keeps very dark or very light colors visible). */
	public static int accent(int rgb) {
		float[] hsv = Palette.toHsv(rgb);
		if (LIGHT) {
			return 0xFF000000 | Palette.hsv(hsv[0], Math.max(0.5f, Math.min(hsv[1] + 0.2f, 0.85f)), Math.min(hsv[2], 0.62f));
		}
		float v = Math.max(hsv[2], 0.75f);
		return 0xFF000000 | Palette.hsv(hsv[0], Math.min(hsv[1], 0.75f), v);
	}

	/** A lyric color made readable on the current theme's background (darkened on light themes). */
	public static int readable(int argb) {
		if (!LIGHT) {
			return argb;
		}
		float[] hsv = Palette.toHsv(argb & 0xFFFFFF);
		int rgb = Palette.hsv(hsv[0], Math.max(hsv[1], hsv[1] < 0.08f ? 0f : 0.45f), Math.min(hsv[2], 0.55f));
		return (argb & 0xFF000000) | rgb;
	}
}
