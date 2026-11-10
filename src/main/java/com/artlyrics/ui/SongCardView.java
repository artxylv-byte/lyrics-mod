package com.artlyrics.ui;

import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.ArtlyricsConfig.TextFont;
import com.artlyrics.config.Palette;
import com.artlyrics.lyrics.LyricLine;
import com.artlyrics.lyrics.LyricWord;
import com.artlyrics.lyrics.Lyrics;
import com.artlyrics.nowplaying.NowPlaying;
import com.artlyrics.render.Easing;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The "now playing" card: a gradient art tile, title, artist, progress with times and the current lyric line
 * (sung words in the palette, the rest faint). Between lines it can preview the next one. It fades in and out,
 * and its height glides between one and two lyric rows instead of jumping.
 */
public final class SongCardView {
	public static final int WIDTH = 176;
	private static final int MARGIN = 6;
	private static final int PAD = 6;
	private static final int ART = 22;
	private static final int ROW = 10;
	private static final int MAX_LYRIC_ROWS = 2;
	private static final TextFont FONT = TextFont.SOFT;

	private float appear;
	private long lastNanos;
	private boolean hasFrame;
	private float dt;
	private @Nullable NowPlaying lastTrack;
	private int lineKey = Integer.MIN_VALUE;
	private long lineChangedNanos;
	private float shownRows = -1;

	// Content measured for the frame being drawn.
	private List<List<Piece>> rows = List.of();
	private @Nullable String status;

	private record Piece(String text, int width, int color) {
	}

	private void tick(long now) {
		dt = hasFrame ? Math.min(0.25f, (now - lastNanos) / 1e9f) : 0f;
		lastNanos = now;
		hasFrame = true;
	}

	/** Draws the card in its corner of the HUD, fading it in and out as music starts and stops. */
	public void drawHud(Canvas c, UiHost host, long now) {
		ArtlyricsConfig config = host.config();
		tick(now);

		NowPlaying track = host.track();
		boolean wanted = track != null && config.enabled && config.songCard;
		appear += ((wanted ? 1f : 0f) - appear) * Easing.approach(dt, 9f);
		if (track != null) {
			lastTrack = track;
		}
		if (appear < 0.01f || lastTrack == null || !config.songCard) {
			return;
		}

		float s = config.cardScale;
		int height = measure(c, host, now, false);
		int w = Math.round(WIDTH * s);
		int h = Math.round(height * s);
		int x = switch (config.cardCorner) {
			case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
			case TOP_CENTER -> (c.width() - w) / 2;
			case TOP_RIGHT, BOTTOM_RIGHT -> c.width() - w - MARGIN;
		};
		int y = switch (config.cardCorner) {
			case TOP_LEFT, TOP_RIGHT -> MARGIN;
			case TOP_CENTER -> MARGIN + 18; // clear of boss bar titles
			case BOTTOM_LEFT -> c.height() - h - 48; // above the chat input
			case BOTTOM_RIGHT -> c.height() - h - MARGIN;
		};
		float eased = Easing.inOutSine(appear);
		boolean top = config.cardCorner != ArtlyricsConfig.CardCorner.BOTTOM_LEFT && config.cardCorner != ArtlyricsConfig.CardCorner.BOTTOM_RIGHT;

		c.push();
		c.translate(x, y + (1f - eased) * (top ? -6f : 6f));
		c.scale(s);
		float alpha = c.alpha();
		c.setAlpha(alpha * eased);
		drawCard(c, host, lastTrack, 0, 0, WIDTH, height, now);
		c.setAlpha(alpha);
		c.pop();
	}

	/**
	 * Works out what the card shows this frame and returns its height (unscaled GUI pixels). Call once per frame
	 * before {@link #drawCard}. {@code preview} uses its own clock for the settings screen.
	 */
	public int measure(Canvas c, UiHost host, long now, boolean preview) {
		if (preview) {
			tick(now);
		}
		ArtlyricsConfig config = host.config();
		computeContent(c, host, WIDTH - PAD * 2, now);
		int target = !rows.isEmpty() ? rows.size() : status != null ? 1 : 0;
		shownRows = shownRows < 0 ? target : shownRows + (target - shownRows) * Easing.approach(dt, 10f);
		int h = PAD + ART + PAD + (config.cardProgress ? ROW : 0);
		return h + Math.round(shownRows * ROW) + (shownRows > 0.05f ? PAD - 2 : 0);
	}

