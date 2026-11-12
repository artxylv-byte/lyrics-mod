package com.artlyrics;

import com.artlyrics.compat.Mc;
import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.TimingOffsets;
import com.artlyrics.lyrics.LocalLyrics;
import com.artlyrics.lyrics.Lyrics;
import com.artlyrics.lyrics.LyricsManager;
import com.artlyrics.nowplaying.NowPlaying;
import com.artlyrics.nowplaying.PlaybackTracker;
import com.artlyrics.ui.UiHost;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

/** Holds the mod's long-lived pieces. Created once by {@link ArtlyricsClient}. */
public final class Artlyrics {
	public static final String MOD_ID = "artlyrics";

	private static Artlyrics instance;

	private final ArtlyricsConfig config;
	private final PlaybackTracker tracker;
	private final LyricsManager lyrics;
	private final TimingOffsets songTiming;
	private final String version;
	/** Bumped whenever settings change, so cached text layouts get rebuilt. */
	private int settingsRevision;

	Artlyrics(ArtlyricsConfig config, PlaybackTracker tracker, LyricsManager lyrics, TimingOffsets songTiming, String version) {
		this.config = config;
		this.tracker = tracker;
		this.lyrics = lyrics;
		this.songTiming = songTiming;
		this.version = version;
		instance = this;
	}

	public static Artlyrics get() {
		return instance;
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

	public TimingOffsets songTiming() {
		return songTiming;
	}

	/** This song's own timing correction (from the "earlier / later" keys), in milliseconds. */
	public int songOffsetMs() {
		return songTiming.get(lyrics.trackKey());
	}

	/**
	 * The song time lyrics are shown for: the smoothed playback position plus the general timing setting and this
	 * song's own correction.
	 */
	public long lyricsTimeMs() {
		return tracker.positionMs() + config.offsetMs + songOffsetMs();
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
			public long lyricsTimeMs() {
				return mod.lyricsTimeMs();
			}

			@Override
			public int songOffsetMs() {
				return mod.songOffsetMs();
			}

			@Override
			public void resetSongOffset() {
				String key = lyrics.trackKey();
				if (key != null) {
					songTiming.set(key, 0);
				}
			}

			@Override
			public void openLyricsFolder() {
				LocalLyrics folder = lyrics.finder().folder();
				if (folder != null) {
					folder.createFolder();
					Mc.openFolder(folder.folder());
				}
			}

			@Override
			public String lyricsDetail() {
				return lyrics.detail();
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
