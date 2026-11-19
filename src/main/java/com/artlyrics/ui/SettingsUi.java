package com.artlyrics.ui;

import com.artlyrics.cover.CoverImage;
import com.artlyrics.holiday.Holiday;
import com.artlyrics.update.Updater;
import java.time.LocalDate;
import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.ArtlyricsConfig.TextFont;
import com.artlyrics.config.Look;
import com.artlyrics.config.LyricStyle;
import com.artlyrics.config.Palette;
import com.artlyrics.holiday.HolidayFinder;
import com.artlyrics.sky.SkyTheme;
import com.artlyrics.nowplaying.MediaCommand;
import com.artlyrics.spotify.SpotifyAccount;
import com.artlyrics.holiday.HolidayTheme;
import com.artlyrics.holiday.Region;
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
	/** The tabs in the top bar: the pages about the lyrics themselves, and everything else. */
	public enum Tab {
		LYRICS, EXTRAS;

		String key() {
			return "artlyrics.ui.tab." + name().toLowerCase(Locale.ROOT);
		}
	}

	public enum Page {
		HOME("home", Tab.LYRICS), NOW_PLAYING("note", Tab.LYRICS), LYRICS("lyrics", Tab.LYRICS), LOOK("palette", Tab.LYRICS),
		SKIES("sky", Tab.EXTRAS), HOLIDAYS("star", Tab.EXTRAS), PLACEMENT("compass", Tab.LYRICS), CARD("card", Tab.LYRICS),
		MUSIC("headphones", Tab.EXTRAS), SOURCES("search", Tab.EXTRAS);

		final String icon;
		final Tab tab;

		Page(String icon, Tab tab) {
			this.icon = icon;
			this.tab = tab;
		}

		String key() {
			return "artlyrics.page." + name().toLowerCase(Locale.ROOT);
		}
	}

	private static final TextFont UI = TextFont.SOFT;
	/** Height of the top bar. */
	static final int BAR_H = 24;
	/** Same as the hover renderer's size of one font pixel, in radians, at text size 1. */
	private static final float HOVER_RADIANS_PER_PX = 0.0068f;
	private static final int ROW_H = 26;
	private static final int NAV_H = 18;
	private static final int CONTROL_W = 112;

	/** Preferred player choices: label key suffix and the name fragment that's matched. */
	private static final String[][] PLAYERS = {
			{"any", ""}, {"spotify", "spotify"}, {"apple_music", "music"}, {"chrome", "chrom"},
			{"firefox", "firefox"}, {"edge", "edge"}, {"vlc", "vlc"}};

	private final UiHost host;
	private final SongCardView cardPreview = new SongCardView();

	private Page page = Page.HOME;
	private final Map<Tab, Page> lastPage = new java.util.EnumMap<>(Tab.class);
	private float tabX = -1;
	private float tabW;
	private @Nullable String tooltip;

	// Layout editor: drag the song card and the hover lyrics anywhere on the screen.
	private boolean layoutMode;
	private int layoutDrag;
	private double dragDx;
	private double dragDy;
	private final SongCardView layoutCard = new SongCardView();
	private int[] cardBox = new int[4];
	private int[] hoverBox = new int[4];
	private int screenW;
	private int screenH;
	private float focal;
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

	/** The pages under a tab, in sidebar order. */
	static List<Page> pagesIn(Tab tab) {
		List<Page> list = new ArrayList<>();
		for (Page p : Page.values()) {
			if (p.tab == tab) {
				list.add(p);
			}
		}
		return list;
	}

	private void switchTab(Tab tab) {
		if (page.tab != tab) {
			setPage(lastPage.getOrDefault(tab, pagesIn(tab).get(0)));
		}
	}

	/** Called when the screen closes. */
	public void onClosed() {
		SongCardView.hudHidden = false;
		layoutMode = false;
	}

	/** Opens the layout editor, where the song card and the hover lyrics are dragged into place. */
	public void openLayout() {
		layoutMode = true;
		layoutDrag = 0;
		SongCardView.hudHidden = true;
	}

	private void closeLayout() {
		layoutMode = false;
		layoutDrag = 0;
		SongCardView.hudHidden = false;
		host.settingsChanged();
	}

	public boolean layoutMode() {
		return layoutMode;
	}

	public void setPage(Page page) {
		if (this.page != page) {
			if (this.page.tab != page.tab) {
				navY = -1; // a different list: jump instead of sliding
			}
			this.page = page;
			lastPage.put(page.tab, page);
			pageNanos = now;
			scroll = 0;
			scrollTarget = 0;
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// Frame

	public void render(Canvas c, int mouseX, int mouseY, long nowNanos) {
		Theme.apply(host.config().menuTheme);
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
		tooltip = null;

		ArtlyricsConfig config = host.config();
		accent = Theme.accent(config.palette().from());
		float open = Easing.inOutSine((now - openedNanos) / 220_000_000f);
		screenW = c.width();
		screenH = c.height();
		if (layoutMode) {
			renderLayout(c);
			drawTooltip(c);
			return;
		}

		c.fill(0, 0, c.width(), c.height(), Draw.withAlpha(Theme.SCRIM, ((Theme.SCRIM >>> 24) / 255f) * open));

		pw = Math.min(480, c.width() - 16);
		ph = Math.min(300, c.height() - 16);
		px = (c.width() - pw) / 2;
		py = (c.height() - ph) / 2 + Math.round((1f - open) * 10f);
		side = pw < 380 ? 30 : 112;
		cx = px + side + 12;
		cw = pw - side - 24;
		contentTop = py + BAR_H + 44;
		contentBottom = py + ph - 8;

		float baseAlpha = c.alpha();
		c.setAlpha(baseAlpha * open);

		// Panel and sidebar.
		Draw.roundRect(c, px, py, px + pw, py + ph, 8, Theme.PANEL);
		Draw.roundRectLeft(c, px, py, px + side, py + ph, 8, Theme.SIDEBAR);
		c.fill(px + side, py + BAR_H, px + side + 1, py + ph, Theme.LINE);
		drawTopBar(c);
		Draw.roundOutline(c, px, py, px + pw, py + ph, 8, Draw.withAlpha(Theme.HIGHLIGHT, 0.09f));
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
			case HOLIDAYS -> pageHolidays(c, start);
			case SOURCES -> pageSources(c, start);
			case HOME -> pageHome(c, start);
			case SKIES -> pageSkies(c, start);
		};
		c.unclip();
		c.setAlpha(baseAlpha * open);
		int contentHeight = end - start + 6;
		maxScroll = Math.max(0, contentHeight - (contentBottom - contentTop));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		drawScrollbar(c, contentHeight);
		c.setAlpha(baseAlpha);
		drawTooltip(c);
	}

	// ---- Top bar ----

	/** The bar across the top: logo, the Lyrics / Extras tabs, and buttons (update, layout, folder, theme, help, close). */
	private void drawTopBar(Canvas c) {
		ArtlyricsConfig config = host.config();
		Draw.roundRectTop(c, px, py, px + pw, py + BAR_H, 8, Theme.SIDEBAR);
		c.fill(px, py + BAR_H, px + pw, py + BAR_H + 1, Theme.LINE);
		boolean compact = side < 60;
		double seconds = now / 1e9;
		int ly = py + 5;
		c.gradientV(px + 9, ly, px + 23, ly + 14, 0xFF000000 | config.palette().colorAt(0f, seconds),
				0xFF000000 | config.palette().colorAt(1f, seconds));
		c.icon("note", px + 11, ly + 2, 0xFFFFFFFF);
		if (!compact) {
			c.text("Artlyrics", UI, px + 28, ly + 3, Theme.TEXT, false);
		}

		// Tabs, with the selected one's background gliding between them.
		int tabsX = compact ? px + 30 : px + side + 8;
		int ty = py + 4;
		int th = 16;
		int x = tabsX;
		for (Tab tab : Tab.values()) {
			String label = host.tr(tab.key()).toUpperCase(Locale.ROOT);
			int w = Math.max(compact ? 46 : 58, c.textWidth(label, UI) + 22);
			boolean selected = page.tab == tab;
			if (selected) {
				tabX = tabX < 0 ? x : tabX + (x - tabX) * Easing.approach(dt, 16f);
				tabW = tabW <= 0 ? w : tabW + (w - tabW) * Easing.approach(dt, 16f);
			}
			x += w + 2;
		}
		Draw.roundRect(c, Math.round(tabX), ty, Math.round(tabX + tabW), ty + th, 4, Theme.CARD_HOVER);
		Draw.roundOutline(c, Math.round(tabX), ty, Math.round(tabX + tabW), ty + th, 4, Draw.withAlpha(Theme.HIGHLIGHT, 0.08f));
		x = tabsX;
		for (Tab tab : Tab.values()) {
			String label = host.tr(tab.key()).toUpperCase(Locale.ROOT);
			int w = Math.max(compact ? 46 : 58, c.textWidth(label, UI) + 22);
			boolean selected = page.tab == tab;
			float h = hover("tab:" + tab, inside(x, ty, x + w, ty + th) && !selected);
			int color = selected ? Theme.TEXT : Draw.lerpArgb(Theme.FAINT, Theme.TEXT, 0.35f + 0.4f * h);
			c.text(label, UI, x + (w - c.textWidth(label, UI)) / 2, ty + 4, color, false);
			hits.add(new Hit(x, ty, x + w, ty + th, () -> switchTab(tab), null, 0, 0));
			x += w + 2;
		}

		// Buttons, from the right.
		int bw = 20;
		int bx = px + pw - 6 - bw;
		int by = py + 4;
		barButton(c, "close", "close", bx, by, host.tr("artlyrics.ui.tip.close"), host::close, false, -1f);
		bx -= bw + 4;
		String repo = config.updateRepository == null || config.updateRepository.isBlank() ? "artxylv-byte/lyrics-mod" : config.updateRepository;
		String branch = config.updateBranches == null || config.updateBranches.isEmpty() ? "main" : config.updateBranches.get(config.updateBranches.size() - 1);
		barButton(c, "help", "help", bx, by, host.tr("artlyrics.ui.tip.help"),
				() -> host.openUrl("https://github.com/" + repo + "/blob/" + branch + "/README.md"), false, -1f);
		bx -= bw + 4;
		barButton(c, "theme", "theme", bx, by, String.format(Locale.ROOT, host.tr("artlyrics.ui.tip.theme"), host.tr(config.menuTheme.translationKey())), () -> {
			ArtlyricsConfig.MenuTheme[] themes = ArtlyricsConfig.MenuTheme.values();
			config.menuTheme = themes[(config.menuTheme.ordinal() + 1) % themes.length];
			host.settingsChanged();
		}, false, -1f);
		bx -= bw + 4;
		barButton(c, "folder", "folder", bx, by, host.tr("artlyrics.ui.tip.folder"), host::openLyricsFolder, false, -1f);
		bx -= bw + 4;
		barButton(c, "layout", "move", bx, by, host.tr("artlyrics.ui.tip.layout"), this::openLayout, false, -1f);
		bx -= bw + 4;
		updateButton(c, bx, by);
	}

	/** The update button: a dot when there's a new version; clicking does the next step. */
	private void updateButton(Canvas c, int x, int y) {
		Updater u = host.updater();
		if (u == null) {
			return;
		}
		String newest = u.latest() == null ? "" : u.latest().version();
		String tip;
		Runnable action;
		boolean badge = false;
		float progress = -1f;
		switch (u.state()) {
			case CHECKING -> {
				tip = host.tr("artlyrics.updates.checking");
				action = () -> { };
			}
			case UP_TO_DATE -> {
				tip = String.format(Locale.ROOT, host.tr("artlyrics.ui.tip.up_to_date"), u.currentVersion());
				action = () -> u.check(false);
			}
			case AVAILABLE -> {
				badge = true;
				tip = String.format(Locale.ROOT, host.tr(u.canInstall() ? "artlyrics.ui.tip.update_install" : "artlyrics.ui.tip.update_available"), newest);
				action = u.canInstall() ? u::install : () -> setPage(Page.MUSIC);
			}
			case DOWNLOADING -> {
				progress = u.progress();
				tip = host.tr("artlyrics.updates.downloading") + " " + Math.round(u.progress() * 100) + "%";
				action = () -> { };
			}
			case READY -> {
				badge = true;
				tip = String.format(Locale.ROOT, host.tr("artlyrics.ui.tip.update_ready"), newest);
				action = () -> setPage(Page.MUSIC);
			}
			case FAILED -> {
				tip = u.message().isBlank() ? host.tr("artlyrics.updates.failed") : u.message();
				action = () -> u.check(false);
			}
			default -> {
				tip = host.tr("artlyrics.ui.tip.update_check");
				action = () -> u.check(false);
			}
		}
		barButton(c, "update", "update", x, y, tip, action, badge, progress);
	}

	/** A square button in the top bar, with a tooltip, an optional dot and an optional progress line. */
	private void barButton(Canvas c, String id, String icon, int x, int y, String tip, Runnable action, boolean badge, float progress) {
		int w = 20;
		int h = 16;
		boolean over = inside(x, y, x + w, y + h);
		float hv = hover("bar:" + id, over);
		Draw.roundRect(c, x, y, x + w, y + h, 4, Draw.lerpArgb(Theme.CARD, Theme.CARD_HOVER, hv));
		Draw.roundOutline(c, x, y, x + w, y + h, 4, Draw.withAlpha(Theme.HIGHLIGHT, 0.08f + 0.08f * hv));
		c.icon(icon, x + (w - 10) / 2, y + (h - 10) / 2, Draw.lerpArgb(Theme.MUTED, Theme.TEXT, 0.4f + 0.6f * hv));
		if (progress >= 0f) {
			c.fill(x + 3, y + h - 3, x + w - 3, y + h - 2, Draw.withAlpha(Theme.HIGHLIGHT, 0.2f));
			c.fill(x + 3, y + h - 3, x + 3 + Math.round((w - 6) * progress), y + h - 2, accent);
		}
		if (badge) {
			float pulse = 0.5f + 0.5f * (float) Math.sin(now / 1e9 * 3.0);
			Draw.circle(c, x + w - 1.5f, y + 1.5f, 4f, Draw.withAlpha(accent & 0xFFFFFF, 0.25f * pulse));
			Draw.circle(c, x + w - 1.5f, y + 1.5f, 2.5f, accent);
		}
		if (over) {
			tooltip = tip;
		}
		hits.add(new Hit(x, y, x + w, y + h, action, null, 0, 0));
	}

	/** The tooltip of the button under the mouse, drawn on top of everything. */
	private void drawTooltip(Canvas c) {
		if (tooltip == null || tooltip.isBlank()) {
			return;
		}
		int w = c.textWidth(tooltip, UI) + 10;
		int h = 14;
		int x = Math.min(c.width() - w - 4, mouseX + 8);
		int y = mouseY + 14;
		if (y + h > c.height() - 4) {
			y = mouseY - h - 6;
		}
		Draw.roundRect(c, x, y, x + w, y + h, 4, 0xF0000000 | (Theme.CARD & 0xFFFFFF));
		Draw.roundOutline(c, x, y, x + w, y + h, 4, Draw.withAlpha(Theme.HIGHLIGHT, 0.15f));
		c.text(tooltip, UI, x + 5, y + 3, Theme.TEXT, false);
	}

	// ---- Layout editor ----

	/** Drag the song card and the hover lyrics anywhere; scroll over them to resize. */
	private void renderLayout(Canvas c) {
		ArtlyricsConfig config = host.config();
		int sw = c.width();
		int sh = c.height();
		c.fill(0, 0, sw, sh, 0x48000000);
		if (layoutDrag != 0) {
			int guide = Draw.withAlpha(accent & 0xFFFFFF, 0.4f);
			c.fill(sw / 2, 0, sw / 2 + 1, sh, guide);
			c.fill(0, sh / 2, sw, sh / 2 + 1, guide);
		}

		// Hover lyrics: where they hang relative to the crosshair.
		focal = (sh / 2f) / (float) Math.tan(Math.toRadians(host.fovDegrees() / 2f));
		float rad = HOVER_RADIANS_PER_PX * config.size;
		int hw = Math.max(40, Math.round(Math.min(config.lineWidth, 260f) * rad * focal));
		int hh = Math.max(10, Math.round(14f * rad * focal));
		float hx = sw / 2f + (float) Math.tan(Math.toRadians(config.hoverSide)) * focal;
		float hy = sh / 2f - (float) Math.tan(Math.toRadians(config.hoverRaise)) * focal;
		hoverBox = new int[] {Math.round(hx - hw / 2f), Math.round(hy - hh / 2f), hw, hh};
		boolean hoverMode = config.displayMode == ArtlyricsConfig.DisplayMode.HOVER;
		boolean overHover = layoutDrag == 2 || layoutDrag == 0 && inside(hoverBox[0], hoverBox[1], hoverBox[0] + hw, hoverBox[1] + hh);
		Draw.roundRect(c, hoverBox[0], hoverBox[1], hoverBox[0] + hw, hoverBox[1] + hh, 4, 0x55000000);
		Draw.roundOutline(c, hoverBox[0], hoverBox[1], hoverBox[0] + hw, hoverBox[1] + hh, 4,
				overHover ? accent : Draw.withAlpha(0xFFFFFF, hoverMode ? 0.55f : 0.3f));
		String sample = host.tr("artlyrics.layout.hover_sample");
		float ts = Math.max(0.6f, Math.min(2.5f, hh * 0.6f / 8f));
		c.push();
		c.translate(hoverBox[0] + hw / 2f - c.textWidth(sample, config.font) * ts / 2f, hoverBox[1] + hh / 2f - 4f * ts);
		c.scale(ts);
		c.text(sample, config.font, 0, 0, Draw.withAlpha(0xFFFFFF, hoverMode ? 0.95f : 0.5f), true);
		c.pop();
		String hoverLabel = host.tr("artlyrics.layout.hover") + (hoverMode ? "" : "  ·  " + host.tr("artlyrics.layout.hover_off"));
		c.text(hoverLabel, UI, hoverBox[0], hoverBox[1] - 11, overHover ? accent : 0xE0FFFFFF, true);

		// The song card, live, where it'll be.
		UiHost cardHost = host.track() != null ? host : sampleHost();
		NowPlaying track = cardHost.track();
		if (track != null && config.songCard) {
			float s = config.cardScale;
			int ch = layoutCard.measure(c, cardHost, now, true);
			int cwid = layoutCard.cardWidth(config);
			int w = Math.round(cwid * s);
			int h = Math.round(ch * s);
			int[] pos = SongCardView.position(config, sw, sh, w, h);
			c.push();
			c.translate(pos[0], pos[1]);
			c.scale(s);
			layoutCard.drawCard(c, cardHost, track, 0, 0, cwid, ch, now);
			c.pop();
			cardBox = new int[] {pos[0], pos[1], w, h};
			boolean overCard = layoutDrag == 1 || layoutDrag == 0 && !overHover && inside(pos[0], pos[1], pos[0] + w, pos[1] + h);
			Draw.roundOutline(c, pos[0] - 2, pos[1] - 2, pos[0] + w + 2, pos[1] + h + 2, 5, overCard ? accent : 0x80FFFFFF);
			String label = host.tr("artlyrics.layout.card") + "  ·  " + Math.round(s * 100) + "%";
			int labelY = pos[1] > 14 ? pos[1] - 12 : pos[1] + h + 4;
			c.text(label, UI, pos[0], labelY, overCard ? accent : 0xE0FFFFFF, true);
		} else {
			cardBox = new int[4];
		}

		// Toolbar.
		String hint = host.tr("artlyrics.layout.hint");
		int resetW = c.textWidth(host.tr("artlyrics.layout.reset"), UI) + 16;
		int doneW = c.textWidth(host.tr("artlyrics.layout.done"), UI) + 20;
		int barW = c.textWidth(hint, UI) + resetW + doneW + 34;
		int bx0 = (sw - barW) / 2;
		int by0 = sh - 34;
		Theme.apply(config.menuTheme);
		Draw.roundRect(c, bx0, by0, bx0 + barW, by0 + 24, 6, Theme.PANEL);
		Draw.roundOutline(c, bx0, by0, bx0 + barW, by0 + 24, 6, Draw.withAlpha(Theme.HIGHLIGHT, 0.12f));
		c.text(hint, UI, bx0 + 10, by0 + 8, Theme.MUTED, false);
		int rx = bx0 + barW - doneW - resetW - 12;
		layoutButton(c, "reset", host.tr("artlyrics.layout.reset"), rx, by0 + 4, resetW, false, () -> {
			ArtlyricsConfig d = new ArtlyricsConfig();
			config.cardCorner = d.cardCorner;
			config.cardX = d.cardX;
			config.cardY = d.cardY;
			config.cardScale = d.cardScale;
			config.hoverRaise = d.hoverRaise;
			config.hoverSide = d.hoverSide;
			host.settingsChanged();
		});
		layoutButton(c, "done", host.tr("artlyrics.layout.done"), bx0 + barW - doneW - 6, by0 + 4, doneW, true, this::closeLayout);
	}

	private void layoutButton(Canvas c, String id, String label, int x, int y, int w, boolean primary, Runnable action) {
		float hv = hover("layout:" + id, inside(x, y, x + w, y + 16));
		int bg = primary ? Draw.lerpArgb(accent, 0xFFFFFFFF, 0.15f * hv) : Draw.lerpArgb(Theme.CARD, Theme.CARD_HOVER, hv);
		Draw.roundRect(c, x, y, x + w, y + 16, 4, bg);
		c.text(label, UI, x + (w - c.textWidth(label, UI)) / 2, y + 4, primary ? Theme.readable(0xFFFFFFFF) : Theme.TEXT, false);
		hits.add(new Hit(x, y, x + w, y + 16, action, null, 0, 0));
	}

	/** Snaps v to the nearest of the targets when it's within a few pixels. */
	private static float snap(float v, float... targets) {
		for (float t : targets) {
			if (Math.abs(v - t) < 5f) {
				return t;
			}
		}
		return v;
	}

	private void layoutDrag(double x, double y) {
		ArtlyricsConfig config = host.config();
		if (layoutDrag == 1) {
			int w = cardBox[2];
			int h = cardBox[3];
			float nx = snap((float) (x - dragDx), 6f, (screenW - w) / 2f, screenW - w - 6f);
			float ny = snap((float) (y - dragDy), 6f, (screenH - h) / 2f, screenH - h - 6f);
			config.cardCorner = ArtlyricsConfig.CardCorner.CUSTOM;
			config.cardX = Easing.clamp01(nx / Math.max(1f, screenW - w));
			config.cardY = Easing.clamp01(ny / Math.max(1f, screenH - h));
		} else if (layoutDrag == 2) {
			float cxp = snap((float) (x - dragDx), screenW / 2f);
			float cyp = snap((float) (y - dragDy), screenH / 2f);
			float side = (float) Math.toDegrees(Math.atan((cxp - screenW / 2f) / focal));
			float raise = (float) Math.toDegrees(Math.atan((screenH / 2f - cyp) / focal));
			config.hoverSide = Math.max(-45f, Math.min(45f, Math.round(side * 2f) / 2f));
			config.hoverRaise = Math.max(-30f, Math.min(35f, Math.round(raise * 2f) / 2f));
		}
	}

	private void drawSidebar(Canvas c) {
		boolean compact = side < 60;
		List<Page> pages = pagesIn(page.tab);
		int navTop = py + BAR_H + 8;
		// Fit every page: rows get a little tighter when there are many.
		int step = Math.max(14, Math.min(NAV_H + 2, (py + ph - 30 - navTop) / pages.size()));
		int itemH = step - 2;
		int selectedIndex = pages.indexOf(page);
		float targetY = navTop + selectedIndex * step;
		navY = navY < 0 ? targetY : navY + (targetY - navY) * Easing.approach(dt, 16f);
		int sx0 = px + 6;
		int sx1 = px + side - 6;
		Draw.roundRect(c, sx0, Math.round(navY), sx1, Math.round(navY) + itemH, 4, Draw.withAlpha(accent, 0.18f));
		c.fill(sx0, Math.round(navY) + 4, sx0 + 2, Math.round(navY) + itemH - 4, accent);

		for (Page p : pages) {
			int iy = navTop + pages.indexOf(p) * step;
			boolean hovered = inside(sx0, iy, sx1, iy + itemH);
			float h = hover("nav:" + p, hovered && p != page);
			if (h > 0.01f) {
				Draw.roundRect(c, sx0, iy, sx1, iy + itemH, 4, Draw.withAlpha(Theme.HIGHLIGHT, 0.05f * h));
			}
			int color = p == page ? Theme.TEXT : Draw.lerpArgb(Theme.MUTED, Theme.TEXT, h);
			c.icon(p.icon, sx0 + (compact ? (sx1 - sx0 - 10) / 2 : 6), iy + (itemH - 10) / 2, p == page ? accent : color);
			if (!compact) {
				c.text(host.tr(p.key()), UI, sx0 + 21, iy + (itemH - 8) / 2, color, false);
			}
			hits.add(new Hit(sx0, iy, sx1, iy + itemH, () -> setPage(p), null, 0, 0));
		}

		if (!compact) {
			String version = host.version();
			int plus = version.indexOf('+');
			String shown = "v" + (plus > 0 ? version.substring(0, plus) : version);
			c.text(shown, null, px + 12, py + ph - 14, Theme.FAINT, false);
			Updater updater = host.updater();
			if (updater != null && updater.latest() != null
					&& (updater.state() == Updater.State.AVAILABLE || updater.state() == Updater.State.READY)) {
				c.text(" → " + updater.latest().version(), null, px + 12 + c.textWidth(shown, null), py + ph - 14, accent, false);
			}
		}
	}

	private void drawHeader(Canvas c) {
		int y = py + BAR_H + 9;
		c.push();
		c.translate(cx, y);
		c.scale(1.4f);
		c.text(host.tr(page.key()), UI, 0, 0, Theme.TEXT, false);
		c.pop();
		c.text(Draw.ellipsize(c, host.tr(page.key() + ".desc"), UI, cw - 90), UI, cx, y + 16, Theme.MUTED, false);

		// Master switch.
		ArtlyricsConfig config = host.config();
		int x1 = px + pw - 12;
		int tw = 22;
		int ty = y + 2;
		String label = host.tr(config.enabled ? "artlyrics.ui.on" : "artlyrics.ui.off");
		c.text(label, UI, x1 - tw - 6 - c.textWidth(label, UI), ty + 2, Theme.MUTED, false);
		drawSwitch(c, "master", x1 - tw, ty, config.enabled);
		hits.add(new Hit(x1 - tw - 30, ty - 2, x1, ty + 14, () -> {
			config.enabled = !config.enabled;
			host.settingsChanged();
		}, null, 0, 0));

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
		Draw.roundRect(c, x, barY, x + 3, barY + barH, 1, Draw.withAlpha(Theme.HIGHLIGHT, 0.25f));
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
		CoverImage cover = config.cardCover ? host.cover() : null;
		if (cover != null) {
			c.cover(cover, config.coverAnimate ? now / 1_000_000L : 0L, ax, ay, art);
		}

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
			Draw.roundRect(c, b0, barY + 3, b1, barY + 6, 1, Draw.withAlpha(Theme.HIGHLIGHT, 0.19f));
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
		if (config.mediaControls && track != null) {
			y = mediaBar(c, track, y);
		}

		y = section(c, "artlyrics.ui.section.quick", y);
		y = rowToggle(c, "demo", "artlyrics.option.demo", y, host.demoActive(), host::setDemoActive);
		y = rowSelector(c, "mode", "artlyrics.option.mode", y, config.displayMode.ordinal(),
				labels(ArtlyricsConfig.DisplayMode.values(), m -> m.translationKey()), i -> config.displayMode = ArtlyricsConfig.DisplayMode.values()[i]);
		y = rowToggle(c, "mediaControlsQuick", "artlyrics.option.media_controls", y, config.mediaControls, v -> config.mediaControls = v);
		y = statusSection(c, y);
		c.text(Draw.ellipsize(c, host.tr("artlyrics.ui.keybind_hint"), UI, cw), UI, cx + 8, y, Theme.FAINT, false);
		return y + 14;
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
		long t = host.lyricsTimeMs();
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
		if (config.displayMode == ArtlyricsConfig.DisplayMode.SKYWRITING) {
			y = rowSelector(c, "skyLook", "artlyrics.option.sky_look", y, config.skyLook.ordinal(),
					labels(ArtlyricsConfig.SkyLook.values(), l -> l.translationKey()), i -> config.skyLook = ArtlyricsConfig.SkyLook.values()[i]);
		}
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
		int song = host.songOffsetMs();
		String songText = song == 0 ? host.tr("artlyrics.ui.song_timing_none")
				: (song > 0 ? "+" : "") + String.format(Locale.ROOT, "%.2f s", song / 1000f);
		y = rowValueButton(c, "songTiming", "artlyrics.option.song_timing", y, songText, "artlyrics.ui.reset", host::resetSongOffset);
		y = rowToggle(c, "wholeLine", "artlyrics.option.whole_line", y, config.wholeLine, v -> config.wholeLine = v);
		y = rowToggle(c, "preview", "artlyrics.option.preview", y, config.nextLinePreview, v -> config.nextLinePreview = v);
		if (config.nextLinePreview) {
			y = rowSlider(c, "previewOpacity", "artlyrics.option.preview_opacity", y, config.previewOpacity, 0.15f, 0.8f,
					Math.round(config.previewOpacity * 100) + "%", v -> config.previewOpacity = round(v, 0.05f));
		}
		return y;
	}

	private int pageLook(Canvas c, int y) {
		ArtlyricsConfig config = host.config();
		double seconds = now / 1e9;

		// The menu's own colors.
		y = section(c, "artlyrics.ui.section.menu", y);
		ArtlyricsConfig.MenuTheme[] menuThemes = ArtlyricsConfig.MenuTheme.values();
		y = rowSelector(c, "menuTheme", "artlyrics.option.menu_theme", y, config.menuTheme.ordinal(),
				labels(menuThemes, ArtlyricsConfig.MenuTheme::translationKey), i -> config.menuTheme = menuThemes[i]);
		y += 4;

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
			if (look.uppercase) {
				name = name.toUpperCase(Locale.ROOT);
			}
			int nw = c.textWidth(name, look.font);
			c.push();
			c.translate(tx + (tileW - nw * 1.25f) / 2f, ty + 6);
			c.scale(1.25f);
			c.text(name, look.font, 0, 0, Theme.readable(0xFF000000 | p.colorAt(0.2f, seconds)), false);
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
			if (style == LyricStyle.AURORA || style == LyricStyle.RAINBOW) {
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
		if (config.textEffect != ArtlyricsConfig.TextEffect.NONE) {
			ArtlyricsConfig.EffectColor[] effectColors = ArtlyricsConfig.EffectColor.values();
			y = rowSelector(c, "effectColor", "artlyrics.option.effect_color", y, config.effectColor.ordinal(),
					labels(effectColors, ArtlyricsConfig.EffectColor::translationKey), i -> config.effectColor = effectColors[i]);
		}
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
		y = rowButton(c, "hoverLayout", "artlyrics.option.layout", "artlyrics.option.layout.button", y, this::openLayout);
		y = rowSlider(c, "hoverDistance", "artlyrics.option.hover_distance", y, config.hoverDistance, 2f, 10f,
				String.format(Locale.ROOT, "%.1f %s", config.hoverDistance, host.tr("artlyrics.ui.blocks")), v -> config.hoverDistance = round(v, 0.5f));
		y = rowSlider(c, "hoverRaise", "artlyrics.option.hover_raise", y, config.hoverRaise, -30f, 35f,
				Math.round(config.hoverRaise) + "°", v -> config.hoverRaise = Math.round(v));
		y = rowSlider(c, "hoverSide", "artlyrics.option.hover_side", y, config.hoverSide, -45f, 45f,
				Math.round(config.hoverSide) + "°", v -> config.hoverSide = Math.round(v));
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
			Draw.roundRect(c, cx, y, cx + cw, y + areaH, 6, Theme.PREVIEW_BG);
			int cardW = cardPreview.cardWidth(config);
			float s = Math.min(1f, (cw - 16) / (float) cardW);
			c.push();
			c.translate(cx + (cw - cardW * s) / 2f, y + 8);
			c.scale(s);
			cardPreview.drawCard(c, previewHost, track, 0, 0, cardW, h, now);
			c.pop();
			y += areaH + 6;
		}
		y = rowToggle(c, "card", "artlyrics.option.song_card", y, config.songCard, v -> config.songCard = v);
		y = rowSelector(c, "cardStyle", "artlyrics.option.card_style", y, config.cardStyle.ordinal(),
				labels(ArtlyricsConfig.CardStyle.values(), k -> k.translationKey()), i -> config.cardStyle = ArtlyricsConfig.CardStyle.values()[i]);
		if (config.cardStyle == ArtlyricsConfig.CardStyle.PLAYER) {
			y = rowToggle(c, "cardVinyl", "artlyrics.option.card_vinyl", y, config.cardVinyl, v -> config.cardVinyl = v);
			y = rowToggle(c, "cardBars", "artlyrics.option.card_bars", y, config.cardVisualizer, v -> config.cardVisualizer = v);
		}
		y = rowSelector(c, "corner", "artlyrics.option.card_corner", y, config.cardCorner.ordinal(),
				labels(ArtlyricsConfig.CardCorner.values(), k -> k.translationKey()), i -> config.cardCorner = ArtlyricsConfig.CardCorner.values()[i]);
		y = rowButton(c, "cardLayout", "artlyrics.option.layout", "artlyrics.option.layout.button", y, this::openLayout);
		y = rowSlider(c, "cardScale", "artlyrics.option.card_scale", y, config.cardScale, 0.6f, 1.6f,
				Math.round(config.cardScale * 100) + "%", v -> config.cardScale = round(v, 0.05f));
		y = rowSlider(c, "cardOpacity", "artlyrics.option.card_opacity", y, config.cardOpacity, 0.2f, 1f,
				Math.round(config.cardOpacity * 100) + "%", v -> config.cardOpacity = round(v, 0.05f));
		y = rowToggle(c, "cardArtist", "artlyrics.option.card_artist", y, config.cardArtist, v -> config.cardArtist = v);
		y = rowToggle(c, "cardProgress", "artlyrics.option.card_progress", y, config.cardProgress, v -> config.cardProgress = v);
		y = rowToggle(c, "cardNext", "artlyrics.option.card_next", y, config.cardNextLine, v -> config.cardNextLine = v);

		y = section(c, "artlyrics.ui.section.cover", y + 2);
		y = rowToggle(c, "cardCover", "artlyrics.option.card_cover", y, config.cardCover, v -> config.cardCover = v);
		if (config.cardCover) {
			if (config.cardStyle == ArtlyricsConfig.CardStyle.COMPACT) {
				y = rowToggle(c, "bigCover", "artlyrics.option.big_cover", y, config.cardBigCover, v -> config.cardBigCover = v);
			}
			y = rowToggle(c, "coverAnimate", "artlyrics.option.cover_animate", y, config.coverAnimate, v -> config.coverAnimate = v);
		}
		return y;
	}

	private int pageHolidays(Canvas c, int y) {
		ArtlyricsConfig config = host.config();
		HolidayFinder.Mode[] modes = HolidayFinder.Mode.values();
		y = rowSelector(c, "holidayMode", "artlyrics.option.holidays", y, config.holidays.ordinal(),
				labels(modes, HolidayFinder.Mode::translationKey), i -> config.holidays = modes[i]);

		Region[] regions = Region.values();
		String[] regionNames = new String[regions.length + 1];
		String auto = host.autoRegionName();
		regionNames[0] = host.tr("artlyrics.holidays.auto") + (auto.isEmpty() ? "" : " (" + auto + ")");
		int regionIndex = 0;
		for (int i = 0; i < regions.length; i++) {
			regionNames[i + 1] = regions[i].displayName();
			if (regions[i].code().equalsIgnoreCase(config.holidayRegion)) {
				regionIndex = i + 1;
			}
		}
		y = rowSelector(c, "region", "artlyrics.option.region", y, regionIndex, regionNames,
				i -> config.holidayRegion = i == 0 ? "auto" : regions[i - 1].code());
		y = rowToggle(c, "holidayColors", "artlyrics.option.holiday_colors", y, config.holidayColors, v -> config.holidayColors = v);
		y = rowToggle(c, "holidayDecor", "artlyrics.option.holiday_decorations", y, config.holidayDecorations,
				v -> config.holidayDecorations = v);

		HolidayTheme[] themes = HolidayTheme.values();
		String[] themeNames = new String[themes.length + 1];
		themeNames[0] = host.tr("artlyrics.holidays.preview_none");
		int themeIndex = 0;
		for (int i = 0; i < themes.length; i++) {
			themeNames[i + 1] = themes[i].label();
			if (themes[i].name().equalsIgnoreCase(config.holidayPreview)) {
				themeIndex = i + 1;
			}
		}
		y = rowSelector(c, "holidayPreview", "artlyrics.option.holiday_preview", y, themeIndex, themeNames,
				i -> config.holidayPreview = i == 0 ? "" : themes[i - 1].name());

		// What's shown now, and the region's next holidays.
		y = section(c, "artlyrics.ui.section.holiday_now", y + 2);
		HolidayFinder.Active active = host.holiday();
		String now = active == null ? host.tr("artlyrics.holidays.none_now")
				: active.holiday().name() + "  ·  " + when(active.daysAway());
		c.text(Draw.ellipsize(c, now, UI, cw - 16), UI, cx + 8, y + 2, active != null ? Theme.TEXT : Theme.MUTED, false);
		y += 15;
		Region region = host.holidayRegion();
		if (region != null) {
			LocalDate today = LocalDate.now();
			List<String[]> upcoming = new ArrayList<>();
			List<LocalDate> dates = new ArrayList<>();
			for (Holiday h : region.holidays()) {
				LocalDate d = h.dateIn(today.getYear());
				if (d.isBefore(today)) {
					d = h.dateIn(today.getYear() + 1);
				}
				int at = 0;
				while (at < dates.size() && !dates.get(at).isAfter(d)) {
					at++;
				}
				dates.add(at, d);
				upcoming.add(at, new String[] {h.name(), d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))});
			}
			c.text(Draw.ellipsize(c, host.tr("artlyrics.holidays.coming_up") + " " + region.displayName(), UI, cw - 16), UI,
					cx + 8, y + 2, Theme.FAINT, false);
			y += 13;
			for (int i = 0; i < Math.min(5, upcoming.size()); i++) {
				String[] row = upcoming.get(i);
				c.text(Draw.ellipsize(c, row[0], UI, cw - 80), UI, cx + 14, y + 2, Theme.MUTED, false);
				c.text(row[1], UI, cx + cw - 8 - c.textWidth(row[1], UI), y + 2, Theme.MUTED, false);
				y += 12;
			}
		}
		return y + 4;
	}

	/** Update mode, the installed version, and a button that does the next step (check, update, restart). */
	private int updatesSection(Canvas c, ArtlyricsConfig config, int y) {
		y = section(c, "artlyrics.ui.section.updates", y + 2);
		ArtlyricsConfig.UpdateMode[] modes = ArtlyricsConfig.UpdateMode.values();
		y = rowSelector(c, "updates", "artlyrics.option.updates", y, config.updates.ordinal(),
				labels(modes, ArtlyricsConfig.UpdateMode::translationKey), i -> config.updates = modes[i]);
		Updater u = host.updater();
		if (u == null) {
			return y;
		}
		String newest = u.latest() == null ? "" : u.latest().version();
		String status;
		String button;
		Runnable action;
		switch (u.state()) {
			case CHECKING -> {
				status = host.tr("artlyrics.updates.checking");
				button = "…";
				action = () -> { };
			}
			case UP_TO_DATE -> {
				status = host.tr("artlyrics.updates.up_to_date");
				button = host.tr("artlyrics.updates.check");
				action = () -> u.check(false);
			}
			case AVAILABLE -> {
				status = String.format(Locale.ROOT, host.tr("artlyrics.updates.available"), newest);
				button = u.canInstall() ? String.format(Locale.ROOT, host.tr("artlyrics.updates.update_to"), newest)
						: host.tr("artlyrics.updates.check");
				action = u.canInstall() ? u::install : () -> u.check(false);
			}
			case DOWNLOADING -> {
				status = host.tr("artlyrics.updates.downloading");
				button = Math.round(u.progress() * 100) + "%";
				action = () -> { };
			}
			case READY -> {
				status = String.format(Locale.ROOT, host.tr("artlyrics.updates.ready"), newest);
				button = host.tr("artlyrics.updates.quit");
				action = host::quitGame;
			}
			case FAILED -> {
				status = u.message().isBlank() ? host.tr("artlyrics.updates.failed") : u.message();
				button = host.tr("artlyrics.updates.check");
				action = () -> u.check(false);
			}
			default -> {
				status = host.tr("artlyrics.updates.not_checked");
				button = host.tr("artlyrics.updates.check");
				action = () -> u.check(false);
			}
		}
		return rowInfoButton(c, "updateNow", y, "Artlyrics " + u.currentVersion(), status, button, action);
	}

	/** A row with its own title and status text (not from translations) and a button. */
	private int rowInfoButton(Canvas c, String id, int y, String title, String status, String buttonText, Runnable action) {
		int bw = Math.max(48, c.textWidth(buttonText, UI) + 16);
		rowH = ROW_H;
		float hv = hover("row:" + id, insideContent(cx, y, cx + cw, y + rowH - 2));
		if (hv > 0.01f) {
			Draw.roundRect(c, cx, y, cx + cw, y + rowH - 2, 4, Draw.withAlpha(Theme.HIGHLIGHT, 0.04f * hv));
		}
		int textMax = cw - bw - 22;
		c.text(Draw.ellipsize(c, title, UI, textMax), UI, cx + 8, y + 3, Theme.TEXT, false);
		c.text(Draw.ellipsize(c, status, UI, textMax), UI, cx + 8, y + 13, Theme.MUTED, false);
		int x1 = cx + cw - 8;
		int x0 = x1 - bw;
		int by = rowMid(y) - 7;
		float h = hover("btn:" + id, insideContent(x0, by, x1, by + 14));
		Draw.roundRect(c, x0, by, x1, by + 14, 4, Draw.lerpArgb(Draw.withAlpha(accent, 0.25f), Draw.withAlpha(accent, 0.45f), h));
		c.text(buttonText, UI, x0 + (bw - c.textWidth(buttonText, UI)) / 2, by + 3, Theme.TEXT, false);
		contentHit(x0, by, x1, by + 14, action);
		return y + rowH;
	}

	private String when(long daysAway) {
		if (daysAway == 0) {
			return host.tr("artlyrics.holidays.today");
		}
		return String.format(Locale.ROOT, host.tr(daysAway < 0 ? "artlyrics.holidays.in_days" : "artlyrics.holidays.days_ago"),
				Math.abs(daysAway));
	}

	// ---- Home ----

	private static final Page[] HOME_TILES = {Page.LYRICS, Page.SKIES, Page.LOOK, Page.CARD, Page.HOLIDAYS, Page.MUSIC};

	/** A welcome banner, shortcuts to the main pages, and what's playing. */
	private int pageHome(Canvas c, int y) {
		ArtlyricsConfig config = host.config();
		Palette palette = config.palette();
		double seconds = now / 1e9;
		SkyTheme sky = config.sky;

		// Banner: the chosen sky (or the palette's colors when there is none), darker behind the words.
		int bh = 60;
		if (sky == SkyTheme.NONE) {
			int top = Palette.lerpRgb(palette.from(), 0x000000, 0.55f);
			int bottom = Palette.lerpRgb(palette.to(), 0x000000, 0.3f);
			c.gradientV(cx, y, cx + cw, y + bh, 0xFF000000 | top, 0xFF000000 | bottom);
			drawStars(c, cx, y, cx + cw, y + bh - 8, 28, seconds);
		} else {
			skyArt(c, sky, cx, y, cx + cw, y + bh, seconds, 0.84f);
		}
		Draw.gradientH(c, cx, y, cx + cw * 2 / 3, y + bh, 0x99000000, 0x00000000);
		int lx = cx + 12;
		c.gradientV(lx, y + 16, lx + 28, y + 44, 0xFF000000 | palette.colorAt(0f, seconds), 0xFF000000 | palette.colorAt(1f, seconds));
		c.push();
		c.translate(lx + 4, y + 20);
		c.scale(2f);
		c.icon("note", 0, 0, 0xF0FFFFFF);
		c.pop();
		c.push();
		c.translate(lx + 36, y + 17);
		c.scale(1.6f);
		c.text("Artlyrics", UI, 0, 0, 0xFFFFFFFF, true);
		c.pop();
		c.text(host.tr("artlyrics.home.tagline"), UI, lx + 37, y + 34, 0xE0FFFFFF, true);
		y += bh + 8;

		// Shortcuts.
		int cols = cw >= 300 ? 3 : 2;
		int gap = 6;
		int tw = (cw - gap * (cols - 1)) / cols;
		int th = 40;
		for (int i = 0; i < HOME_TILES.length; i++) {
			Page p = HOME_TILES[i];
			int tx = cx + (i % cols) * (tw + gap);
			int ty = y + (i / cols) * (th + gap);
			float h = hover("home:" + p, insideContent(tx, ty, tx + tw, ty + th));
			Draw.roundRect(c, tx, ty, tx + tw, ty + th, 6, Draw.lerpArgb(Theme.CARD, Theme.CARD_HOVER, h));
			Draw.roundRect(c, tx + 6, ty + 6, tx + 20, ty + 20, 4, Draw.withAlpha(accent, 0.22f + 0.15f * h));
			c.icon(p.icon, tx + 8, ty + 8, accent);
			c.text(Draw.ellipsize(c, host.tr(p.key()), UI, tw - 30), UI, tx + 25, ty + 9, Theme.TEXT, false);
			c.text(Draw.ellipsize(c, host.tr(p.key() + ".desc"), UI, tw - 12), UI, tx + 6, ty + 25, Theme.MUTED, false);
			c.text("→", null, tx + tw - 12 + Math.round(2 * h), ty + 9, Draw.lerpArgb(Theme.FAINT, accent, h), false);
			contentHit(tx, ty, tx + tw, ty + th, () -> setPage(p));
		}
		y += ((HOME_TILES.length + cols - 1) / cols) * (th + gap) + 2;

		// Now playing.
		y = section(c, "artlyrics.ui.section.now_playing", y);
		NowPlaying track = host.track();
		int sh = 40;
		Draw.roundRect(c, cx, y, cx + cw, y + sh, 6, Theme.CARD);
		int ax = cx + 6;
		int ay = y + 6;
		int art = 28;
		c.gradientV(ax, ay, ax + art, ay + art, 0xFF000000 | palette.colorAt(0f, seconds), 0xFF000000 | palette.colorAt(1f, seconds));
		c.icon("note", ax + 9, ay + 9, 0xE0FFFFFF);
		CoverImage cover = config.cardCover ? host.cover() : null;
		if (cover != null) {
			c.cover(cover, config.coverAnimate ? now / 1_000_000L : 0L, ax, ay, art);
		}
		int tx = ax + art + 8;
		int controlsW = config.mediaControls && track != null ? 3 * 24 + 2 * 4 + 6 : 0;
		int textW = cx + cw - 8 - controlsW - tx;
		if (track == null) {
			c.text(Draw.ellipsize(c, host.tr("artlyrics.ui.nothing_playing"), UI, textW), UI, tx, ay + 4, Theme.MUTED, false);
		} else {
			c.text(Draw.ellipsize(c, track.title(), UI, textW), UI, tx, ay + 1, Theme.TEXT, false);
			c.text(Draw.ellipsize(c, track.artist(), UI, textW), UI, tx, ay + 11, Theme.MUTED, false);
			int barY = ay + 23;
			Draw.roundRect(c, tx, barY, tx + textW, barY + 3, 1, Draw.withAlpha(Theme.HIGHLIGHT, 0.19f));
			if (track.durationMs() > 0) {
				int filled = tx + Math.round(textW * Math.min(1f, host.positionMs() / (float) track.durationMs()));
				Draw.gradientH(c, tx, barY, Math.max(tx + 1, filled), barY + 3, 0xFF000000 | palette.from(), 0xFF000000 | palette.to());
			}
			if (controlsW > 0) {
				int bx = cx + cw - 6 - (3 * 24 + 2 * 4);
				int by = y + (sh - 18) / 2;
				iconButton(c, "homePrev", bx, by, 24, 18, "prev", false, () -> host.sendMedia(MediaCommand.PREVIOUS));
				iconButton(c, "homePlay", bx + 28, by, 24, 18, track.playing() ? "pause" : "play", true, () -> host.sendMedia(MediaCommand.PLAY_PAUSE));
				iconButton(c, "homeNext", bx + 56, by, 24, 18, "next", false, () -> host.sendMedia(MediaCommand.NEXT));
			}
		}
		contentHit(cx, y, cx + cw - controlsW, y + sh, () -> setPage(Page.NOW_PLAYING));
		return y + sh + 6;
	}

	// ---- Skies ----

	private static final java.util.Map<SkyTheme, CoverImage> SKY_PREVIEWS = new java.util.EnumMap<>(SkyTheme.class);

	/** The sky's picture for previews, or null. */
	static synchronized CoverImage skyPreview(SkyTheme sky) {
		if (sky.texturePath() == null) {
			return null;
		}
		return SKY_PREVIEWS.computeIfAbsent(sky, s -> {
			try (java.io.InputStream in = SettingsUi.class.getResourceAsStream("/assets/artlyrics/" + s.texturePath())) {
				return in == null ? null : com.artlyrics.cover.CoverDecoder.decode(in.readAllBytes(), "sky");
			} catch (java.io.IOException e) {
				return null;
			}
		});
	}

	private int pageSkies(Canvas c, int y) {
		ArtlyricsConfig config = host.config();
		double seconds = now / 1e9;
		SkyTheme[] skies = SkyTheme.values();
		int cols = cw >= 300 ? 3 : 2;
		int gap = 6;
		int tw = (cw - gap * (cols - 1)) / cols;
		int ph = Math.round(tw * 0.56f);
		int th = ph + 18;
		for (int i = 0; i < skies.length; i++) {
			SkyTheme sky = skies[i];
			int tx = cx + (i % cols) * (tw + gap);
			int ty = y + (i / cols) * (th + gap);
			boolean selected = config.sky == sky;
			float h = hover("sky:" + sky, insideContent(tx, ty, tx + tw, ty + th));
			int card = Draw.lerpArgb(Theme.CARD, Theme.CARD_HOVER, h);
			Draw.roundRect(c, tx, ty, tx + tw, ty + th, 6, card);
			skyArt(c, sky, tx + 3, ty + 3, tx + tw - 3, ty + 3 + ph, seconds, 0.5f);
			Draw.roundCorners(c, tx + 3, ty + 3, tx + tw - 3, ty + 3 + ph, 4, card);
			if (selected) {
				Draw.roundOutline(c, tx, ty, tx + tw, ty + th, 6, accent);
				Draw.roundOutline(c, tx + 1, ty + 1, tx + tw - 1, ty + th - 1, 5, Draw.withAlpha(accent & 0xFFFFFF, 0.45f));
				Draw.circle(c, tx + tw - 11, ty + 11, 6f, accent);
				c.icon("check", tx + tw - 16, ty + 6, 0xFFFFFFFF);
			}
			String name = Draw.ellipsize(c, host.tr(sky.translationKey()), UI, tw - 10);
			c.text(name, UI, tx + 6, ty + ph + 7, selected ? accent : Theme.TEXT, false);
			contentHit(tx, ty, tx + tw, ty + th, () -> {
				config.sky = sky;
				host.settingsChanged();
			});
		}
		y += ((skies.length + cols - 1) / cols) * (th + gap);
		c.text(Draw.ellipsize(c, host.tr("artlyrics.ui.sky_gpu"), UI, cw), UI, cx + 2, y, Theme.FAINT, false);
		y += 14;

		y = section(c, "artlyrics.ui.section.sky_options", y);
		y = rowToggle(c, "skyNight", "artlyrics.option.sky_night", y, config.skyNightOnly, v -> config.skyNightOnly = v);
		y = rowSlider(c, "skyIntensity", "artlyrics.option.sky_intensity", y, config.skyIntensity, 0.2f, 1f,
				Math.round(config.skyIntensity * 100) + "%", v -> config.skyIntensity = round(v, 0.05f));
		y = rowSlider(c, "skySpeed", "artlyrics.option.sky_speed", y, config.skySpeed, 0f, 2f,
				String.format(Locale.ROOT, "%.1f×", config.skySpeed), v -> config.skySpeed = round(v, 0.1f));
		y = rowToggle(c, "skyStars", "artlyrics.option.sky_stars", y, config.skyStars, v -> config.skyStars = v);
		return rowSelector(c, "skyDetail", "artlyrics.option.sky_detail", y, config.skyDetail.ordinal(),
				labels(ArtlyricsConfig.SkyDetail.values(), k -> k.translationKey()), i -> config.skyDetail = ArtlyricsConfig.SkyDetail.values()[i]);
	}

	/** A sky picture: drawn live by the GPU where it can be, otherwise a simple drawing. */
	private void skyArt(Canvas c, SkyTheme sky, int x0, int y0, int x1, int y1, double seconds, float focus) {
		if (!c.sky(sky.ordinal(), x0, y0, x1 - x0, y1 - y0)) {
			drawSkyPreview(c, sky, x0, y0, x1, y1, seconds, focus);
		}
	}

	/** Twinkling dots scattered over an area. */
	private static void drawStars(Canvas c, int x0, int y0, int x1, int y1, int count, double seconds) {
		int w = Math.max(1, x1 - x0 - 4);
		int h = Math.max(1, y1 - y0 - 4);
		for (int i = 0; i < count; i++) {
			long hash = (i + 1) * 0x9E3779B97F4A7C15L;
			int sx = x0 + 2 + (int) Math.floorMod(hash >>> 17, (long) w);
			int sy = y0 + 2 + (int) Math.floorMod((hash * 31) >>> 23, (long) h);
			float tw = (float) (0.5 + 0.5 * Math.sin(seconds * (1.2 + (i % 4) * 0.5) + i * 2.3));
			c.fill(sx, sy, sx + 1, sy + 1, Draw.withAlpha(0xFFFFFF, 0.25f + 0.65f * tw));
		}
	}

	/** A small picture of a sky over dark hills; {@code focus} is where across it the sky's picture sits (0..1). */
	private void drawSkyPreview(Canvas c, SkyTheme sky, int x0, int y0, int x1, int y1, double seconds, float focus) {
		int w = x1 - x0;
		int h = y1 - y0;
		int fx = x0 + Math.round(w * focus);
		if (sky == SkyTheme.NONE) {
			c.gradientV(x0, y0, x1, y1, 0xFF6F9BE8, 0xFFBFD6F5);
		} else {
			c.gradientV(x0, y0, x1, y1, 0xFF000000 | sky.zenith, 0xFF000000 | sky.horizon);
		}
		if (sky.stars > 60) {
			drawStars(c, x0, y0, x1, y0 + h * 2 / 3, Math.max(10, w / 6), seconds);
		}
		switch (sky) {
			case AURORA -> {
				// Two curtains of light that wave slowly; brightest in the middle of the picture.
				for (int k = 0; k < 2; k++) {
					int color = k == 0 ? 0x5CFFB0 : 0x9A7CFF;
					for (int x = x0; x < x1; x += 2) {
						float edge = (float) Math.sin(Math.PI * (x - x0) / (double) w);
						int wy = y0 + Math.round(h * 0.22f + k * 6 + 3f * (float) Math.sin(x * 0.07 + seconds * 0.8 + k * 2.1));
						int len = Math.round(h * 0.3f + 4f * (float) Math.sin(x * 0.045 + seconds * 0.5 + k));
						float a = (k == 0 ? 0.65f : 0.4f) * edge * edge;
						c.gradientV(x, wy, Math.min(x1, x + 2), wy + len, Draw.withAlpha(color, 0f), Draw.withAlpha(color, a));
					}
				}
			}
			case LIGHT_PILLARS -> {
				for (int k = 0; k < 6; k++) {
					int bx = x0 + 6 + k * (w - 12) / 6;
					float pulse = (float) (0.5 + 0.5 * Math.sin(seconds * 0.8 + k * 1.7));
					c.gradientV(bx, y0 + 4, bx + 1, y1 - 6, Draw.withAlpha(0xD8C8FF, 0f), Draw.withAlpha(0xD8C8FF, 0.4f + 0.5f * pulse));
				}
			}
			case METEOR_SHOWER -> {
				for (int k = 0; k < 3; k++) {
					float p = (float) ((seconds * 0.5 + k / 3.0) % 1.0);
					int mx = x0 + w / 5 + k * w / 4 + Math.round(p * 20);
					int my = y0 + 6 + k * 5 + Math.round(p * 10);
					for (int d = 0; d < 6; d++) {
						c.fill(mx - d * 2, my - d, mx - d * 2 + 2, my - d + 1, Draw.withAlpha(0xFFFFFF, (1f - p) * (1f - d / 6f)));
					}
				}
			}
			case SAKURA_DUSK -> {
				for (int k = 0; k < Math.max(10, w / 9); k++) {
					float p = (float) ((seconds * 0.15 + k / 10.0) % 1.0);
					long hash = (k + 7) * 0x9E3779B97F4A7C15L;
					int px = x0 + 3 + (int) Math.floorMod(hash >>> 19, (long) Math.max(1, w - 6))
							+ Math.round(3f * (float) Math.sin(seconds + k));
					int py = y0 + Math.round(p * (h - 8));
					c.fill(px, py, px + 2, py + 1, 0xFFFFC2DA);
				}
			}
			case TWIN_MOONS -> {
				CoverImage moon = skyPreview(sky);
				if (moon != null) {
					int big = h / 2;
					int small = h / 4;
					c.cover(moon, 0, fx - big + 2, y0 + 5, big);
					c.cover(moon, 0, fx + 6, y0 + 3, small);
				}
			}
			default -> {
				CoverImage picture = skyPreview(sky);
				if (picture != null) {
					int size = sky == SkyTheme.SYNTH_SUNSET ? h - 6 : h - 4;
					int py = sky == SkyTheme.SYNTH_SUNSET ? y1 - size - 2 : y0 + 2;
					c.cover(picture, 0, fx - size / 2, py, size);
				}
			}
		}
		// Hills.
		for (int x = x0; x < x1; x += 2) {
			int hh = 4 + (int) Math.round(2.5 * Math.sin(x * 0.11) + 1.5 * Math.sin(x * 0.37 + 1));
			c.fill(x, y1 - hh, Math.min(x1, x + 2), y1, 0xFF0A0A10);
		}
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

		// Playback controls, and the Spotify account for liking and playlists.
		y = section(c, "artlyrics.ui.section.controls", y + 2);
		y = rowToggle(c, "mediaControls", "artlyrics.option.media_controls", y, config.mediaControls, v -> config.mediaControls = v);
		SpotifyAccount spotify = host.spotify();
		if (config.mediaControls && spotify != null) {
			String id = config.spotifyClientId.isBlank() ? host.tr("artlyrics.spotify.no_id")
					: config.spotifyClientId.substring(0, Math.min(8, config.spotifyClientId.length())) + "…";
			y = rowValueButton(c, "spotifyId", "artlyrics.option.spotify_id", y, id, "artlyrics.ui.paste", () -> {
				String pasted = host.clipboard().strip();
				if (!pasted.isEmpty()) {
					config.spotifyClientId = pasted;
					spotify.setClientId(pasted);
					host.settingsChanged();
				}
			});
			if (spotify.connected()) {
				y = rowInfoButton(c, "spotifyAccount", y, host.tr("artlyrics.option.spotify_account"),
						host.tr("artlyrics.spotify.connected_as") + " " + spotify.userName(), host.tr("artlyrics.ui.disconnect"),
						spotify::disconnect);
			} else {
				y = rowInfoButton(c, "spotifyAccount", y, host.tr("artlyrics.option.spotify_account"),
						host.tr("artlyrics.spotify.not_connected"), host.tr("artlyrics.ui.connect"), () -> {
							spotify.setClientId(config.spotifyClientId);
							spotify.connect();
						});
				y = rowButton(c, "spotifyHelp", "artlyrics.option.spotify_help", "artlyrics.ui.open_dashboard", y,
						() -> host.openUrl(SpotifyAccount.DASHBOARD));
				c.text(Draw.ellipsize(c, host.tr("artlyrics.spotify.redirect") + " " + SpotifyAccount.REDIRECT, UI, cw - 16), UI,
						cx + 8, y + 2, Theme.FAINT, false);
				y += 13;
			}
			if (!spotify.message().isBlank()) {
				c.text(Draw.ellipsize(c, spotify.message(), UI, cw - 16), UI, cx + 8, y + 2, Theme.MUTED, false);
				y += 13;
			}
		}

		return updatesSection(c, config, y);
	}

	/** Where lyrics and covers come from. Changing a source searches again right away. */
	private int pageSources(Canvas c, int y) {
		ArtlyricsConfig config = host.config();
		y = rowButton(c, "refresh", "artlyrics.option.refresh", "artlyrics.ui.search", y, host::refreshLyrics);
		y = section(c, "artlyrics.ui.section.sources", y + 2);
		y = rowToggle(c, "srcFolder", "artlyrics.option.source_folder", y, config.sourceFolder, v -> {
			config.sourceFolder = v;
			host.refreshLyrics();
		});
		y = rowButton(c, "openFolder", "artlyrics.option.open_folder", "artlyrics.ui.open", y, host::openLyricsFolder);
		y = rowToggle(c, "srcLrclib", "artlyrics.option.source_lrclib", y, config.sourceLrclib, v -> {
			config.sourceLrclib = v;
			host.refreshLyrics();
		});
		y = rowToggle(c, "srcNetease", "artlyrics.option.source_netease", y, config.sourceNetease, v -> {
			config.sourceNetease = v;
			host.refreshLyrics();
		});
		y = rowToggle(c, "estimate", "artlyrics.option.estimate", y, config.estimateUnsynced, v -> {
			config.estimateUnsynced = v;
			host.refreshLyrics();
		});
		y = section(c, "artlyrics.ui.section.covers", y + 2);
		y = rowToggle(c, "coverOnline", "artlyrics.option.cover_online", y, config.coverOnline, v -> {
			config.coverOnline = v;
			host.refreshLyrics();
		});
		return rowButton(c, "coversFolder", "artlyrics.option.covers_folder", "artlyrics.ui.open", y, host::openCoversFolder);
	}

	/** What's being followed and where the lyrics came from. */
	private int statusSection(Canvas c, int y) {
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
		String detail = host.lyricsDetail();
		String sourceLine = detail == null || detail.isBlank() ? host.tr("artlyrics.ui.lyrics_source")
				: host.tr("artlyrics.ui.lyrics_from") + " " + detail;
		c.text(Draw.ellipsize(c, sourceLine, UI, cw - 16), UI, cx + 8, y + 2, Theme.FAINT, false);
		return y + 16;
	}

	/** Previous, play / pause, next, and with Spotify connected: like and add to playlist. */
	private int mediaBar(Canvas c, NowPlaying track, int y) {
		SpotifyAccount spotify = host.spotify();
		boolean spotifyOn = spotify != null && spotify.connected();
		if (spotifyOn) {
			spotify.checkSong();
		}
		int bw = 30;
		int bh = 20;
		int gap = 6;
		int count = spotifyOn ? 5 : 3;
		int x = cx + (cw - (count * bw + (count - 1) * gap)) / 2;
		iconButton(c, "prev", x, y, bw, bh, "prev", false, () -> host.sendMedia(MediaCommand.PREVIOUS));
		x += bw + gap;
		iconButton(c, "playpause", x, y, bw, bh, track.playing() ? "pause" : "play", true, () -> host.sendMedia(MediaCommand.PLAY_PAUSE));
		x += bw + gap;
		iconButton(c, "next", x, y, bw, bh, "next", false, () -> host.sendMedia(MediaCommand.NEXT));
		if (spotifyOn) {
			x += bw + gap;
			boolean liked = Boolean.TRUE.equals(spotify.liked());
			iconButton(c, "like", x, y, bw, bh, liked ? "heart_full" : "heart", liked, spotify::toggleLike);
			x += bw + gap;
			iconButton(c, "playlist", x, y, bw, bh, "plus", playlistsOpen, () -> playlistsOpen = !playlistsOpen);
		}
		y += bh + 6;
		if (spotifyOn && !spotify.message().isBlank()) {
			String m = Draw.ellipsize(c, spotify.message(), UI, cw - 16);
			c.text(m, UI, cx + (cw - c.textWidth(m, UI)) / 2, y, Theme.MUTED, false);
			y += 12;
		} else if (!spotifyOn) {
			String m = Draw.ellipsize(c, host.tr("artlyrics.spotify.hint"), UI, cw - 16);
			c.text(m, UI, cx + (cw - c.textWidth(m, UI)) / 2, y, Theme.FAINT, false);
			y += 12;
		}
		if (spotifyOn && playlistsOpen) {
			y = section(c, "artlyrics.ui.section.add_to_playlist", y + 2);
			List<SpotifyAccount.Playlist> lists = spotify.playlists();
			if (lists.isEmpty()) {
				c.text(host.tr(spotify.busy() ? "artlyrics.spotify.loading" : "artlyrics.spotify.no_playlists"), UI, cx + 8, y + 2, Theme.MUTED, false);
				y += 14;
			}
			for (SpotifyAccount.Playlist p : lists) {
				int top = y;
				float h = hover("pl:" + p.id(), insideContent(cx, top, cx + cw, top + 16));
				Draw.roundRect(c, cx, top, cx + cw, top + 16, 4, Draw.lerpArgb(Theme.CARD, Theme.CARD_HOVER, h));
				c.icon("plus", cx + 6, top + 3, Theme.MUTED);
				c.text(Draw.ellipsize(c, p.name(), UI, cw - 30), UI, cx + 22, top + 4, Theme.TEXT, false);
				contentHit(cx, top, cx + cw, top + 16, () -> {
					spotify.addToPlaylist(p);
					playlistsOpen = false;
				});
				y += 18;
			}
		}
		return y + 2;
	}

	private boolean playlistsOpen;

	private void iconButton(Canvas c, String id, int x, int y, int w, int h, String icon, boolean strong, Runnable action) {
		float hv = hover("ib:" + id, insideContent(x, y, x + w, y + h));
		int bg = strong ? Draw.lerpArgb(Draw.withAlpha(accent, 0.35f), Draw.withAlpha(accent, 0.55f), hv)
				: Draw.lerpArgb(Theme.CONTROL, Theme.CONTROL_HOVER, hv);
		Draw.roundRect(c, x, y, x + w, y + h, 6, bg);
		c.icon(icon, x + (w - 10) / 2, y + (h - 10) / 2, Theme.TEXT);
		contentHit(x, y, x + w, y + h, action);
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
			Draw.roundRect(c, cx, y, cx + cw, y + rowH - 2, 4, Draw.withAlpha(Theme.HIGHLIGHT, 0.04f * h));
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

	/** A row with a value on the right and a small button next to it. */
	private int rowValueButton(Canvas c, String id, String labelKey, int y, String value, String buttonKey, Runnable action) {
		int top = y;
		int bw = 48;
		y = rowFrame(c, id, labelKey, y, bw + 12 + c.textWidth(value, UI));
		int x1 = cx + cw - 8;
		int x0 = x1 - bw;
		int by = rowMid(top) - 7;
		float h = hover("btn:" + id, insideContent(x0, by, x1, by + 14));
		Draw.roundRect(c, x0, by, x1, by + 14, 4, Draw.lerpArgb(Draw.withAlpha(accent, 0.25f), Draw.withAlpha(accent, 0.45f), h));
		String label = host.tr(buttonKey);
		c.text(label, UI, x0 + (bw - c.textWidth(label, UI)) / 2, by + 3, Theme.TEXT, false);
		c.text(value, UI, x0 - 8 - c.textWidth(value, UI), rowMid(top) - 4, Theme.MUTED, false);
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
		if (layoutMode) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit hit = hits.get(i);
				if (hit.contains(x, y) && hit.click != null) {
					hit.click.run();
					return true;
				}
			}
			if (x >= hoverBox[0] && x < hoverBox[0] + hoverBox[2] && y >= hoverBox[1] && y < hoverBox[1] + hoverBox[3]) {
				layoutDrag = 2;
				dragDx = x - (hoverBox[0] + hoverBox[2] / 2.0);
				dragDy = y - (hoverBox[1] + hoverBox[3] / 2.0);
			} else if (cardBox[2] > 0 && x >= cardBox[0] && x < cardBox[0] + cardBox[2] && y >= cardBox[1] && y < cardBox[1] + cardBox[3]) {
				layoutDrag = 1;
				dragDx = x - cardBox[0];
				dragDy = y - cardBox[1];
			}
			return true;
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
		if (layoutMode) {
			layoutDrag(x, y);
			return;
		}
		if (dragging != null) {
			applyDrag(x);
		}
	}

	public void mouseReleased() {
		dragging = null;
		if (layoutDrag != 0) {
			layoutDrag = 0;
			host.settingsChanged();
		}
	}

	private void applyDrag(double x) {
		if (dragging == null || dragging.drag == null) {
			return;
		}
		float t = (float) ((x - dragging.dragX0) / Math.max(1, dragging.dragX1 - dragging.dragX0));
		dragging.drag.set(Easing.clamp01(t));
	}

	public boolean mouseScrolled(double x, double y, double amount) {
		if (layoutMode) {
			ArtlyricsConfig config = host.config();
			if (x >= hoverBox[0] && x < hoverBox[0] + hoverBox[2] && y >= hoverBox[1] && y < hoverBox[1] + hoverBox[3]) {
				config.size = Math.max(0.5f, Math.min(2.5f, Math.round((config.size + (float) amount * 0.05f) * 20f) / 20f));
			} else if (cardBox[2] > 0 && x >= cardBox[0] && x < cardBox[0] + cardBox[2] && y >= cardBox[1] && y < cardBox[1] + cardBox[3]) {
				config.cardScale = Math.max(0.6f, Math.min(1.6f, Math.round((config.cardScale + (float) amount * 0.05f) * 20f) / 20f));
			}
			host.settingsChanged();
			return true;
		}
		if (x >= cx - 4 && x < cx + cw + 10 && y >= contentTop && y < contentBottom) {
			scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) amount * 26f));
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Sample data for the card preview when nothing is playing.

	private static final Lyrics SAMPLE_LYRICS = LrcParser.parse("[00:00.00]Your lyrics will glow right here\n[00:30.00]x", 60_000, "sample");

	private static CoverImage sampleCover;
	private static boolean sampleCoverLoaded;

	/** The demo song's animated cover, for the card preview. */
	static synchronized CoverImage sampleCover() {
		if (!sampleCoverLoaded) {
			sampleCoverLoaded = true;
			try (java.io.InputStream in = SettingsUi.class.getResourceAsStream("/artlyrics/demo-cover.gif")) {
				sampleCover = in == null ? null : com.artlyrics.cover.CoverDecoder.decode(in.readAllBytes(), "sample");
			} catch (java.io.IOException e) {
				sampleCover = null;
			}
		}
		return sampleCover;
	}

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
			public long lyricsTimeMs() {
				return 1_400;
			}

			@Override
			public int songOffsetMs() {
				return 0;
			}

			@Override
			public void resetSongOffset() {
			}

			@Override
			public void openLyricsFolder() {
			}

			@Override
			public String lyricsDetail() {
				return "";
			}

			@Override
			public CoverImage cover() {
				return sampleCover();
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
