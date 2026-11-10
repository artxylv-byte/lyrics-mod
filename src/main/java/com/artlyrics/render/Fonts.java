package com.artlyrics.render;

import com.artlyrics.compat.Mc;
import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.ArtlyricsConfig.TextFont;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Font lookups. Every bundled font comes in two versions: a high-resolution one for text in the world (it
 * gets big on screen) and a lighter one for menus and the song card.
 */
public final class Fonts {
	private static final FontDescription UNIFORM = Mc.vanillaFont("uniform");
	private static final Map<TextFont, FontDescription> WORLD = new EnumMap<>(TextFont.class);
	private static final Map<TextFont, FontDescription> UI = new EnumMap<>(TextFont.class);

	static {
		for (TextFont font : TextFont.values()) {
			if (font.bundled != null) {
				WORLD.put(font, Mc.modFont(font.bundled));
				UI.put(font, Mc.modFont(font.bundled + "_ui"));
			}
		}
	}

	private Fonts() {
	}

	/** Font for text in the world, or null for Minecraft's default font. */
	public static @Nullable FontDescription world(TextFont font) {
		return font == TextFont.PIXEL ? UNIFORM : WORLD.get(font);
	}

	/** Font for menus and HUD, or null for Minecraft's default font. */
	public static @Nullable FontDescription ui(TextFont font) {
		return font == TextFont.PIXEL ? UNIFORM : UI.get(font);
	}

	/** The text style for lyrics with the user's font, bold and italic settings. */
	public static Style lyricStyle(ArtlyricsConfig config, boolean forUi) {
		Style style = Style.EMPTY.withBold(config.bold).withItalic(config.italic);
		FontDescription font = forUi ? ui(config.font) : world(config.font);
		return font == null ? style : style.withFont(font);
	}

	/** A plain style using {@code font} for menus. */
	public static Style uiStyle(@Nullable TextFont font) {
		FontDescription description = font == null ? null : ui(font);
		return description == null ? Style.EMPTY : Style.EMPTY.withFont(description);
	}
}
