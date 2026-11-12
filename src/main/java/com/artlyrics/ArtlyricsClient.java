package com.artlyrics;

import com.artlyrics.compat.Mc;
import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.TimingOffsets;
import com.artlyrics.lyrics.LocalLyrics;
import com.artlyrics.lyrics.LrclibClient;
import com.artlyrics.lyrics.LyricsFinder;
import com.artlyrics.lyrics.LyricsManager;
import com.artlyrics.lyrics.NeteaseClient;
import com.artlyrics.nowplaying.MediaSource;
import com.artlyrics.nowplaying.MediaSources;
import com.artlyrics.nowplaying.PlaybackTracker;
import com.artlyrics.render.SkyLyricsRenderer;
import com.artlyrics.render.SongCardHud;
import com.artlyrics.screen.ArtlyricsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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

	private static final KeyMapping.Category CATEGORY = Mc.keyCategory("main");
	private static KeyMapping openMenu;
	private static KeyMapping toggleLyrics;
	private static KeyMapping toggleMode;
	private static KeyMapping earlier;
	private static KeyMapping later;
	/** How much one press of the "earlier / later" keys moves this song's lyrics. */
	private static final int NUDGE_MS = 250;

	@Override
	public void onInitializeClient() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		ArtlyricsConfig config = ArtlyricsConfig.load(configDir.resolve("artlyrics.json"));

		String version = FabricLoader.getInstance().getModContainer(Artlyrics.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
		Path dataDir = configDir.resolve(Artlyrics.MOD_ID);
		String userAgent = "Artlyrics/" + version + " (Minecraft Fabric mod)";
		LocalLyrics folder = new LocalLyrics(dataDir.resolve("lyrics"));
		folder.createFolder();
		LyricsFinder finder = new LyricsFinder(folder, new LrclibClient(userAgent),
				new NeteaseClient("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"),
				() -> new LyricsFinder.Settings(config.sourceFolder, config.sourceLrclib, config.sourceNetease, config.estimateUnsynced));

		MediaSource source = MediaSources.forThisComputer(dataDir);
		source.setPreferredPlayer(config.preferredPlayer);
		PlaybackTracker tracker = new PlaybackTracker(source);
		LyricsManager lyrics = new LyricsManager(finder);
		Artlyrics mod = new Artlyrics(config, tracker, lyrics, new TimingOffsets(dataDir.resolve("song-timing.json")), version);

		if (Boolean.getBoolean("artlyrics.demo")) {
			tracker.setDemoActive(true); // handy for development and screenshots
		}

		SkyLyricsRenderer renderer = new SkyLyricsRenderer();
		Mc.onWorldRender(renderer::render);
		Mc.addHudElementBeforeChat("song_card", new SongCardHud());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> renderer.reset());

		openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.artlyrics.menu", GLFW.GLFW_KEY_K, CATEGORY));
		toggleLyrics = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.artlyrics.toggle", InputConstants.UNKNOWN.getValue(), CATEGORY));
		toggleMode = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.artlyrics.mode", InputConstants.UNKNOWN.getValue(), CATEGORY));
		earlier = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.artlyrics.earlier", GLFW.GLFW_KEY_LEFT_BRACKET, CATEGORY));
		later = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.artlyrics.later", GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY));

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
			config.displayMode = config.displayMode == ArtlyricsConfig.DisplayMode.SKYWRITING
					? ArtlyricsConfig.DisplayMode.HOVER
					: ArtlyricsConfig.DisplayMode.SKYWRITING;
			mod.settingsChanged();
			config.save();
			client.gui.setOverlayMessage(Component.translatable(config.displayMode.translationKey()), false);
		}
		int nudge = 0;
		while (earlier.consumeClick()) {
			nudge += NUDGE_MS;
		}
		while (later.consumeClick()) {
			nudge -= NUDGE_MS;
		}
		if (nudge != 0) {
			String key = mod.lyrics().trackKey();
			if (key == null) {
				client.gui.setOverlayMessage(Component.translatable("artlyrics.toast.no_song"), false);
			} else {
				int value = mod.songTiming().adjust(key, nudge);
				String shown = (value > 0 ? "+" : "") + String.format(java.util.Locale.ROOT, "%.2f", value / 1000f) + " s";
				client.gui.setOverlayMessage(Component.translatable("artlyrics.toast.song_timing", shown), false);
			}
		}
	}
}
