package com.artlyrics.ui;

import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.ArtlyricsConfig.TextFont;
import com.artlyrics.config.Look;
import com.artlyrics.config.LyricStyle;
import com.artlyrics.config.Palette;
import com.artlyrics.lyrics.LrcParser;
import com.artlyrics.lyrics.Lyrics;
import com.artlyrics.lyrics.LyricsManager;
import com.artlyrics.nowplaying.NowPlaying;
import com.artlyrics.render.Easing;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The Artlyrics settings screen, drawn "immediate mode": every frame lays out and draws the controls of the
 * current page and records where they are, and mouse input is matched against those spots. Everything animates
 * smoothly (opening, page changes, hovering, toggles, scrolling).
 */
public final class SettingsUi {
	public enum Page {
		NOW_PLAYING("note"), LYRICS("lyrics"), LOOK("palette"), PLACEMENT("compass"), CARD("card"), MUSIC("headphones");

		final String icon;

		Page(String icon) {
			this.icon = icon;
		}

		String key() {
			return "artlyrics.page." + name().toLowerCase(Locale.ROOT);
		}
	}

	private static final TextFont UI = TextFont.SOFT;
	private static final int ROW_H = 26;
	private static final int NAV_H = 18;
	private static final int CONTROL_W = 112;

	/** Preferred player choices: label key suffix and the name fragment that's matched. */
	private static final String[][] PLAYERS = {
			{"any", ""}, {"spotify", "spotify"}, {"apple_music", "music"}, {"chrome", "chrom"},
			{"firefox", "firefox"}, {"edge", "edge"}, {"vlc", "vlc"}};

	private final UiHost host;
	private final SongCardView cardPreview = new SongCardView();

	private Page page = Page.NOW_PLAYING;
	private boolean started;
	private long openedNanos;
	private long pageNanos;
	private long lastNanos;
	private float dt;
	private long now;
	private float scroll;
	private float scrollTarget;
	private float maxScroll;
	private float navY = -1;
	private final Map<String, Float> anim = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();
	private @Nullable Hit dragging;
	private int mouseX;
	private int mouseY;
	/** Which custom color is being edited: 0 = start, 1 = end, 2 = accent. */
	private int colorSlot;
	private float[] editHsv;

	// Layout of the current frame.
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int side;
	private int cx;
	private int cw;
	private int contentTop;
	private int contentBottom;
	private int accent;

	private interface DragTarget {
		void set(float t);
	}

	private record Hit(int x0, int y0, int x1, int y1, @Nullable Runnable click, @Nullable DragTarget drag, int dragX0, int dragX1) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	public SettingsUi(UiHost host) {
		this.host = host;
	}

	public Page page() {
		return page;
	}

