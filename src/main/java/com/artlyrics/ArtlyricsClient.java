package com.artlyrics;

import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.lyrics.LrclibClient;
import com.artlyrics.lyrics.LyricsManager;
import com.artlyrics.nowplaying.MediaSource;
import com.artlyrics.nowplaying.MediaSources;
import com.artlyrics.nowplaying.PlaybackTracker;
import com.artlyrics.render.SkyLyricsRenderer;
import com.artlyrics.render.TopBarHud;
import com.artlyrics.screen.ArtlyricsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class ArtlyricsClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("Artlyrics");

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Artlyrics.id("main"));
	private static KeyMapping openMenu;
	private static KeyMapping toggleLyrics;
	private static KeyMapping toggleMode;

	@Override
	public void onInitializeClient() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		ArtlyricsConfig config = ArtlyricsConfig.load(configDir.resolve("artlyrics.json"));

		String version = FabricLoader.getInstance().getModContainer(Artlyrics.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
		LrclibClient lrclib = new LrclibClient("Artlyrics/" + version + " (Minecraft Fabric mod)");

		MediaSource source = MediaSources.forThisComputer(configDir.resolve(Artlyrics.MOD_ID));
		source.setPreferredPlayer(config.preferredPlayer);
		PlaybackTracker tracker = new PlaybackTracker(source);
		LyricsManager lyrics = new LyricsManager(lrclib);
		Artlyrics mod = new Artlyrics(config, tracker, lyrics);

		if (Boolean.getBoolean("artlyrics.demo")) {
			tracker.setDemoActive(true); // handy for development and screenshots
		}

		SkyLyricsRenderer renderer = new SkyLyricsRenderer();
		WorldRenderEvents.AFTER_ENTITIES.register(renderer::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Artlyrics.id("top_bar"), new TopBarHud());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> renderer.reset());

		openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.artlyrics.menu", GLFW.GLFW_KEY_K, CATEGORY));
		toggleLyrics = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.artlyrics.toggle", InputConstants.UNKNOWN.getValue(), CATEGORY));
		toggleMode = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.artlyrics.mode", InputConstants.UNKNOWN.getValue(), CATEGORY));

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> tracker.start());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			tracker.close();
			lyrics.close();
			config.save();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			long now = System.nanoTime();
			tracker.frame(now);
			lyrics.update(tracker.track(), now);
			handleKeys(client, mod);
		});

		LOGGER.info("[Artlyrics] Ready ({}).", version);
	}

	private static void handleKeys(Minecraft client, Artlyrics mod) {
		while (openMenu.consumeClick()) {
			client.setScreen(new ArtlyricsScreen(client.screen));
		}
		while (toggleLyrics.consumeClick()) {
			ArtlyricsConfig config = mod.config();
			config.enabled = !config.enabled;
			mod.settingsChanged();
			config.save();
			client.gui.setOverlayMessage(Component.translatable(config.enabled ? "artlyrics.toast.on" : "artlyrics.toast.off"), false);
		}
		while (toggleMode.consumeClick()) {
			ArtlyricsConfig config = mod.config();
			config.displayMode = config.displayMode == ArtlyricsConfig.DisplayMode.SKY
					? ArtlyricsConfig.DisplayMode.FRONT
					: ArtlyricsConfig.DisplayMode.SKY;
			mod.settingsChanged();
			config.save();
			client.gui.setOverlayMessage(Component.translatable(config.displayMode.translationKey()), false);
		}
	}
}
