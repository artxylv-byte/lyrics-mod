package com.artlyrics.gametest;

import com.artlyrics.Artlyrics;
import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.Look;
import com.artlyrics.config.LyricStyle;
import com.artlyrics.render.Fonts;
import com.artlyrics.screen.ArtlyricsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Opens a world at sunset, plays the built-in demo song frozen at fixed moments and screenshots each look,
 * display mode and settings page. Screenshots land in {@code build/run/clientGameTest/screenshots}.
 */
public class ArtlyricsScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Artlyrics-GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		checkFontsLoaded(context);
		if (System.getenv("ARTLYRICS_GAMETEST_QUICK") != null) {
			return; // fonts only, skip the slow world part
		}
		context.runOnClient(client -> client.options.renderDistance().set(5));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			try {
				// Generous timeout: CI machines and software rendering can be slow to mesh chunks.
				world.getClientWorld().waitForChunksRender(20 * 60 * 5);
			} catch (AssertionError e) {
				LOGGER.warn("Chunks still rendering after 5 minutes; taking screenshots anyway");
			}
			world.getServer().runCommand("time set 12600");
			world.getServer().runCommand("weather clear");

			shot(context, "01-starlight", 4200, -14f, c -> c.apply(Look.STARLIGHT));
			shot(context, "02-starlight-mid-line", 2300, -14f, c -> c.apply(Look.STARLIGHT));
			shot(context, "03-petal-rise", 9300, -14f, c -> c.apply(Look.PETAL));
			shot(context, "04-neon-bloom-arc", 13_200, -14f, c -> {
				c.apply(Look.NEON);
				c.arc = true;
			});
			shot(context, "05-notebook-typewriter", 17_400, -14f, c -> c.apply(Look.NOTEBOOK));
			shot(context, "06-hover-sweep", 21_000, 4f, c -> {
				c.apply(Look.CAMPFIRE);
				c.displayMode = ArtlyricsConfig.DisplayMode.HOVER;
				c.animation = ArtlyricsConfig.Animation.SWEEP;
			});
			shot(context, "07-spotlight-ballroom", 24_600, -14f, c -> {
				c.apply(Look.BALLROOM);
				c.animation = ArtlyricsConfig.Animation.SPOTLIGHT;
			});

			world.getServer().runCommand("time set 18000");
			shot(context, "08-night-custom-colors-card-top-right", 28_800, -14f, c -> {
				c.apply(Look.TIDE);
				c.style = LyricStyle.CUSTOM;
				c.cardCorner = ArtlyricsConfig.CardCorner.TOP_RIGHT;
			});

			context.setScreen(() -> new ArtlyricsScreen(null));
			for (int i = 0; i < 6; i++) {
				int page = i;
				context.runOnClient(client -> {
					if (client.screen instanceof ArtlyricsScreen screen) {
						screen.showPage(page);
					}
				});
				context.waitTicks(10);
				context.takeScreenshot("09-settings-page-" + (page + 1));
			}
			context.setScreen(() -> null);
		}
	}

	/** Every bundled font must have loaded: text in it measures differently from Minecraft's default font. */
	private static void checkFontsLoaded(ClientGameTestContext context) {
		// Wait for resources to finish loading: until then every glyph is the 6px "missing" box ("iiii" = 24).
		context.waitFor(client -> client.getOverlay() == null && client.font.width("iiii") < 24, 20 * 120);
		context.runOnClient(client -> {
			String sample = "The quick brown fox jumps over the lazy dog";
			int vanilla = client.font.width(sample);
			for (ArtlyricsConfig.TextFont font : ArtlyricsConfig.TextFont.values()) {
				if (font.bundled == null) {
					continue;
				}
				int world = client.font.width(Component.literal(sample).withStyle(Style.EMPTY.withFont(Fonts.world(font))));
				int ui = client.font.width(Component.literal(sample).withStyle(Fonts.uiStyle(font)));
				LOGGER.info("Font {}: world width {}, menu width {}, Minecraft font {}", font, world, ui, vanilla);
				if (world == vanilla || ui == vanilla) {
					throw new AssertionError("Bundled font " + font + " didn't load (same width as the default font)");
				}
			}
		});
	}

	private static void shot(ClientGameTestContext context, String name, long songMs, float pitch, Consumer<ArtlyricsConfig> setup) {
		context.runOnClient(client -> {
			Artlyrics mod = Artlyrics.get();
			ArtlyricsConfig c = mod.config();
			ArtlyricsConfig defaults = new ArtlyricsConfig();
			c.enabled = true;
			c.displayMode = defaults.displayMode;
			c.cardCorner = defaults.cardCorner;
			c.arc = defaults.arc;
			setup.accept(c);
			mod.settingsChanged();

			client.player.setYRot(0f);
			client.player.yRotO = 0f;
			client.player.setXRot(pitch);
			client.player.xRotO = pitch;
			mod.tracker().seekDemo(songMs, true);
		});
		// Let the previous line leave and the new one finish animating in.
		context.waitTicks(30);
		context.takeScreenshot(name);
	}
}