	/** Draws the card with its top-left at (x, y), unscaled, using the content from {@link #measure}. */
	public void drawCard(Canvas c, UiHost host, NowPlaying track, int x, int y, int width, int height, long now) {
		ArtlyricsConfig config = host.config();
		Palette palette = config.palette();
		int accent = Theme.accent(palette.from());

		Draw.roundRect(c, x, y, x + width, y + height, 5, Draw.withAlpha(0x14111C, config.cardOpacity));
		Draw.roundOutline(c, x, y, x + width, y + height, 5, Draw.withAlpha(0xFFFFFF, 0.07f));

		// Art tile: palette gradient with a note.
		int ax = x + PAD;
		int ay = y + PAD;
		double seconds = now / 1e9;
		c.gradientV(ax, ay, ax + ART, ay + ART, 0xFF000000 | palette.colorAt(0f, seconds), 0xFF000000 | palette.colorAt(1f, seconds));
		c.fill(ax, ay, ax + ART, ay + ART, 0x1A000000);
		c.icon("note", ax + (ART - 10) / 2, ay + (ART - 10) / 2, 0xF0FFFFFF);

		int tx = ax + ART + 6;
		int textWidth = x + width - PAD - tx;
		c.text(Draw.ellipsize(c, track.title(), FONT, textWidth), FONT, tx, ay + 2, Theme.TEXT, false);
		String sub = config.cardArtist ? track.artist() : "";
		if (!track.playing()) {
			sub = sub.isBlank() ? host.tr("artlyrics.card.paused") : sub + "  ·  " + host.tr("artlyrics.card.paused");
		}
		if (!sub.isBlank()) {
			c.text(Draw.ellipsize(c, sub, FONT, textWidth), FONT, tx, ay + 13, Theme.MUTED, false);
		}

		int cy = ay + ART + PAD;
		if (config.cardProgress) {
			drawProgress(c, track, host.positionMs(), x + PAD, cy, width - PAD * 2, accent, palette);
			cy += ROW;
		}
		drawLyrics(c, x + PAD, cy, width - PAD * 2, cardFont(config.font), now);
	}

	private void drawProgress(Canvas c, NowPlaying track, long positionMs, int x, int y, int width, int accent, Palette palette) {
		String elapsed = clock(positionMs);
		String total = track.durationMs() > 0 ? clock(track.durationMs()) : "-:--";
		int left = c.textWidth(elapsed, null) + 5;
		int right = c.textWidth(total, null) + 5;
		c.text(elapsed, null, x, y, Theme.MUTED, false);
		c.text(total, null, x + width - c.textWidth(total, null), y, Theme.MUTED, false);
		int bx0 = x + left;
		int bx1 = x + width - right;
		int by = y + 3;
		Draw.roundRect(c, bx0, by, bx1, by + 3, 1, 0x30FFFFFF);
		if (track.durationMs() > 0 && bx1 > bx0) {
			float p = Math.min(1f, positionMs / (float) track.durationMs());
			int filled = bx0 + Math.round((bx1 - bx0) * p);
			Draw.gradientH(c, bx0, by, Math.max(bx0 + 1, filled), by + 3, 0xFF000000 | palette.from(), 0xFF000000 | palette.to());
			Draw.circle(c, filled, by + 1.5f, 2.5f, accent);
		}
	}

	private void drawLyrics(Canvas c, int x, int y, int width, TextFont font, long now) {
		float fade = Easing.inOutSine((now - lineChangedNanos) / 220_000_000f);
		float alpha = c.alpha();
		if (rows.isEmpty()) {
			if (status != null && shownRows > 0.05f) {
				c.setAlpha(alpha * fade * Math.min(1f, shownRows));
				c.text(Draw.ellipsize(c, status, FONT, width), FONT, x, y + 2, Theme.MUTED, false);
				c.setAlpha(alpha);
			}
			return;
		}
		int ry = y + 2;
		for (int r = 0; r < rows.size(); r++) {
			// Rows that don't fit yet (the card is still growing) fade in as it does.
			float fit = Easing.clamp01(shownRows - r);
			c.setAlpha(alpha * fade * fit);
			int px = x;
			for (Piece piece : rows.get(r)) {
				c.text(piece.text, font, px, ry, piece.color, false);
				px += piece.width;
			}
			ry += ROW;
		}
		c.setAlpha(alpha);
	}

