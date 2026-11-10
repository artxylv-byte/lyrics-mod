package com.artlyrics;

import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.lyrics.Lyrics;
import com.artlyrics.lyrics.LyricsManager;
import com.artlyrics.nowplaying.NowPlaying;
import com.artlyrics.nowplaying.PlaybackTracker;
import com.artlyrics.ui.UiHost;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Holds the mod's long-lived pieces. Created once by {@link ArtlyricsClient}. */
public final class Artlyrics {
	public static final String MOD_ID = "artlyrics";

	private static Artlyrics instance;

	private final ArtlyricsConfig config;
	private final PlaybackTracker tracker;
	private final LyricsManager lyrics;
	private final String version;
	/** Bumped whenever settings change, so cached text layouts get rebuilt. */
	private int settingsRevision;

	Artlyrics(ArtlyricsConfig config, PlaybackTracker tracker, LyricsManager lyrics, String version) {
		this.config = config;
		this.tracker = tracker;
		this.lyrics = lyrics;
		this.version = version;
		instance = this;
	}

	public static Artlyrics get() {
		return instance;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public ArtlyricsConfig config() {
		return config;
	}

	public PlaybackTracker tracker() {
		return tracker;
	}

	public LyricsManager lyrics() {
		return lyrics;
	}

	public int settingsRevision() {
		return settingsRevision;
	}

	/** Applies changed settings right away. Call {@code config().save()} to persist them. */
	public void settingsChanged() {
		settingsRevision++;
		tracker.system().setPreferredPlayer(config.preferredPlayer);
	}

	/** The view of the mod that the settings screen and song card draw from. */
	public UiHost uiHost(Runnable close) {
		Artlyrics mod = this;
		return new UiHost() {
			@Override
			public ArtlyricsConfig config() {
				return config;
			}

			@Override
			public void settingsChanged() {
				mod.settingsChanged();
			}

			@Override
			public @Nullable NowPlaying track() {
				return tracker.track();
			}

			@Override
			public long positionMs() {
				return tracker.positionMs();
			}

			@Override
			public @Nullable Lyrics lyrics() {
				return lyrics.lyrics();
			}

			@Override
			public LyricsManager.State lyricsState() {
				return lyrics.state();
			}

			@Override
			public @Nullable String problem() {
				return tracker.problem();
			}

			@Override
			public boolean demoActive() {
				return tracker.isDemoActive();
			}

			@Override
			public void setDemoActive(boolean active) {
				tracker.setDemoActive(active);
			}

			@Override
			public void refreshLyrics() {
				lyrics.refresh();
			}

			@Override
			public String version() {
				return version;
			}

			@Override
			public String tr(String key) {
				return I18n.exists(key) ? I18n.get(key) : key;
			}

			@Override
			public void close() {
				close.run();
			}
		};
	}
}
