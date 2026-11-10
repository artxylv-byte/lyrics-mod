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

import java.util.ArrayList;
import java.util.List;

/**
 * A compact "now playing" card in a corner of the screen: title, artist, a progress bar with times, and the
 * current lyric line with the sung words in the palette colors. Between lines it previews the next one.
 */
public final class SongCardHud implements HudElement {
	private static final int MAX_WIDTH = 170;
	private static final int MARGIN = 6;
	private static final int PAD = 5;
	private static final int STRIPE = 2;
	private static final int ROW = 10;
	private static final int MAX_LYRIC_ROWS = 2;

	private static final int BACKGROUND = 0xC0141019;
	private static final int TITLE = 0xFFF4F2FA;
	private static final int MUTED = 0xFF9C99AC;
	private static final int UNSUNG = 0xFF8E8B9C;

	@Override
	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Artlyrics mod = Artlyrics.get();
		Minecraft mc = Minecraft.getInstance();
		if (mod == null || mc.options.hideGui) {
			return;
		}
		ArtlyricsConfig config = mod.config();
		if (!config.enabled || !config.songCard) {
			return;
		}
		mod.tracker().frame(System.nanoTime());
		NowPlaying track = mod.tracker().track();
		if (track == null) {
			return;
		}

		Font font = mc.font;
		LyricStyle look = config.style;
		Style style = LineLayout.textStyle(config);
		long timeMs = mod.tracker().positionMs() + config.offsetMs;

		int width = Math.min(MAX_WIDTH, graphics.guiWidth() - MARGIN * 2);
		int inner = width - PAD * 2 - STRIPE - 2;

		// Lyric rows: the current line, else a dimmed preview of the next one, else a status message.
		List<List<Piece>> lyricRows = new ArrayList<>();
		String status = null;
		Lyrics lyrics = mod.lyrics().lyrics();
		LyricLine current = currentLine(lyrics, timeMs);
		if (current != null) {
			lyricRows = wrap(current, font, style, look, timeMs, inner, true);
		} else {
			LyricLine next = nextLine(lyrics, timeMs);
			if (next != null) {
				lyricRows = wrap(next, font, style, look, -1, inner, false);
			} else {
				status = statusText(mod, track);
			}
		}

		boolean hasArtist = !track.artist().isBlank();
		int height = PAD + ROW + (hasArtist ? ROW : 0) + ROW + 3
				+ (status != null ? ROW : lyricRows.size() * ROW) + PAD - 2;

		int x = switch (config.cardCorner) {
			case TOP_LEFT -> MARGIN;
			case TOP_RIGHT, BOTTOM_RIGHT -> graphics.guiWidth() - width - MARGIN;
		};
		int y = switch (config.cardCorner) {
			case TOP_LEFT -> MARGIN;
			// Keep clear of the status effect icons in the top right.
			case TOP_RIGHT -> MARGIN + (mc.player != null && !mc.player.getActiveEffects().isEmpty() ? 52 : 0);
			case BOTTOM_RIGHT -> graphics.guiHeight() - height - MARGIN;
		};

		graphics.fill(x, y, x + width, y + height, BACKGROUND);
		graphics.fillGradient(x, y, x + STRIPE, y + height, ARGB.opaque(look.from), ARGB.opaque(look.to));

		int tx = x + STRIPE + PAD;
		int ty = y + PAD;
		graphics.drawString(font, ellipsize(font, "♫ " + track.title(), inner), tx, ty, TITLE, false);
		ty += ROW;
		if (hasArtist) {
			graphics.drawString(font, ellipsize(font, track.artist(), inner), tx, ty, MUTED, false);
			ty += ROW;
		}

		drawProgress(graphics, font, look, track, mod.tracker().positionMs(), tx, ty, inner);
		ty += ROW + 3;