	private void computeContent(Canvas c, UiHost host, int width, long now) {
		ArtlyricsConfig config = host.config();
		Lyrics lyrics = host.lyrics();
		long timeMs = host.positionMs() + config.offsetMs;
		long lingerMs = Math.round(config.lingerSeconds * 1000);

		LyricLine line = null;
		boolean live = false;
		int key = -1;
		if (lyrics != null) {
			int index = lyrics.lineIndexAt(timeMs);
			if (index >= 0 && !lyrics.lines().get(index).isBlank() && timeMs < lyrics.visibleEnd(index, lingerMs)) {
				line = lyrics.lines().get(index);
				live = true;
				key = index;
			} else if (config.cardNextLine) {
				for (int i = index + 1; i < lyrics.lines().size(); i++) {
					if (!lyrics.lines().get(i).isBlank()) {
						line = lyrics.lines().get(i);
						key = 100_000 + i;
						break;
					}
				}
			}
		}
		if (key != lineKey) {
			lineKey = key;
			lineChangedNanos = now;
		}
		if (line == null) {
			rows = List.of();
			status = status(host);
		} else {
			rows = wrap(c, line, config, timeMs, width, live, cardFont(config.font), now);
			status = null;
		}
	}

	/** Wraps a line into at most two rows; long lines start late enough to include the word being sung. */
	private static List<List<Piece>> wrap(Canvas c, LyricLine line, ArtlyricsConfig config, long timeMs, int maxWidth,
										  boolean live, TextFont font, long now) {
		List<LyricWord> words = line.words();
		int n = words.size();
		int active = live ? line.wordIndexAt(timeMs) : -1;
		Palette palette = config.palette();
		double seconds = now / 1e9;
		Piece[] pieces = new Piece[n];
		for (int i = 0; i < n; i++) {
			LyricWord w = words.get(i);
			String text = config.uppercase ? w.text().toUpperCase(Locale.ROOT) : w.text();
			int color;
			if (!live) {
				color = Theme.FAINT;
			} else if (i == active && w.isActiveAt(timeMs)) {
				color = 0xFF000000 | palette.accent();
			} else if (timeMs >= w.startMs()) {
				color = 0xFF000000 | palette.colorAt(n > 1 ? i / (float) (n - 1) : 0.5f, seconds);
			} else {
				color = Theme.FAINT;
			}
			pieces[i] = new Piece(text, c.textWidth(text, font), color);
		}
		for (int start = 0; start < n; start++) {
			List<List<Piece>> rows = new ArrayList<>();
			List<Piece> row = new ArrayList<>();
			int rowWidth = 0;
			int last = start - 1;
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
				last = i;
			}
			if (row != null && !row.isEmpty()) {
				rows.add(row);
			}
			if (active <= last || start == n - 1) {
				return rows;
			}
		}
		return List.of();
	}

	/** The lyric font on the card: very thin or flowing fonts are hard to read that small, so those use Soft. */
	static TextFont cardFont(TextFont font) {
		return font == TextFont.FANCY || font == TextFont.DECO ? TextFont.SOFT : font;
	}

	static @Nullable String status(UiHost host) {
		return switch (host.lyricsState()) {
			case WAITING, SEARCHING -> host.tr("artlyrics.status.searching");
			case INSTRUMENTAL -> host.tr("artlyrics.status.instrumental");
			case UNSYNCED_ONLY -> host.tr("artlyrics.status.unsynced_only");
			case NOT_FOUND -> host.tr("artlyrics.status.not_found");
			case ERROR -> host.tr("artlyrics.status.error");
			case FOUND, IDLE -> null;
		};
	}

	static String clock(long ms) {
		long seconds = Math.max(0, ms / 1000);
		return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
	}
}
