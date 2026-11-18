package com.artlyrics.ui;

import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.ArtlyricsConfig.TextFont;
import com.artlyrics.config.Palette;
import com.artlyrics.cover.CoverImage;
import com.artlyrics.holiday.Decorations;
import com.artlyrics.holiday.HolidayFinder;
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
	/** Width of the player style. */
	public static final int PLAYER_WIDTH = 204;
	private static final int PLAYER_ART = 52;
	/** How far the record comes out from behind the cover while playing. */
	private static final int VINYL_OUT = 20;
	private static final int BARS = 20;
	private static final int MARGIN = 6;
	private static final int PAD = 6;
	private static final int ART = 22;
	private static final int ART_BIG = 34;
	/** Seconds a new cover takes to fade in over the old one. */
	private static final float COVER_FADE_S = 0.35f;
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
	private @Nullable CoverImage shownCover;
	private @Nullable CoverImage previousCover;
	private float coverFade = 1f;
	private long coverStartNanos;
	/** Player style: how far the record is out (0..1), its turn in revolutions, and the bars' heights. */
	private float vinylOut;
	private double vinylTurn;
	private final float[] bars = new float[BARS];
	/** Milliseconds since the word being sung started, for the bars to jump on. */
	private long beatAgeMs = Long.MAX_VALUE;

	// Content measured for the frame being drawn.
	private List<List<Piece>> rows = List.of();
	/** The rows of the line before, fading out while the new one fades in. */
	private List<List<Piece>> previousRows = List.of();
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
		int w = Math.round(width(config) * s);
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
		drawCard(c, host, lastTrack, 0, 0, width(config), height, now);
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
		computeContent(c, host, width(config) - PAD * 2, now);
		int target = !rows.isEmpty() ? rows.size() : status != null ? 1 : 0;
		shownRows = shownRows < 0 ? target : shownRows + (target - shownRows) * Easing.approach(dt, 10f);
		int h = PAD + art(config) + PAD + (config.cardProgress ? ROW : 0);
		return h + Math.round(shownRows * ROW) + (shownRows > 0.05f ? PAD - 2 : 0);
	}

	/** Draws the card with its top-left at (x, y), unscaled, using the content from {@link #measure}. */
	public void drawCard(Canvas c, UiHost host, NowPlaying track, int x, int y, int width, int height, long now) {
		ArtlyricsConfig config = host.config();
		Palette palette = config.palette();
		int accent = Theme.accent(palette.from());

		Theme.apply(config.menuTheme);
		Draw.roundRect(c, x, y, x + width, y + height, 5, Draw.withAlpha(Theme.CARD_BG, config.cardOpacity));
		Draw.roundOutline(c, x, y, x + width, y + height, 5, Draw.withAlpha(Theme.HIGHLIGHT, 0.07f));
		HolidayFinder.Active holiday = config.holidayDecorations ? host.holiday() : null;
		if (holiday != null) {
			drawDecorations(c, holiday, x, y, width, height, now);
		}

		// Art tile: the song's cover, or a palette gradient with a note while there is none.
		int art = art(config);
		int ax = x + PAD;
		int ay = y + PAD;
		double seconds = now / 1e9;
		boolean player = config.cardStyle == ArtlyricsConfig.CardStyle.PLAYER;
		if (player && config.cardVinyl) {
			drawVinyl(c, track, palette, ax, ay, art, seconds);
		}
		c.gradientV(ax, ay, ax + art, ay + art, 0xFF000000 | palette.colorAt(0f, seconds), 0xFF000000 | palette.colorAt(1f, seconds));
		c.fill(ax, ay, ax + art, ay + art, 0x1A000000);
		if (holiday != null) {
			// The holiday's own symbol instead of the note.
			String glyph = holiday.theme().glyphs()[0];
			c.push();
			c.translate(ax + art / 2f, ay + art / 2f);
			c.scale(art / 14f);
			c.text(glyph, null, -c.textWidth(glyph, null) / 2, -4, 0xF0FFFFFF, false);
			c.pop();
		} else {
			c.icon("note", ax + (art - 10) / 2, ay + (art - 10) / 2, 0xF0FFFFFF);
		}
		drawCover(c, host, config, ax, ay, art, now);

		int tx = ax + art + (player ? (config.cardVinyl ? VINYL_OUT + 8 : 8) : 6);
		int textWidth = x + width - PAD - tx;
		int textTop = player ? ay + 3 : ay + (art - ART) / 2;
		c.text(Draw.ellipsize(c, track.title(), FONT, textWidth), FONT, tx, textTop + 2, Theme.TEXT, false);
		String sub = config.cardArtist ? track.artist() : "";
		if (!track.playing()) {
			sub = sub.isBlank() ? host.tr("artlyrics.card.paused") : sub + "  ·  " + host.tr("artlyrics.card.paused");
		}
		if (!sub.isBlank()) {
			c.text(Draw.ellipsize(c, sub, FONT, textWidth), FONT, tx, textTop + 13, Theme.MUTED, false);
		}
		if (player && config.cardVisualizer) {
			drawBars(c, track, palette, tx, ay + 28, textWidth, art - 28, seconds);
		}

		int cy = ay + art + PAD;
		if (config.cardProgress) {
			drawProgress(c, track, host.positionMs(), x + PAD, cy, width - PAD * 2, accent, palette);
			cy += ROW;
		}
		drawLyrics(c, x + PAD, cy, width - PAD * 2, cardFont(config.font), now);
	}

	/** The holiday's decorations drifting softly over the card, behind the text. */
	private void drawDecorations(Canvas c, HolidayFinder.Active holiday, int x, int y, int width, int height, long now) {
		float alpha = c.alpha();
		for (Decorations.Piece p : Decorations.at(holiday.theme(), holiday.decorationColors(), 9, now / 1e9, width, height)) {
			if (p.x() < 3 || p.x() > width - 3 || p.y() < 2 || p.y() > height - 2) {
				continue;
			}
			c.setAlpha(alpha * p.alpha() * 0.35f);
			c.push();
			c.translate(x + p.x(), y + p.y());
			c.scale(p.scale() * 0.8f);
			c.text(p.glyph(), null, -c.textWidth(p.glyph(), null) / 2, -4, 0xFF000000 | p.color(), false);
			c.pop();
		}
		c.setAlpha(alpha);
	}

	private static int art(ArtlyricsConfig config) {
		if (config.cardStyle == ArtlyricsConfig.CardStyle.PLAYER) {
			return PLAYER_ART;
		}
		return config.cardBigCover ? ART_BIG : ART;
	}

	/** The card's width for its style (unscaled). */
	public static int width(ArtlyricsConfig config) {
		return config.cardStyle == ArtlyricsConfig.CardStyle.PLAYER ? PLAYER_WIDTH : WIDTH;
	}

	/**
	 * A record behind the cover. It slides out while the song plays and back in when paused, and turns at about
	 * the speed of a real one; the shine on its grooves and the dot on the label show it turning.
	 */
	private void drawVinyl(Canvas c, NowPlaying track, Palette palette, int ax, int ay, int art, double seconds) {
		vinylOut += ((track.playing() ? 1f : 0.25f) - vinylOut) * Easing.approach(dt, 5f);
		if (track.playing()) {
			vinylTurn += dt * 0.55; // 33 rpm
		}
		float r = art / 2f - 1f;
		float cx = ax + art / 2f + VINYL_OUT * Easing.inOutSine(vinylOut);
		float cy = ay + art / 2f;
		Draw.circle(c, cx, cy, r, 0xFF101014);
		for (float g = r - 2; g > r * 0.42f; g -= 2.5f) {
			Draw.circle(c, cx, cy, g, 0xFF26262E);
			Draw.circle(c, cx, cy, g - 1f, 0xFF15151A);
		}
		// Shine: two soft spots on opposite sides of the grooves.
		double angle = vinylTurn * Math.PI * 2;
		for (int k = 0; k < 2; k++) {
			double a = angle + k * Math.PI;
			for (int i = 0; i < 4; i++) {
				float rr = r * (0.58f + i * 0.1f);
				float px = cx + (float) Math.cos(a) * rr;
				float py = cy + (float) Math.sin(a) * rr;
				c.fill(Math.round(px) - 1, Math.round(py) - 1, Math.round(px) + 1, Math.round(py) + 1, 0x40FFFFFF);
			}
		}
		// Label in the palette's colors, with a dot that goes round.
		float label = r * 0.36f;
		Draw.circle(c, cx, cy, label, 0xFF000000 | palette.colorAt(0.3f, seconds));
		Draw.circle(c, cx, cy, label * 0.62f, 0xFF000000 | palette.colorAt(0.8f, seconds));
		float dx = cx + (float) Math.cos(angle) * label * 0.75f;
		float dy = cy + (float) Math.sin(angle) * label * 0.75f;
		c.fill(Math.round(dx) - 1, Math.round(dy) - 1, Math.round(dx) + 1, Math.round(dy) + 1, 0xC0FFFFFF);
		Draw.circle(c, cx, cy, 1.5f, 0xFF0A0A0C);
	}

	/** Bars that move to the music: each sung word gives them a kick, and they settle while paused. */
	private void drawBars(Canvas c, NowPlaying track, Palette palette, int x, int y, int width, int height, double seconds) {
		float kick = beatAgeMs == Long.MAX_VALUE ? 0f : (float) Math.exp(-beatAgeMs / 260.0);
		float energy = track.playing() ? 0.3f + 0.7f * kick : 0.05f;
		int gap = 2;
		int bw = Math.max(2, (width - gap * (BARS - 1)) / BARS);
		int bx = x;
		int bottom = y + height;
		for (int i = 0; i < BARS; i++) {
			double wave = 0.5 + 0.5 * Math.sin(seconds * (3.1 + (i * 7 % 5) * 0.9) + i * 1.7)
					* Math.sin(seconds * (1.3 + (i % 3) * 0.6) + i * 0.4);
			float shape = 0.55f + 0.45f * (float) Math.sin(Math.PI * (i + 0.5) / BARS); // taller in the middle
			float target = Math.max(0.08f, energy * shape * (0.35f + 0.65f * (float) wave));
			// Quick to jump up, slower to fall: like a real meter.
			float speed = target > bars[i] ? 18f : 6f;
			bars[i] += (target - bars[i]) * Easing.approach(dt, speed);
			int bh = Math.max(1, Math.round(bars[i] * height));
			int color = 0xFF000000 | palette.colorAt(i / (float) (BARS - 1), seconds);
			Draw.roundRect(c, bx, bottom - bh, bx + bw, bottom, 1, Draw.withAlpha(color & 0xFFFFFF, 0.9f));
			bx += bw + gap;
		}
	}

	/** The cover over the art tile, fading smoothly from the previous one (or from the plain tile). */
	private void drawCover(Canvas c, UiHost host, ArtlyricsConfig config, int x, int y, int size, long now) {
		CoverImage cover = config.cardCover ? host.cover() : null;
		if (cover != shownCover) {
			previousCover = shownCover;
			shownCover = cover;
			coverFade = 0f;
			coverStartNanos = now;
		}
		coverFade = Math.min(1f, coverFade + dt / COVER_FADE_S);
		float eased = Easing.inOutSine(coverFade);
		long elapsedMs = config.coverAnimate ? (now - coverStartNanos) / 1_000_000L : 0L;
		float alpha = c.alpha();
		if (previousCover != null && eased < 1f) {
			c.setAlpha(alpha * (1f - eased));
			c.cover(previousCover, config.coverAnimate ? elapsedMs : 0L, x, y, size);
		}
		if (shownCover != null) {
			c.setAlpha(alpha * eased);
			if (!c.cover(shownCover, elapsedMs, x, y, size)) {
				coverFade = 0f; // still being prepared: start the fade once it can be drawn
			}
		}
		c.setAlpha(alpha);
		if (eased >= 1f) {
			previousCover = null;
		}
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
		Draw.roundRect(c, bx0, by, bx1, by + 3, 1, Draw.withAlpha(Theme.HIGHLIGHT, 0.19f));
		if (track.durationMs() > 0 && bx1 > bx0) {
			float p = Math.min(1f, positionMs / (float) track.durationMs());
			int filled = bx0 + Math.round((bx1 - bx0) * p);
			Draw.gradientH(c, bx0, by, Math.max(bx0 + 1, filled), by + 3, 0xFF000000 | palette.from(), 0xFF000000 | palette.to());
			Draw.circle(c, filled, by + 1.5f, 2.5f, accent);
		}
	}

	private void drawLyrics(Canvas c, int x, int y, int width, TextFont font, long now) {
		float fade = Easing.inOutSine((now - lineChangedNanos) / 320_000_000f);
		float alpha = c.alpha();
		if (rows.isEmpty()) {
			if (status != null && shownRows > 0.05f) {
				c.setAlpha(alpha * fade * Math.min(1f, shownRows));
				c.text(Draw.ellipsize(c, status, FONT, width), FONT, x, y + 2, Theme.MUTED, false);
				c.setAlpha(alpha);
			}
			return;
		}
		if (fade < 1f && !previousRows.isEmpty()) {
			// The old line drifts up and fades as the new one comes in.
			int py = y + 2 - Math.round(4f * fade);
			for (int r = 0; r < previousRows.size() && r < MAX_LYRIC_ROWS; r++) {
				c.setAlpha(alpha * (1f - fade) * Easing.clamp01(shownRows - r));
				int px = x;
				for (Piece piece : previousRows.get(r)) {
					c.text(piece.text, font, px, py, piece.color, false);
					px += piece.width;
				}
				py += ROW;
			}
			c.setAlpha(alpha);
		}
		int ry = y + 2 + Math.round(4f * (1f - fade));
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
		long timeMs = host.lyricsTimeMs();
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
		beatAgeMs = Long.MAX_VALUE;
		if (live) {
			int active = line.wordIndexAt(timeMs);
			if (active >= 0) {
				beatAgeMs = Math.max(0, timeMs - line.words().get(active).startMs());
			}
		}
		if (key != lineKey) {
			lineKey = key;
			lineChangedNanos = now;
			previousRows = rows;
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
				color = Theme.readable(0xFF000000 | palette.accent());
			} else if (timeMs >= w.startMs()) {
				color = Theme.readable(0xFF000000 | palette.colorAt(n > 1 ? i / (float) (n - 1) : 0.5f, seconds));
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