		if (status != null) {
			graphics.drawString(font, Component.literal(ellipsize(font, status, inner)).withStyle(s -> s.withItalic(true)), tx, ty, MUTED, false);
			return;
		}
		for (List<Piece> row : lyricRows) {
			int px = tx;
			for (Piece piece : row) {
				graphics.drawString(font, piece.text, px, ty, piece.color, false);
				px += piece.width;
			}
			ty += ROW;
		}
	}

	private record Piece(Component text, int width, int color) {
	}

	/**
	 * Splits a line into at most {@link #MAX_LYRIC_ROWS} rows that fit the card. If the line is longer, the rows
	 * start late enough to include the word being sung.
	 */
	private static List<List<Piece>> wrap(LyricLine line, Font font, Style style, LyricStyle look, long timeMs, int maxWidth, boolean live) {
		List<LyricWord> words = line.words();
		int n = words.size();
		int active = live ? line.wordIndexAt(timeMs) : -1;
		double seconds = System.nanoTime() / 1e9;

		Piece[] pieces = new Piece[n];
		for (int i = 0; i < n; i++) {
			LyricWord w = words.get(i);
			Component text = Component.literal(w.text()).withStyle(style);
			int color;
			if (!live) {
				color = UNSUNG;
			} else if (i == active && w.isActiveAt(timeMs)) {
				color = ARGB.opaque(look.accent);
			} else if (timeMs >= w.startMs()) {
				color = ARGB.opaque(look.colorAt(n > 1 ? i / (float) (n - 1) : 0.5f, seconds));
			} else {
				color = UNSUNG;
			}
			pieces[i] = new Piece(text, font.width(text), color);
		}

		for (int start = 0; start < n; start++) {
			List<List<Piece>> rows = new ArrayList<>();
			List<Piece> row = new ArrayList<>();
			int rowWidth = 0;
			int lastIncluded = start - 1;
			for (int i = start; i < n; i++) {
				if (!row.isEmpty() && rowWidth + pieces[i].width > maxWidth) {
					rows.add(row);
					if (rows.size() == MAX_LYRIC_ROWS) {
						row = null;
						break;
					}
					row = new ArrayList<>();
					rowWidth = 0;
				}
				row.add(pieces[i]);
				rowWidth += pieces[i].width;
				lastIncluded = i;
			}
			if (row != null && !row.isEmpty()) {
				rows.add(row);
			}
			if (active <= lastIncluded || start == n - 1) {
				return rows;
			}
		}
		return List.of();
	}

	private static void drawProgress(GuiGraphics graphics, Font font, LyricStyle look, NowPlaying track, long positionMs, int x, int y, int width) {
		String elapsed = clock(positionMs);
		String total = track.durationMs() > 0 ? clock(track.durationMs()) : "--:--";
		int left = font.width(elapsed) + 4;
		int right = font.width(total) + 4;
		graphics.drawString(font, elapsed, x, y, MUTED, false);
		graphics.drawString(font, total, x + width - font.width(total), y, MUTED, false);

		int barX0 = x + left;
		int barX1 = x + width - right;
		int barY = y + 3;
		graphics.fill(barX0, barY, barX1, barY + 2, 0x40FFFFFF);
		if (track.durationMs() > 0 && barX1 > barX0) {
			float progress = Math.min(1f, positionMs / (float) track.durationMs());
			int filled = barX0 + Math.round((barX1 - barX0) * progress);
			graphics.fill(barX0, barY, filled, barY + 2, ARGB.opaque(look.from));
			graphics.fill(Math.max(barX0, filled - 1), barY - 1, Math.min(barX1, filled + 1), barY + 3, ARGB.opaque(look.accent));
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

	private static LyricLine nextLine(Lyrics lyrics, long timeMs) {
		if (lyrics == null) {
			return null;
		}
		for (int i = lyrics.lineIndexAt(timeMs) + 1; i < lyrics.lines().size(); i++) {
			LyricLine line = lyrics.lines().get(i);
			if (!line.isBlank()) {
				return line;
			}
		}
		return null;
	}

	private static String statusText(Artlyrics mod, NowPlaying track) {
		return switch (mod.lyrics().state()) {
			case WAITING, SEARCHING -> "Looking up lyrics…";
			case INSTRUMENTAL -> "Instrumental";
			case UNSYNCED_ONLY -> "No timed lyrics for this song";
			case NOT_FOUND -> "No lyrics found";
			case ERROR -> "Lyrics service unreachable";
			case FOUND, IDLE -> track.playing() ? "♪" : "Paused";
		};
	}

	private static String ellipsize(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
	}

	private static String clock(long ms) {
		long seconds = Math.max(0, ms / 1000);
		return seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
	}
}
