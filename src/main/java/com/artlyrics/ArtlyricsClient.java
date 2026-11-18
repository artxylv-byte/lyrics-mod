package com.artlyrics;

import com.artlyrics.compat.Mc;
import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.TimingOffsets;
import com.artlyrics.cover.CoverArt;
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
import net.fabricmc.loader.api.metadata.ModOrigin;
import com.artlyrics.update.Updater;
import com.artlyrics.nowplaying.MediaCommand;
import com.artlyrics.spotify.SpotifyAccount;
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
	private static KeyMapping playPause;
	private static KeyMapping nextSong;
	private static KeyMapping previousSong;
	private static KeyMapping likeSong;
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
		CoverArt covers = new CoverArt(dataDir.resolve("covers"), userAgent,
				() -> new CoverArt.Settings(config.songCard && config.cardCover, config.coverOnline));
		covers.createFolder();
		Artlyrics mod = new Artlyrics(config, tracker, lyrics, new TimingOffsets(dataDir.resolve("song-timing.json")), covers, version);

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
		playPause = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.artlyrics.play_pause", InputConstants.UNKNOWN.getValue(), CATEGORY));
		nextSong = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.artlyrics.next", InputConstants.UNKNOWN.getValue(), CATEGORY));
		previousSong = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.artlyrics.previous", InputConstants.UNKNOWN.getValue(), CATEGORY));
		likeSong = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.artlyrics.like", InputConstants.UNKNOWN.getValue(), CATEGORY));

		Updater updater = new Updater(version, installedJar(), config.updateRepository, config.updateBranches, userAgent);
		updater.resumePending();
		mod.setUpdater(updater);
		SpotifyAccount spotify = new SpotifyAccount(dataDir.resolve("spotify.json"), Mc::openUrl);
		if (!config.spotifyClientId.isBlank()) {
			spotify.setClientId(config.spotifyClientId);
		}
		mod.setSpotify(spotify);

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			tracker.start();
			if (config.updates != ArtlyricsConfig.UpdateMode.OFF) {
				updater.check(config.updates == ArtlyricsConfig.UpdateMode.AUTOMATIC);
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			tracker.close();
			lyrics.close();
			covers.close();
			config.save();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			long now = System.nanoTime();
			tracker.frame(now);
			lyrics.update(tracker.track(), now);
			covers.update(tracker.track(), now);
			mod.updateHoliday();
			tellAboutUpdate(client, mod);
			mod.songTick();
			handleKeys(client, mod);
		});

		LOGGER.info("[Artlyrics] Ready ({}).", version);
	}

	private static void mediaKey(Minecraft client, Artlyrics mod, KeyMapping key, MediaCommand command, String toast) {
		while (key.consumeClick()) {
			if (!mod.config().mediaControls) {
				client.gui.setOverlayMessage(Component.translatable("artlyrics.toast.controls_off"), false);
			} else if (mod.tracker().send(command)) {
				client.gui.setOverlayMessage(Component.translatable(toast), false);
			}
		}
	}

	private static Updater.State toldState;

	/** Once per news, in chat when playing: a new version is ready, or available to install from the menu. */
	private static void tellAboutUpdate(Minecraft client, Artlyrics mod) {
		Updater u = mod.updater();
		if (u == null || client.player == null || mod.config().updates == ArtlyricsConfig.UpdateMode.OFF) {
			return;
		}
		Updater.State state = u.state();
		if (state == toldState || (state != Updater.State.READY && state != Updater.State.AVAILABLE) || u.latest() == null) {
			return;
		}
		toldState = state;
		String key = state == Updater.State.READY ? "artlyrics.chat.update_ready" : "artlyrics.chat.update_available";
		client.gui.getChat().addMessage(Component.translatable(key, u.latest().version()));
	}

	/** The jar file this mod was loaded from, or null when running from a development build. */
	private static Path installedJar() {
		return FabricLoader.getInstance().getModContainer(Artlyrics.MOD_ID).flatMap(c -> {
			ModOrigin origin = c.getOrigin();
			if (origin.getKind() != ModOrigin.Kind.PATH) {
				return java.util.Optional.empty();
			}
			return origin.getPaths().stream()
					.filter(p -> p.getFileName() != null && p.getFileName().toString().endsWith(".jar") && java.nio.file.Files.isRegularFile(p))
					.findFirst();
		}).orElse(null);
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
		mediaKey(client, mod, playPause, MediaCommand.PLAY_PAUSE, "artlyrics.toast.play_pause");
		mediaKey(client, mod, nextSong, MediaCommand.NEXT, "artlyrics.toast.next");
		mediaKey(client, mod, previousSong, MediaCommand.PREVIOUS, "artlyrics.toast.previous");
		while (likeSong.consumeClick()) {
			SpotifyAccount spotify = mod.spotify();
			if (!mod.config().mediaControls) {
				client.gui.setOverlayMessage(Component.translatable("artlyrics.toast.controls_off"), false);
			} else if (spotify == null || !spotify.connected()) {
				client.gui.setOverlayMessage(Component.translatable("artlyrics.toast.connect_spotify"), false);
			} else {
				spotify.toggleLike();
			}
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