	public void setPage(Page page) {
		if (this.page != page) {
			this.page = page;
			pageNanos = now;
			scroll = 0;
			scrollTarget = 0;
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// Frame

	public void render(Canvas c, int mouseX, int mouseY, long nowNanos) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		if (!started) {
			started = true;
			openedNanos = nowNanos;
			pageNanos = nowNanos;
			lastNanos = nowNanos;
		}
		dt = Math.min(0.1f, (nowNanos - lastNanos) / 1e9f);
		lastNanos = nowNanos;
		now = nowNanos;
		hits.clear();

		ArtlyricsConfig config = host.config();
		accent = Theme.accent(config.palette().from());
		float open = Easing.inOutSine((now - openedNanos) / 220_000_000f);

		c.fill(0, 0, c.width(), c.height(), Draw.withAlpha(Theme.SCRIM, ((Theme.SCRIM >>> 24) / 255f) * open));

		pw = Math.min(470, c.width() - 16);
		ph = Math.min(280, c.height() - 16);
		px = (c.width() - pw) / 2;
		py = (c.height() - ph) / 2 + Math.round((1f - open) * 10f);
		side = pw < 380 ? 30 : 112;
		cx = px + side + 12;
		cw = pw - side - 24;
		contentTop = py + 44;
		contentBottom = py + ph - 8;

		float baseAlpha = c.alpha();
		c.setAlpha(baseAlpha * open);

		// Panel and sidebar.
		Draw.roundRect(c, px, py, px + pw, py + ph, 8, Theme.PANEL);
		Draw.roundRectLeft(c, px, py, px + side, py + ph, 8, Theme.SIDEBAR);
		c.fill(px + side, py, px + side + 1, py + ph, Theme.LINE);
		Draw.roundOutline(c, px, py, px + pw, py + ph, 8, 0x18FFFFFF);
		drawSidebar(c);
		drawHeader(c);

		// Page content, clipped and scrolled.
		scroll += (scrollTarget - scroll) * Easing.approach(dt, 18f);
		float pageIn = Easing.inOutSine((now - pageNanos) / 180_000_000f);
		c.clip(cx - 4, contentTop, cx + cw + 4, contentBottom);
		c.setAlpha(baseAlpha * open * pageIn);
		int start = contentTop + 2 - Math.round(scroll) + Math.round((1f - pageIn) * 6f);
		int end = switch (page) {
			case NOW_PLAYING -> pageNowPlaying(c, start);
			case LYRICS -> pageLyrics(c, start);
			case LOOK -> pageLook(c, start);
			case PLACEMENT -> pagePlacement(c, start);
			case CARD -> pageCard(c, start);
			case MUSIC -> pageMusic(c, start);
		};
		c.unclip();
		c.setAlpha(baseAlpha * open);
		int contentHeight = end - start + 6;
		maxScroll = Math.max(0, contentHeight - (contentBottom - contentTop));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		drawScrollbar(c, contentHeight);
		c.setAlpha(baseAlpha);
	}

	private void drawSidebar(Canvas c) {
		boolean compact = side < 60;
		int y = py + 10;
		if (!compact) {
			c.gradientV(px + 10, y, px + 26, y + 16, 0xFF000000 | host.config().palette().colorAt(0f, now / 1e9),
					0xFF000000 | host.config().palette().colorAt(1f, now / 1e9));
			c.icon("note", px + 13, y + 3, 0xFFFFFFFF);
			c.text("Artlyrics", UI, px + 31, y, Theme.TEXT, false);
			c.text(host.tr("artlyrics.ui.tagline"), UI, px + 31, y + 9, Theme.MUTED, false);
		} else {
			c.icon("note", px + 10, y + 3, accent);
		}

		int navTop = py + 40;
		int selectedIndex = page.ordinal();
		float targetY = navTop + selectedIndex * (NAV_H + 2);
		navY = navY < 0 ? targetY : navY + (targetY - navY) * Easing.approach(dt, 16f);
		int sx0 = px + 6;
		int sx1 = px + side - 6;
		Draw.roundRect(c, sx0, Math.round(navY), sx1, Math.round(navY) + NAV_H, 4, Draw.withAlpha(accent, 0.18f));
		c.fill(sx0, Math.round(navY) + 4, sx0 + 2, Math.round(navY) + NAV_H - 4, accent);

		for (Page p : Page.values()) {
			int iy = navTop + p.ordinal() * (NAV_H + 2);
			boolean hovered = inside(sx0, iy, sx1, iy + NAV_H);
			float h = hover("nav:" + p, hovered && p != page);
			if (h > 0.01f) {
				Draw.roundRect(c, sx0, iy, sx1, iy + NAV_H, 4, Draw.withAlpha(0xFFFFFF, 0.05f * h));
			}
			int color = p == page ? Theme.TEXT : Draw.lerpArgb(Theme.MUTED, Theme.TEXT, h);
			c.icon(p.icon, sx0 + (compact ? (sx1 - sx0 - 10) / 2 : 6), iy + 4, p == page ? accent : color);
			if (!compact) {
				c.text(host.tr(p.key()), UI, sx0 + 21, iy + 5, color, false);
			}
			hits.add(new Hit(sx0, iy, sx1, iy + NAV_H, () -> setPage(p), null, 0, 0));
		}

		if (!compact) {
			String version = host.version();
			int plus = version.indexOf('+');
			c.text("v" + (plus > 0 ? version.substring(0, plus) : version), null, px + 12, py + ph - 14, Theme.FAINT, false);
		}
	}

	private void drawHeader(Canvas c) {
		int y = py + 10;
		c.push();
		c.translate(cx, y);
		c.scale(1.4f);
		c.text(host.tr(page.key()), UI, 0, 0, Theme.TEXT, false);
		c.pop();
		c.text(Draw.ellipsize(c, host.tr(page.key() + ".desc"), UI, cw - 90), UI, cx, y + 16, Theme.MUTED, false);

		// Master switch.
		ArtlyricsConfig config = host.config();
		int x1 = px + pw - 26;
		int tw = 22;
		int ty = y + 2;
		String label = host.tr(config.enabled ? "artlyrics.ui.on" : "artlyrics.ui.off");
		c.text(label, UI, x1 - tw - 6 - c.textWidth(label, UI), ty + 2, Theme.MUTED, false);
		drawSwitch(c, "master", x1 - tw, ty, config.enabled);
		hits.add(new Hit(x1 - tw - 30, ty - 2, x1, ty + 14, () -> {
			config.enabled = !config.enabled;
			host.settingsChanged();
		}, null, 0, 0));

		// Close button.
		int bx = px + pw - 18;
		int by = py + 8;
		float h = hover("close", inside(bx - 2, by - 2, bx + 12, by + 12));
		if (h > 0.01f) {
			Draw.roundRect(c, bx - 2, by - 2, bx + 12, by + 12, 3, Draw.withAlpha(0xFFFFFF, 0.08f * h));
		}
		c.icon("close", bx, by, Draw.lerpArgb(Theme.MUTED, Theme.TEXT, h));
		hits.add(new Hit(bx - 2, by - 2, bx + 12, by + 12, host::close, null, 0, 0));

		c.fill(cx, contentTop - 4, cx + cw, contentTop - 3, Theme.LINE);
	}

	private void drawScrollbar(Canvas c, int contentHeight) {
		if (maxScroll <= 0) {
			return;
		}
		int trackH = contentBottom - contentTop;
		int barH = Math.max(16, Math.round(trackH * trackH / (float) contentHeight));
		int barY = contentTop + Math.round((trackH - barH) * (scroll / maxScroll));
		int x = cx + cw + 6;
		Draw.roundRect(c, x, barY, x + 3, barY + barH, 1, 0x40FFFFFF);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Pages

	private int pageNowPlaying(Canvas c, int y) {
		NowPlaying track = host.track();
		ArtlyricsConfig config = host.config();
		Palette palette = config.palette();
		double seconds = now / 1e9;

		int cardH = 92;
		Draw.roundRect(c, cx, y, cx + cw, y + cardH, 6, Theme.CARD);
		int art = 44;
		int ax = cx + 10;
		int ay = y + 10;
		c.gradientV(ax, ay, ax + art, ay + art, 0xFF000000 | palette.colorAt(0f, seconds), 0xFF000000 | palette.colorAt(1f, seconds));
		c.push();
		c.translate(ax + art / 2f - 10, ay + art / 2f - 10);
		c.scale(2f);
		c.icon("note", 0, 0, 0xE0FFFFFF);
		c.pop();

		int tx = ax + art + 10;
		int tw = cx + cw - 10 - tx;
		if (track == null) {
			c.text(host.tr("artlyrics.ui.nothing_playing"), UI, tx, ay + 4, Theme.TEXT, false);
			c.text(Draw.ellipsize(c, host.tr("artlyrics.status.nothing_playing"), UI, tw), UI, tx, ay + 16, Theme.MUTED, false);
			String problem = host.problem();
			if (problem != null) {
				c.text(Draw.ellipsize(c, problem, UI, tw), UI, tx, ay + 28, Theme.BAD, false);
			}
		} else {
			c.push();
			c.translate(tx, ay + 2);
			c.scale(1.2f);
			c.text(Draw.ellipsize(c, track.title(), UI, Math.round(tw / 1.2f)), UI, 0, 0, Theme.TEXT, false);
			c.pop();
			c.text(Draw.ellipsize(c, track.artist(), UI, tw), UI, tx, ay + 15, Theme.MUTED, false);
			drawStatusBadge(c, tx, ay + 27);

			// Progress.
			int barY = ay + art + 8;
			long pos = host.positionMs();
			String elapsed = SongCardView.clock(pos);
			String total = track.durationMs() > 0 ? SongCardView.clock(track.durationMs()) : "-:--";
			c.text(elapsed, null, ax, barY, Theme.MUTED, false);
			c.text(total, null, cx + cw - 10 - c.textWidth(total, null), barY, Theme.MUTED, false);
			int b0 = ax + c.textWidth(elapsed, null) + 6;
			int b1 = cx + cw - 16 - c.textWidth(total, null);
			Draw.roundRect(c, b0, barY + 3, b1, barY + 6, 1, 0x30FFFFFF);
			if (track.durationMs() > 0) {
				int filled = b0 + Math.round((b1 - b0) * Math.min(1f, pos / (float) track.durationMs()));
				Draw.gradientH(c, b0, barY + 3, Math.max(b0 + 1, filled), barY + 6, 0xFF000000 | palette.from(), 0xFF000000 | palette.to());
			}

			// The line being sung, in the chosen font and colors.
			String current = currentLineText();
			if (current != null) {
				c.text(Draw.ellipsize(c, current, config.font, cw - 20), config.font, ax, barY + 13, 0xFF000000 | palette.colorAt(0.3f, seconds), false);
			}
		}
		y += cardH + 8;

		y = section(c, "artlyrics.ui.section.quick", y);
		y = rowToggle(c, "demo", "artlyrics.option.demo", y, host.demoActive(), host::setDemoActive);
		y = rowButton(c, "refresh", "artlyrics.option.refresh", "artlyrics.ui.search", y, host::refreshLyrics);
		y = rowSelector(c, "mode", "artlyrics.option.mode", y, config.displayMode.ordinal(),
				labels(ArtlyricsConfig.DisplayMode.values(), m -> m.translationKey()), i -> config.displayMode = ArtlyricsConfig.DisplayMode.values()[i]);
		c.text(Draw.ellipsize(c, host.tr("artlyrics.ui.keybind_hint"), UI, cw), UI, cx + 8, y + 4, Theme.FAINT, false);
		return y + 16;
	}

	private void drawStatusBadge(Canvas c, int x, int y) {
		LyricsManager.State state = host.lyricsState();
		Lyrics lyrics = host.lyrics();
		int color = switch (state) {
			case FOUND -> Theme.GOOD;
			case WAITING, SEARCHING -> Theme.WARN;
			case INSTRUMENTAL, IDLE -> Theme.MUTED;
			case UNSYNCED_ONLY, NOT_FOUND, ERROR -> Theme.BAD;
		};
		String text = state == LyricsManager.State.FOUND && lyrics != null
				? host.tr(lyrics.wordSynced() ? "artlyrics.ui.word_synced" : "artlyrics.ui.line_synced")
				: host.tr("artlyrics.status." + state.name().toLowerCase(Locale.ROOT));
		int w = c.textWidth(text, UI) + 16;
		Draw.roundRect(c, x, y, x + w, y + 12, 6, Draw.withAlpha(color, 0.14f));
		Draw.circle(c, x + 6, y + 6, 2, color);
		c.text(text, UI, x + 11, y + 2, color, false);
	}

	private @Nullable String currentLineText() {
		Lyrics lyrics = host.lyrics();
		if (lyrics == null) {
			return null;
		}
		ArtlyricsConfig config = host.config();
		long t = host.positionMs() + config.offsetMs;
		int index = lyrics.lineIndexAt(t);
		if (index < 0 || lyrics.lines().get(index).isBlank()) {
			return null;
		}
		String text = lyrics.lines().get(index).text();
		return config.uppercase ? text.toUpperCase(Locale.ROOT) : text;
	}

	private int pageLyrics(Canvas c, int y) {
		ArtlyricsConfig config = host.config();
		y = rowSelector(c, "mode", "artlyrics.option.mode", y, config.displayMode.ordinal(),
				labels(ArtlyricsConfig.DisplayMode.values(), m -> m.translationKey()), i -> config.displayMode = ArtlyricsConfig.DisplayMode.values()[i]);
		y = rowSelector(c, "animation", "artlyrics.option.animation", y, config.animation.ordinal(),
				labels(ArtlyricsConfig.Animation.values(), a -> a.translationKey()), i -> config.animation = ArtlyricsConfig.Animation.values()[i]);
		y = rowSlider(c, "speed", "artlyrics.option.speed", y, config.animationSpeed, 0.5f, 2f,
				String.format(Locale.ROOT, "%.1f×", config.animationSpeed), v -> config.animationSpeed = round(v, 0.1f));
		y = rowSelector(c, "exit", "artlyrics.option.exit", y, config.exitStyle.ordinal(),
				labels(ArtlyricsConfig.ExitStyle.values(), e -> e.translationKey()), i -> config.exitStyle = ArtlyricsConfig.ExitStyle.values()[i]);
		y = rowSlider(c, "linger", "artlyrics.option.linger", y, config.lingerSeconds, 0.5f, 6f,
				String.format(Locale.ROOT, "%.1f s", config.lingerSeconds), v -> config.lingerSeconds = round(v, 0.5f));
		y = rowSlider(c, "offset", "artlyrics.option.offset", y, config.offsetMs, -3000, 3000,
				(config.offsetMs > 0 ? "+" : "") + config.offsetMs + " ms", v -> config.offsetMs = Math.round(v / 50f) * 50);
		return y;
	}

	private int pageLook(Canvas c, int y) {
		ArtlyricsConfig config = host.config();
		double seconds = now / 1e9;

		// Looks: one click sets font, palette, effect and animation.
		y = section(c, "artlyrics.ui.section.looks", y);
		int cols = cw >= 300 ? 3 : 2;
		int gap = 6;
		int tileW = (cw - gap * (cols - 1)) / cols;
		int tileH = 32;
		Look[] looks = Look.values();
		for (int i = 0; i < looks.length; i++) {
			Look look = looks[i];
			int tx = cx + (i % cols) * (tileW + gap);
			int ty = y + (i / cols) * (tileH + gap);
			boolean selected = look.matches(config);
			Palette p = look.style.palette();
			int tint = Theme.accent(p.from());
			float h = hover("look:" + look, insideContent(tx, ty, tx + tileW, ty + tileH));
			Draw.roundRect(c, tx, ty, tx + tileW, ty + tileH, 5, Draw.lerpArgb(Theme.CARD, Theme.CARD_HOVER, h));
			Draw.gradientH(c, tx + 6, ty + tileH - 4, tx + tileW - 6, ty + tileH - 2, 0xFF000000 | p.from(), 0xFF000000 | p.to());
			if (selected) {
				Draw.roundOutline(c, tx, ty, tx + tileW, ty + tileH, 5, tint);
			}
			String name = host.tr(look.translationKey());
			int nw = c.textWidth(name, look.font);
			c.push();
			c.translate(tx + (tileW - nw * 1.25f) / 2f, ty + 6);
			c.scale(1.25f);
			c.text(name, look.font, 0, 0, 0xFF000000 | p.colorAt(0.2f, seconds), false);
			c.pop();
			String sub = host.tr(look.animation.translationKey());
			c.text(sub, UI, tx + (tileW - c.textWidth(sub, UI)) / 2, ty + 19, selected ? tint : Theme.MUTED, false);
			contentHit(tx, ty, tx + tileW, ty + tileH, () -> {
				config.apply(look);
				host.settingsChanged();
			});
		}
		y += ((looks.length + cols - 1) / cols) * (tileH + gap) + 4;

		// Fonts.
		y = section(c, "artlyrics.ui.section.font", y);
		int fcols = cw >= 300 ? 5 : 4;
		int fw = (cw - gap * (fcols - 1)) / fcols;
		int fh = 30;
		TextFont[] fonts = TextFont.values();
		for (int i = 0; i < fonts.length; i++) {
			TextFont font = fonts[i];
			int tx = cx + (i % fcols) * (fw + gap);
			int ty = y + (i / fcols) * (fh + gap);
			boolean selected = config.font == font;
			float h = hover("font:" + font, insideContent(tx, ty, tx + fw, ty + fh));
			Draw.roundRect(c, tx, ty, tx + fw, ty + fh, 5, Draw.lerpArgb(Theme.CARD, Theme.CARD_HOVER, h));
			if (selected) {
				Draw.roundOutline(c, tx, ty, tx + fw, ty + fh, 5, accent);
			}
			String sample = "Aa";
			int sw = c.textWidth(sample, font);
			c.push();
			c.translate(tx + (fw - sw * 1.4f) / 2f, ty + 4);
			c.scale(1.4f);
			c.text(sample, font, 0, 0, selected ? accent : Theme.TEXT, false);
			c.pop();
			String name = Draw.ellipsize(c, host.tr(font.translationKey()), UI, fw - 4);
			c.text(name, UI, tx + (fw - c.textWidth(name, UI)) / 2, ty + 19, Theme.MUTED, false);
			contentHit(tx, ty, tx + fw, ty + fh, () -> {
				config.font = font;
				host.settingsChanged();
			});
		}
		y += ((fonts.length + fcols - 1) / fcols) * (fh + gap) + 4;

		// Palettes.
		y = section(c, "artlyrics.ui.section.palette", y);
		LyricStyle[] styles = LyricStyle.values();
		int pcols = cw >= 300 ? 5 : 3;
		int pwid = (cw - gap * (pcols - 1)) / pcols;
		int phgt = 24;
		for (int i = 0; i < styles.length; i++) {
			LyricStyle style = styles[i];
			int tx = cx + (i % pcols) * (pwid + gap);
			int ty = y + (i / pcols) * (phgt + gap);
			Palette p = style == LyricStyle.CUSTOM
					? new Palette(config.customFrom, config.customTo, config.customAccent, false)
					: style.palette();
			boolean selected = config.style == style;
			float h = hover("pal:" + style, insideContent(tx, ty, tx + pwid, ty + phgt));
			Draw.roundRect(c, tx, ty, tx + pwid, ty + phgt, 5, Draw.lerpArgb(Theme.CARD, Theme.CARD_HOVER, h));
			int sx0 = tx + 4;
			int sx1 = tx + pwid - 4;
			if (style == LyricStyle.AURORA) {
				for (int s = 0; s < 4; s++) {
					int segX0 = sx0 + (sx1 - sx0) * s / 4;
					int segX1 = sx0 + (sx1 - sx0) * (s + 1) / 4;
					Draw.gradientH(c, segX0, ty + 4, segX1, ty + 9, 0xFF000000 | p.colorAt(s / 4f, seconds), 0xFF000000 | p.colorAt((s + 1) / 4f, seconds));
				}
			} else {
				Draw.gradientH(c, sx0, ty + 4, sx1, ty + 9, 0xFF000000 | p.from(), 0xFF000000 | p.to());
			}
			if (selected) {
				Draw.roundOutline(c, tx, ty, tx + pwid, ty + phgt, 5, 0xFF000000 | Theme.accent(p.from()));
			}
			String name = Draw.ellipsize(c, host.tr(style.translationKey()), UI, pwid - 6);
			c.text(name, UI, tx + (pwid - c.textWidth(name, UI)) / 2, ty + 12, selected ? Theme.TEXT : Theme.MUTED, false);
			contentHit(tx, ty, tx + pwid, ty + phgt, () -> {
				config.style = style;
				host.settingsChanged();
			});
		}
		y += ((styles.length + pcols - 1) / pcols) * (phgt + gap) + 2;

		if (config.style == LyricStyle.CUSTOM) {
			y = customColors(c, y);
		}

		// Text.
		y = section(c, "artlyrics.ui.section.text", y);
		y = rowSelector(c, "effect", "artlyrics.option.effect", y, config.textEffect.ordinal(),
				labels(ArtlyricsConfig.TextEffect.values(), e -> e.translationKey()), i -> config.textEffect = ArtlyricsConfig.TextEffect.values()[i]);
		if (config.textEffect == ArtlyricsConfig.TextEffect.GLOW) {
			y = rowSlider(c, "glow", "artlyrics.option.glow", y, config.glowStrength, 0f, 1f,
					Math.round(config.glowStrength * 100) + "%", v -> config.glowStrength = round(v, 0.05f));
		}
		y = rowSlider(c, "size", "artlyrics.option.size", y, config.size, 0.5f, 2.5f,
				Math.round(config.size * 100) + "%", v -> config.size = round(v, 0.05f));
		y = rowSlider(c, "opacity", "artlyrics.option.opacity", y, config.opacity, 0.2f, 1f,
				Math.round(config.opacity * 100) + "%", v -> config.opacity = round(v, 0.05f));
		y = rowSlider(c, "width", "artlyrics.option.line_width", y, config.lineWidth, 100, 320,
				config.lineWidth + " px", v -> config.lineWidth = Math.round(v / 10f) * 10);
		y = rowToggle(c, "bold", "artlyrics.option.bold", y, config.bold, v -> config.bold = v);
		y = rowToggle(c, "italic", "artlyrics.option.italic", y, config.italic, v -> config.italic = v);
		y = rowToggle(c, "caps", "artlyrics.option.uppercase", y, config.uppercase, v -> config.uppercase = v);
		return y;
	}

	/** Editor for the custom palette: pick which color, then hue, saturation and brightness. */
	private int customColors(Canvas c, int y) {
		ArtlyricsConfig config = host.config();
		int[] values = {config.customFrom, config.customTo, config.customAccent};
		String[] keys = {"artlyrics.ui.color.start", "artlyrics.ui.color.end", "artlyrics.ui.color.accent"};
		int sw = 64;
		for (int i = 0; i < 3; i++) {
			int tx = cx + i * (sw + 6);
			boolean selected = colorSlot == i;
			Draw.roundRect(c, tx, y, tx + sw, y + 18, 4, selected ? Theme.CARD_HOVER : Theme.CARD);
			Draw.roundRect(c, tx + 4, y + 4, tx + 14, y + 14, 2, 0xFF000000 | values[i]);
			c.text(host.tr(keys[i]), UI, tx + 18, y + 5, selected ? Theme.TEXT : Theme.MUTED, false);
			if (selected) {
				Draw.roundOutline(c, tx, y, tx + sw, y + 18, 4, accent);
			}
			int slot = i;
			contentHit(tx, y, tx + sw, y + 18, () -> {
				colorSlot = slot;
				editHsv = null;
			});
		}
		y += 24;

		if (editHsv == null) {
			editHsv = Palette.toHsv(values[colorSlot]);
		}
		float[] hsv = editHsv;
		y = rowColorSlider(c, "hue", "artlyrics.ui.color.hue", y, hsv[0], 0, hsv, v -> hsv[0] = v);
		y = rowColorSlider(c, "sat", "artlyrics.ui.color.saturation", y, hsv[1], 1, hsv, v -> hsv[1] = v);
		y = rowColorSlider(c, "val", "artlyrics.ui.color.brightness", y, hsv[2], 2, hsv, v -> hsv[2] = v);
		return y + 4;
	}

	private int rowColorSlider(Canvas c, String id, String labelKey, int y, float value, int channel, float[] hsv, Consumer<Float> set) {
		ArtlyricsConfig config = host.config();
		int top = y;
		y = rowFrame(c, id, labelKey, y, CONTROL_W);
		int x1 = cx + cw - 8;
		int x0 = x1 - CONTROL_W;
		int ty = rowMid(top) - 2;
		// Track showing the colors this slider moves through.
		for (int s = 0; s < 12; s++) {
			int sx0 = x0 + (x1 - x0) * s / 12;
			int sx1 = x0 + (x1 - x0) * (s + 1) / 12;
			float a = s / 12f;
			float b = (s + 1) / 12f;
			Draw.gradientH(c, sx0, ty, sx1, ty + 5, 0xFF000000 | colorFor(hsv, channel, a), 0xFF000000 | colorFor(hsv, channel, b));
		}
		int kx = x0 + Math.round((x1 - x0) * value);
		Draw.circle(c, kx, ty + 2.5f, 4.5f, 0xFFFFFFFF);
		Draw.circle(c, kx, ty + 2.5f, 3f, 0xFF000000 | colorFor(hsv, channel, value));
		dragHit(x0 - 4, top, x1 + 4, y, x0, x1, t -> {
			set.accept(t);
			int rgb = Palette.hsv(hsv[0], hsv[1], hsv[2]);
			switch (colorSlot) {
				case 0 -> config.customFrom = rgb;
				case 1 -> config.customTo = rgb;
				default -> config.customAccent = rgb;
			}
			host.settingsChanged();
		});
		return y;
	}

	private static int colorFor(float[] hsv, int channel, float v) {
		return switch (channel) {
			case 0 -> Palette.hsv(v, Math.max(0.6f, hsv[1]), Math.max(0.85f, hsv[2]));
			case 1 -> Palette.hsv(hsv[0], v, Math.max(0.5f, hsv[2]));
			default -> Palette.hsv(hsv[0], hsv[1], v);
		};
	}

	private int pagePlacement(Canvas c, int y) {
		ArtlyricsConfig config = host.config();
		y = section(c, "artlyrics.ui.section.skywriting", y);
		y = rowSlider(c, "height", "artlyrics.option.sky_height", y, config.skyHeight, 5, 60,
				Math.round(config.skyHeight) + "°", v -> config.skyHeight = Math.round(v));
		String distance = config.skyDistance == 0 ? host.tr("artlyrics.ui.auto") : config.skyDistance + " " + host.tr("artlyrics.ui.blocks");
		y = rowSlider(c, "distance", "artlyrics.option.sky_distance", y, config.skyDistance, 0, 128, distance,
				v -> config.skyDistance = v < 12 ? 0 : Math.max(16, Math.round(v / 4f) * 4));
		y = rowToggle(c, "scatter", "artlyrics.option.scatter", y, config.scatter, v -> config.scatter = v);
		y = rowSlider(c, "drift", "artlyrics.option.drift", y, config.driftSpeed, 0f, 1f,
				String.format(Locale.ROOT, "%.2f°/s", config.driftSpeed), v -> config.driftSpeed = round(v, 0.05f));
		y = rowToggle(c, "arc", "artlyrics.option.arc", y, config.arc, v -> config.arc = v);
		if (config.arc) {
			y = rowSlider(c, "arcAmount", "artlyrics.option.arc_amount", y, config.arcAmount, 0f, 1f,
					Math.round(config.arcAmount * 100) + "%", v -> config.arcAmount = round(v, 0.05f));
		}
		y = section(c, "artlyrics.ui.section.hover", y + 2);
		y = rowSlider(c, "hoverDistance", "artlyrics.option.hover_distance", y, config.hoverDistance, 2f, 10f,
				String.format(Locale.ROOT, "%.1f %s", config.hoverDistance, host.tr("artlyrics.ui.blocks")), v -> config.hoverDistance = round(v, 0.5f));
		y = rowSlider(c, "hoverRaise", "artlyrics.option.hover_raise", y, config.hoverRaise, -15f, 30f,
				Math.round(config.hoverRaise) + "°", v -> config.hoverRaise = Math.round(v));
		y = rowSlider(c, "follow", "artlyrics.option.follow", y, config.followSpeed, 1f, 20f,
				followLabel(config.followSpeed), v -> config.followSpeed = Math.round(v));
		return y;
	}

	private String followLabel(float speed) {
		String key = speed < 5 ? "artlyrics.ui.follow.floaty" : speed < 12 ? "artlyrics.ui.follow.smooth" : "artlyrics.ui.follow.snappy";
		return host.tr(key);
	}

	private int pageCard(Canvas c, int y) {
		ArtlyricsConfig config = host.config();
		// Live preview of the card.
		UiHost previewHost = host.track() != null ? host : sampleHost();
		NowPlaying track = previewHost.track();
		if (track != null) {
			int h = cardPreview.measure(c, previewHost, now, true);
			int areaH = h + 16;
			Draw.roundRect(c, cx, y, cx + cw, y + areaH, 6, 0xFF0A0910);
			float s = Math.min(1f, (cw - 16) / (float) SongCardView.WIDTH);
			c.push();
			c.translate(cx + (cw - SongCardView.WIDTH * s) / 2f, y + 8);
			c.scale(s);
			cardPreview.drawCard(c, previewHost, track, 0, 0, SongCardView.WIDTH, h, now);
			c.pop();
			y += areaH + 6;
		}
		y = rowToggle(c, "card", "artlyrics.option.song_card", y, config.songCard, v -> config.songCard = v);
		y = rowSelector(c, "corner", "artlyrics.option.card_corner", y, config.cardCorner.ordinal(),
				labels(ArtlyricsConfig.CardCorner.values(), k -> k.translationKey()), i -> config.cardCorner = ArtlyricsConfig.CardCorner.values()[i]);
		y = rowSlider(c, "cardScale", "artlyrics.option.card_scale", y, config.cardScale, 0.6f, 1.6f,
				Math.round(config.cardScale * 100) + "%", v -> config.cardScale = round(v, 0.05f));
		y = rowSlider(c, "cardOpacity", "artlyrics.option.card_opacity", y, config.cardOpacity, 0.2f, 1f,
				Math.round(config.cardOpacity * 100) + "%", v -> config.cardOpacity = round(v, 0.05f));
		y = rowToggle(c, "cardArtist", "artlyrics.option.card_artist", y, config.cardArtist, v -> config.cardArtist = v);
		y = rowToggle(c, "cardProgress", "artlyrics.option.card_progress", y, config.cardProgress, v -> config.cardProgress = v);
		y = rowToggle(c, "cardNext", "artlyrics.option.card_next", y, config.cardNextLine, v -> config.cardNextLine = v);
		return y;
	}

	private int pageMusic(Canvas c, int y) {
		ArtlyricsConfig config = host.config();
		int index = 0;
		List<String> labels = new ArrayList<>();
		boolean known = false;
		for (int i = 0; i < PLAYERS.length; i++) {
			labels.add(host.tr("artlyrics.player." + PLAYERS[i][0]));
			if (PLAYERS[i][1].equalsIgnoreCase(config.preferredPlayer)) {
				index = i;
				known = true;
			}
		}
		if (!known) {
			labels.add(0, config.preferredPlayer);
		}
		boolean custom = !known;
		y = rowSelector(c, "player", "artlyrics.option.player", y, index, labels.toArray(new String[0]), i -> {
			int real = custom ? i - 1 : i;
			if (real >= 0) {
				config.preferredPlayer = PLAYERS[real][1];
			}
		});
		y = rowToggle(c, "demo", "artlyrics.option.demo", y, host.demoActive(), host::setDemoActive);
		y = rowButton(c, "refresh", "artlyrics.option.refresh", "artlyrics.ui.search", y, host::refreshLyrics);

		y = section(c, "artlyrics.ui.section.status", y + 2);
		NowPlaying track = host.track();
		String problem = host.problem();
		String line1 = track != null
				? host.tr("artlyrics.ui.following") + " " + track.player()
				: host.tr("artlyrics.ui.nothing_playing");
		c.text(Draw.ellipsize(c, line1, UI, cw - 16), UI, cx + 8, y + 2, track != null ? Theme.GOOD : Theme.MUTED, false);
		y += 13;
		if (problem != null) {
			c.text(Draw.ellipsize(c, problem, UI, cw - 16), UI, cx + 8, y + 2, Theme.BAD, false);
			y += 13;
		}
		c.text(Draw.ellipsize(c, host.tr("artlyrics.ui.lyrics_source"), UI, cw - 16), UI, cx + 8, y + 2, Theme.FAINT, false);
		y += 13;
		c.text(Draw.ellipsize(c, host.tr("artlyrics.ui.keybind_hint"), UI, cw - 16), UI, cx + 8, y + 2, Theme.FAINT, false);
		return y + 14;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Building blocks

	private int section(Canvas c, String key, int y) {
		String text = host.tr(key).toUpperCase(Locale.ROOT);
		c.text(text, UI, cx + 2, y + 4, Theme.FAINT, false);
		int tw = c.textWidth(text, UI);
		c.fill(cx + tw + 8, y + 8, cx + cw, y + 9, Theme.LINE);
		return y + 16;
	}

	/** Height of the row drawn last by {@link #rowFrame}: taller when it has a description. */
	private int rowH = ROW_H;

	/** Draws a row's background, label and description; returns the y below the row. */
	private int rowFrame(Canvas c, String id, String labelKey, int y, int controlWidth) {
		String descKey = labelKey + ".desc";
		String desc = host.tr(descKey);
		boolean hasDesc = !desc.equals(descKey);
		rowH = hasDesc ? ROW_H : 20;
		boolean hovered = insideContent(cx, y, cx + cw, y + rowH - 2);
		float h = hover("row:" + id, hovered);
		if (h > 0.01f) {
			Draw.roundRect(c, cx, y, cx + cw, y + rowH - 2, 4, Draw.withAlpha(0xFFFFFF, 0.04f * h));
		}
		int textMax = cw - controlWidth - 22;
		c.text(Draw.ellipsize(c, host.tr(labelKey), UI, textMax), UI, cx + 8, hasDesc ? y + 3 : y + 5, Theme.TEXT, false);
		if (hasDesc) {
			c.text(Draw.ellipsize(c, desc, UI, textMax), UI, cx + 8, y + 13, Theme.MUTED, false);
		}
		return y + rowH;
	}

	/** Vertical center of the row drawn last, for placing its control. */
	private int rowMid(int top) {
		return top + (rowH - 2) / 2;
	}

	private int rowToggle(Canvas c, String id, String labelKey, int y, boolean value, Consumer<Boolean> set) {
		int top = y;
		y = rowFrame(c, id, labelKey, y, 30);
		int x1 = cx + cw - 8;
		drawSwitch(c, id, x1 - 22, rowMid(top) - 6, value);
		contentHit(cx, top, cx + cw, top + rowH - 2, () -> {
			set.accept(!value);
			host.settingsChanged();
		});
		return y;
	}

	private void drawSwitch(Canvas c, String id, int x, int y, boolean value) {
		float t = smooth("switch:" + id, value ? 1f : 0f, 16f);
		Draw.roundRect(c, x, y, x + 22, y + 12, 6, Draw.lerpArgb(Theme.OFF, accent, t));
		Draw.circle(c, x + 6 + t * 10, y + 6, 4.5f, 0xFFFFFFFF);
	}

	private int rowSelector(Canvas c, String id, String labelKey, int y, int index, String[] labels, IntConsumer set) {
		int top = y;
		y = rowFrame(c, id, labelKey, y, CONTROL_W);
		int x1 = cx + cw - 8;
		int x0 = x1 - CONTROL_W;
		int sy = rowMid(top) - 7;
		boolean hovered = insideContent(x0, sy, x1, sy + 14);
		float h = hover("sel:" + id, hovered);
		Draw.roundRect(c, x0, sy, x1, sy + 14, 4, Draw.lerpArgb(Theme.CONTROL, Theme.CONTROL_HOVER, h));
		boolean leftHover = hovered && mouseX < (x0 + x1) / 2;
		Draw.chevron(c, x0 + 5, sy + 5, false, leftHover ? Theme.TEXT : Theme.MUTED);
		Draw.chevron(c, x1 - 8, sy + 5, true, hovered && !leftHover ? Theme.TEXT : Theme.MUTED);
		String label = Draw.ellipsize(c, labels[index], UI, CONTROL_W - 24);
		c.text(label, UI, x0 + (CONTROL_W - c.textWidth(label, UI)) / 2, sy + 3, Theme.TEXT, false);
		int n = labels.length;
		contentHit(x0, sy - 2, (x0 + x1) / 2, sy + 16, () -> {
			set.accept((index - 1 + n) % n);
			host.settingsChanged();
		});
		contentHit((x0 + x1) / 2, sy - 2, x1, sy + 16, () -> {
			set.accept((index + 1) % n);
			host.settingsChanged();
		});
		return y;
	}

	private int rowSlider(Canvas c, String id, String labelKey, int y, float value, float min, float max, String text,
						  Consumer<Float> set) {
		int top = y;
		y = rowFrame(c, id, labelKey, y, 84 + c.textWidth(text, UI));
		int x1 = cx + cw - 8;
		int x0 = x1 - 78;
		int ty = rowMid(top) - 1;
		float t = Easing.clamp01((value - min) / (max - min));
		float shown = smooth("slider:" + id, t, 22f);
		boolean hovered = insideContent(x0 - 4, top, x1 + 4, top + rowH - 2) || dragging != null && dragging.dragX0 == x0 && dragging.y0 == Math.max(top, contentTop);
		float h = hover("sl:" + id, hovered);
		Draw.roundRect(c, x0, ty, x1, ty + 3, 1, Theme.OFF);
		int kx = x0 + Math.round((x1 - x0) * shown);
		Draw.roundRect(c, x0, ty, Math.max(x0 + 2, kx), ty + 3, 1, accent);
		Draw.circle(c, kx, ty + 1.5f, 3.5f + h, 0xFFFFFFFF);
		c.text(text, UI, x0 - 6 - c.textWidth(text, UI), rowMid(top) - 4, Theme.MUTED, false);
		dragHit(x0 - 4, top, x1 + 4, top + rowH - 2, x0, x1, tt -> {
			set.accept(min + (max - min) * tt);
			host.settingsChanged();
		});
		return y;
	}

	private int rowButton(Canvas c, String id, String labelKey, String buttonKey, int y, Runnable action) {
		int top = y;
		y = rowFrame(c, id, labelKey, y, CONTROL_W);
		int x1 = cx + cw - 8;
		int x0 = x1 - CONTROL_W;
		int by = rowMid(top) - 7;
		float h = hover("btn:" + id, insideContent(x0, by, x1, by + 14));
		Draw.roundRect(c, x0, by, x1, by + 14, 4, Draw.lerpArgb(Draw.withAlpha(accent, 0.25f), Draw.withAlpha(accent, 0.45f), h));
		String label = host.tr(buttonKey);
		c.text(label, UI, x0 + (CONTROL_W - c.textWidth(label, UI)) / 2, by + 3, Theme.TEXT, false);
		contentHit(x0, by, x1, by + 14, action);
		return y;
	}

	private static <T> String[] labelKeys(T[] values, java.util.function.Function<T, String> key) {
		String[] out = new String[values.length];
		for (int i = 0; i < values.length; i++) {
			out[i] = key.apply(values[i]);
		}
		return out;
	}

	private <T> String[] labels(T[] values, java.util.function.Function<T, String> key) {
		String[] keys = labelKeys(values, key);
		for (int i = 0; i < keys.length; i++) {
			keys[i] = host.tr(keys[i]);
		}
		return keys;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Interaction

	private boolean inside(int x0, int y0, int x1, int y1) {
		return mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
	}

	private boolean insideContent(int x0, int y0, int x1, int y1) {
		return inside(x0, y0, x1, y1) && mouseY >= contentTop && mouseY < contentBottom;
	}

	private void contentHit(int x0, int y0, int x1, int y1, Runnable click) {
		int cy0 = Math.max(y0, contentTop);
		int cy1 = Math.min(y1, contentBottom);
		if (cy1 > cy0) {
			hits.add(new Hit(x0, cy0, x1, cy1, click, null, 0, 0));
		}
	}

	private void dragHit(int x0, int y0, int x1, int y1, int dragX0, int dragX1, DragTarget target) {
		int cy0 = Math.max(y0, contentTop);
		int cy1 = Math.min(y1, contentBottom);
		if (cy1 > cy0) {
			hits.add(new Hit(x0, cy0, x1, cy1, null, target, dragX0, dragX1));
		}
	}

	/** Animated 0..1 value that follows {@code hovered}. */
	private float hover(String id, boolean hovered) {
		return smooth(id, hovered ? 1f : 0f, 14f);
	}

	private float smooth(String id, float target, float rate) {
		Float current = anim.get(id);
		float v = current == null ? target : current + (target - current) * Easing.approach(dt, rate);
		anim.put(id, v);
		return v;
	}

	private static float round(float v, float step) {
		return Math.round(v / step) * step;
	}

	public boolean mouseClicked(double x, double y, int button) {
		if (button != 0) {
			return false;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(x, y)) {
				if (hit.drag != null) {
					dragging = hit;
					applyDrag(x);
				} else if (hit.click != null) {
					hit.click.run();
				}
				return true;
			}
		}
		return false;
	}

	public void mouseDragged(double x, double y) {
		if (dragging != null) {
			applyDrag(x);
		}
	}

	public void mouseReleased() {
		dragging = null;
	}

	private void applyDrag(double x) {
		if (dragging == null || dragging.drag == null) {
			return;
		}
		float t = (float) ((x - dragging.dragX0) / Math.max(1, dragging.dragX1 - dragging.dragX0));
		dragging.drag.set(Easing.clamp01(t));
	}

	public boolean mouseScrolled(double x, double y, double amount) {
		if (x >= cx - 4 && x < cx + cw + 10 && y >= contentTop && y < contentBottom) {
			scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) amount * 26f));
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Sample data for the card preview when nothing is playing.

	private static final Lyrics SAMPLE_LYRICS = LrcParser.parse("[00:00.00]Your lyrics will glow right here\n[00:30.00]x", 60_000, "sample");

	private UiHost sampleHost() {
		UiHost real = host;
		return new UiHost() {
			@Override
			public ArtlyricsConfig config() {
				return real.config();
			}

			@Override
			public void settingsChanged() {
				real.settingsChanged();
			}

			@Override
			public NowPlaying track() {
				return new NowPlaying("Artlyrics", real.tr("artlyrics.ui.sample_title"), real.tr("artlyrics.ui.sample_artist"), "",
						60_000, 1_400, true, now);
			}

			@Override
			public long positionMs() {
				return 1_400;
			}

			@Override
			public Lyrics lyrics() {
				return SAMPLE_LYRICS;
			}

			@Override
			public LyricsManager.State lyricsState() {
				return LyricsManager.State.FOUND;
			}

			@Override
			public String problem() {
				return null;
			}

			@Override
			public boolean demoActive() {
				return false;
			}

			@Override
			public void setDemoActive(boolean active) {
			}

			@Override
			public void refreshLyrics() {
			}

			@Override
			public String version() {
				return real.version();
			}

			@Override
			public String tr(String key) {
				return real.tr(key);
			}

			@Override
			public void close() {
			}
		};
	}
}
