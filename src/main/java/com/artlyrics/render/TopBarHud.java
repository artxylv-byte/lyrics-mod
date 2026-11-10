package com.artlyrics.render;

import com.artlyrics.Artlyrics;
import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.LyricStyle;
import com.artlyrics.lyrics.LyricLine;
import com.artlyrics.lyrics.LyricWord;
import com.artlyrics.lyrics.Lyrics;
import com.artlyrics.nowplaying.NowPlaying;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;

import java.util.List;

/**
 * A small bar at the top of the screen with the current line, the word being sung highlighted,
 * and a thin song-progress line. When there are no synced lyrics it shows what's playing and why.
 */
public final class TopBarHud implements HudElement {
	private static final int HEIGHT = 15;
	private static final int TOP = 3;
	private static final int PADDING = 6;
	private static final String NOTE = "♪";

	@Override
	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Artlyrics mod = Artlyrics.get();
		Minecraft mc = Minecraft.getInstance();
		if (mod == null || mc.options.hideGui) {
			return;
		}
		ArtlyricsConfig config = mod.config();
		if (!config.enabled || !config.topBar) {
			return;
		}
		mod.tracker().frame(System.nanoTime());
		NowPlaying track = mod.tracker().track();
		if (track == null) {
			return;
		}

		Font font = mc.font;
		Style style = LineLayout.textStyle(config);
		LyricStyle look = config.style;
		long timeMs = mod.tracker().positionMs() + config.offsetMs;

		LyricLine line = currentLine(mod.lyrics().lyrics(), timeMs);
		int maxWidth = graphics.guiWidth() - 20;

		if (line != null) {
			drawLyricLine(graphics, font, style, look, line, timeMs, track, maxWidth);
		} else {
			drawStatus(graphics, font, look, mod, track, maxWidth);
		}
	}

	private static LyricLine currentLine(Lyrics lyrics, long timeMs) {
		if (lyrics == null) {
			return null;
		}
		int index = lyrics.lineIndexAt(timeMs);
		if (index < 0) {
			return null;
		}
		LyricLine line = lyrics.lines().get(index);
		return line.isBlank() || timeMs >= line.visibleUntil() ? null : line;
	}

	private static void drawLyricLine(GuiGraphics graphics, Font font, Style style, LyricStyle look, LyricLine line,
									  long timeMs, NowPlaying track, int maxWidth) {
		List<LyricWord> words = line.words();
		int[] widths = new int[words.size()];
		Component[] parts = new Component[words.size()];
		int textWidth = 0;
		for (int i = 0; i < words.size(); i++) {
			parts[i] = Component.literal(words.get(i).text()).withStyle(style);
			widths[i] = font.width(parts[i]);
			textWidth += widths[i];
		}
		int noteWidth = font.width(NOTE) + 4;
		int innerMax = maxWidth - PADDING * 2 - noteWidth;
		// Very long lines: show a window of words around the one being sung.
		int first = 0;
		int last = words.size() - 1;
		int active = line.wordIndexAt(timeMs);
		while (textWidth > innerMax && first < last) {
			if (active - first > last - active) {
				textWidth -= widths[first++];
			} else {
				textWidth -= widths[last--];
			}
		}

		int width = textWidth + noteWidth + PADDING * 2;
		int x0 = (graphics.guiWidth() - width) / 2;
		drawPanel(graphics, x0, width, look, track);
		int textY = TOP + (HEIGHT - 8) / 2;
		graphics.drawString(font, NOTE, x0 + PADDING, textY, ARGB.opaque(look.from), false);

		int x = x0 + PADDING + noteWidth;
		for (int i = first; i <= last; i++) {
			LyricWord w = words.get(i);
			boolean sung = timeMs >= w.startMs();
			boolean current = w.isActiveAt(timeMs);
			int color;
			if (current) {
				int wordWidth = font.width(Component.literal(w.trimmed()).withStyle(style));
				graphics.fill(x - 2, TOP + 2, x + wordWidth + 2, TOP + HEIGHT - 2, ARGB.color(230, look.highlight == 0xFFFFFF ? look.from : look.highlight));
				color = 0xFF1A1426;
			} else if (sung) {
				color = 0xFFFFFFFF;
			} else {
				color = 0xFF9A98A8;
			}
			graphics.drawString(font, parts[i], x, textY, color, !current);
			x += widths[i];
		}
	}

	private static void drawStatus(GuiGraphics graphics, Font font, LyricStyle look, Artlyrics mod, NowPlaying track, int maxWidth) {
		String status = switch (mod.lyrics().state()) {
			case WAITING, SEARCHING -> "Searching lyrics…";
			case INSTRUMENTAL -> "Instrumental";
			case UNSYNCED_ONLY -> "Only unsynced lyrics found";
			case NOT_FOUND -> "No lyrics found";
			case ERROR -> "Couldn't reach LRCLIB";
			case FOUND, IDLE -> null;
		};
		String title = track.displayName();
		String text = status == null ? title : title + "  ·  " + status;
		int noteWidth = font.width(NOTE) + 4;
		String fitted = font.plainSubstrByWidth(text, maxWidth - PADDING * 2 - noteWidth);
		if (!fitted.equals(text)) {
			fitted = font.plainSubstrByWidth(text, maxWidth - PADDING * 2 - noteWidth - font.width("…")) + "…";
		}
		int width = font.width(fitted) + noteWidth + PADDING * 2;
		int x0 = (graphics.guiWidth() - width) / 2;
		drawPanel(graphics, x0, width, look, track);
		int textY = TOP + (HEIGHT - 8) / 2;
		graphics.drawString(font, NOTE, x0 + PADDING, textY, ARGB.opaque(look.from), false);
		graphics.drawString(font, fitted, x0 + PADDING + noteWidth, textY, 0xFFD8D6E4, true);
	}

	/** Dark rounded panel with a thin progress line along the bottom. */
	private static void drawPanel(GuiGraphics graphics, int x0, int width, LyricStyle look, NowPlaying track) {
		int x1 = x0 + width;
		int y0 = TOP;
		int y1 = TOP + HEIGHT;
		int bg = 0xB4100E18;
		graphics.fill(x0 + 1, y0, x1 - 1, y1, bg);
		graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, bg);
		graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, bg);

		if (track.durationMs() > 0) {
			Artlyrics mod = Artlyrics.get();
			float progress = Math.min(1f, mod.tracker().positionMs() / (float) track.durationMs());
			int px = x0 + 2 + Math.round((width - 4) * progress);
			graphics.fill(x0 + 2, y1 - 1, x1 - 2, y1, 0x40FFFFFF);
			graphics.fill(x0 + 2, y1 - 1, px, y1, ARGB.opaque(look.from));
		}
	}
}
