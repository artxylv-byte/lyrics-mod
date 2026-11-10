package com.artlyrics.gametest;

import com.artlyrics.Artlyrics;
import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.LyricStyle;
import com.artlyrics.screen.ArtlyricsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Opens a world at sunset, plays the built-in demo song frozen at fixed moments and screenshots each
 * display mode and style. Screenshots land in {@code build/run/clientGameTest/screenshots}.
 */
public class ArtlyricsScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Artlyrics-GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
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

			shot(context, "01-skywriting-bloom", 4200, -14f, c -> { });
			shot(context, "02-skywriting-mid-line", 2300, -14f, c -> { });
			shot(context, "03-typewriter-ember-outline", 9300, -14f, c -> {
				c.animation = ArtlyricsConfig.Animation.TYPEWRITER;
				c.style = LyricStyle.EMBER;
				c.textEffect = ArtlyricsConfig.TextEffect.OUTLINE;
			});
			shot(context, "04-hover-sweep-cherry", 14_600, 4f, c -> {
				c.displayMode = ArtlyricsConfig.DisplayMode.HOVER;
				c.animation = ArtlyricsConfig.Animation.SWEEP;
				c.style = LyricStyle.CHERRY;
			});
			shot(context, "05-spotlight-honey", 17_700, -14f, c -> {
				c.animation = ArtlyricsConfig.Animation.SPOTLIGHT;
				c.style = LyricStyle.HONEY;
			});
			shot(context, "06-smooth-font-aurora-arc", 23_400, -14f, c -> {
				c.font = ArtlyricsConfig.TextFont.SMOOTH;
				c.style = LyricStyle.AURORA;
				c.arc = true;
			});

			world.getServer().runCommand("time set 18000");
			shot(context, "07-night-prismarine-card-top-right", 30_500, -14f, c -> {
				c.style = LyricStyle.PRISMARINE;
				c.cardCorner = ArtlyricsConfig.CardCorner.TOP_RIGHT;
			});

			context.setScreen(() -> new ArtlyricsScreen(null));
			context.waitTicks(10);
			context.takeScreenshot("08-settings-screen");
			context.setScreen(() -> null);
		}
	}

	private static void shot(ClientGameTestContext context, String name, long songMs, float pitch, Consumer<ArtlyricsConfig> setup) {
		context.runOnClient(client -> {
			Artlyrics mod = Artlyrics.get();
			ArtlyricsConfig c = mod.config();
			c.enabled = true;
			c.displayMode = ArtlyricsConfig.DisplayMode.SKYWRITING;
			c.animation = ArtlyricsConfig.Animation.BLOOM;
			c.style = LyricStyle.AMETHYST;
			c.font = ArtlyricsConfig.TextFont.DEFAULT;
			c.textEffect = ArtlyricsConfig.TextEffect.SHADOW;
			c.cardCorner = ArtlyricsConfig.CardCorner.TOP_LEFT;
			c.arc = false;
			setup.accept(c);
			mod.settingsChanged();

			client.player.setYRot(0f);
			client.player.yRotO = 0f;
			client.player.setXRot(pitch);
			client.player.xRotO = pitch;
			mod.tracker().seekDemo(songMs, true);
		});
		// Let the previous line fade out and the new one finish animating in.
		context.waitTicks(30);
		context.takeScreenshot(name);
	}
}
