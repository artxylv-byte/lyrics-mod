package com.artlyrics.ui;

import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.Look;
import com.artlyrics.config.LyricStyle;
import com.artlyrics.lyrics.LrcParser;
import com.artlyrics.lyrics.Lyrics;
import com.artlyrics.lyrics.LyricsManager;
import com.artlyrics.nowplaying.NowPlaying;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Renders every settings page and the song card to PNG files with {@link JavaCanvas}, so the UI can be looked at
 * without starting Minecraft. Run with {@code ./gradlew test -Dartlyrics.uiPreview=true}; images go to
 * {@code build/ui-preview}.
 */
@EnabledIfSystemProperty(named = "artlyrics.uiPreview", matches = "true")
class SettingsUiPreviewTest {
	private static final Path OUT = Path.of("build", "ui-preview");
	private static final int W = 480;
	private static final int H = 270;
	private static final int SCALE = 3;

	@Test
	void renderPages() throws Exception {
		for (SettingsUi.Page page : SettingsUi.Page.values()) {
			FakeHost host = new FakeHost();
			render(host, page, 0, "page-" + (page.ordinal() + 1) + "-" + page.name().toLowerCase(), W, H);
		}
		// The Look page scrolled down, with the custom palette editor open.
		FakeHost custom = new FakeHost();
		custom.config.style = LyricStyle.CUSTOM;
		render(custom, SettingsUi.Page.LOOK, 260, "page-3-look-scrolled", W, H);
		render(custom, SettingsUi.Page.LOOK, 600, "page-3-look-bottom", W, H);
		// Small screens (GUI scale 4 on a 1366x768 display) get the compact sidebar.
		render(new FakeHost(), SettingsUi.Page.NOW_PLAYING, 0, "compact-now-playing", 341, 192);
		// Nothing playing.
		FakeHost idle = new FakeHost();
		idle.track = null;
		idle.problem = "Install 'playerctl' so Artlyrics can see what's playing";
		render(idle, SettingsUi.Page.NOW_PLAYING, 0, "idle-now-playing", W, H);
	}

	@Test
	void renderSongCards() throws Exception {
		for (Look look : new Look[] {Look.STARLIGHT, Look.CAMPFIRE, Look.NOTEBOOK}) {
			FakeHost host = new FakeHost();
			host.config.apply(look);
			JavaCanvas canvas = new JavaCanvas(W, H, SCALE);
			canvas.paintBackdrop();
			SongCardView view = new SongCardView();
			long t = 1_000_000_000L;
			// Let the fade-in finish on scratch canvases, then draw the final frame.
			for (int i = 0; i < 30; i++) {
				view.drawHud(i == 29 ? canvas : new JavaCanvas(W, H, 1), host, t + i * 50_000_000L);
			}
			canvas.save(OUT.resolve("card-" + look.name().toLowerCase() + ".png"));
		}
	}

	@Test
	void clickingNavigationAndControlsWorks() {
		FakeHost host = new FakeHost();
		SettingsUi ui = new SettingsUi(host);
		JavaCanvas canvas = new JavaCanvas(W, H, 1);
		// Past the opening animation, so the panel is in its final place.
		ui.render(canvas, -1, -1, 1_000_000_000L);
		ui.render(canvas, -1, -1, 1_500_000_000L);
		// Click the "Look" item in the sidebar (third entry).
		int panelX = (W - Math.min(470, W - 16)) / 2;
		int panelY = (H - Math.min(280, H - 16)) / 2;
		ui.mouseClicked(panelX + 30, panelY + 40 + 2 * 20 + 5, 0);
		assertEquals(SettingsUi.Page.LOOK, ui.page());
		// Click the master switch: lyrics turn off.
		ui.render(canvas, -1, -1, 2_000_000_000L);
		int switchX = panelX + Math.min(470, W - 16) - 26 - 11;
		ui.mouseClicked(switchX, panelY + 10 + 6, 0);
		assertEquals(false, host.config.enabled);
	}

	private static void render(FakeHost host, SettingsUi.Page page, int scroll, String name, int w, int h) throws Exception {
		JavaCanvas canvas = new JavaCanvas(w, h, SCALE);
		canvas.paintBackdrop();
		SettingsUi ui = new SettingsUi(host);
		ui.setPage(page);
		long t = 1_000_000_000L;
		// Let open, page and scroll animations settle.
		for (int i = 0; i < 40; i++) {
			if (i == 1 && scroll > 0) {
				int panelX = (w - Math.min(470, w - 16)) / 2;
				ui.mouseScrolled(panelX + 200, h / 2.0, -scroll / 26.0);
			}
			JavaCanvas frame = i == 39 ? canvas : new JavaCanvas(w, h, 1);
			ui.render(frame, -1, -1, t + i * 50_000_000L);
		}
		canvas.save(OUT.resolve(name + ".png"));
	}

	/** A pretend mod: plays the demo lyrics at a fixed moment, translations from en_us.json. */
	static final class FakeHost implements UiHost {
		final ArtlyricsConfig config = new ArtlyricsConfig();
		@Nullable NowPlaying track = new NowPlaying("Spotify", "Written in the Sky", "Artlyrics Demo", "", 32_000, 2_600, true, 0);
		@Nullable String problem;
		boolean demo;
		final Lyrics lyrics;
		private final Map<String, String> lang = new HashMap<>();

		FakeHost() {
			try (InputStream in = FakeHost.class.getResourceAsStream("/artlyrics/demo.lrc")) {
				lyrics = LrcParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), 32_000, "demo");
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
			try (InputStream in = FakeHost.class.getResourceAsStream("/assets/artlyrics/lang/en_us.json")) {
				JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				for (Map.Entry<String, JsonElement> e : o.entrySet()) {
					lang.put(e.getKey(), e.getValue().getAsString());
				}
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public ArtlyricsConfig config() {
			return config;
		}

		@Override
		public void settingsChanged() {
		}

		@Override
		public @Nullable NowPlaying track() {
			return track;
		}

		@Override
		public long positionMs() {
			return 2_600;
		}

		@Override
		public @Nullable Lyrics lyrics() {
			return track == null ? null : lyrics;
		}

		@Override
		public LyricsManager.State lyricsState() {
			return track == null ? LyricsManager.State.IDLE : LyricsManager.State.FOUND;
		}

		@Override
		public @Nullable String problem() {
			return problem;
		}

		@Override
		public boolean demoActive() {
			return demo;
		}

		@Override
		public void setDemoActive(boolean active) {
			demo = active;
		}

		@Override
		public void refreshLyrics() {
		}

		@Override
		public String version() {
			return "1.1.0";
		}

		@Override
		public String tr(String key) {
			return lang.getOrDefault(key, key);
		}

		@Override
		public void close() {
		}
	}
}
