package com.artlyrics;

import com.artlyrics.cover.CoverArt;
import com.artlyrics.cover.CoverImage;
import com.artlyrics.holiday.HolidayFinder;
import com.artlyrics.holiday.HolidayTheme;
import com.artlyrics.holiday.Region;
import com.artlyrics.nowplaying.MediaCommand;
import com.artlyrics.spotify.SpotifyAccount;
import com.artlyrics.update.Updater;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import com.artlyrics.compat.Mc;
import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.TimingOffsets;
import com.artlyrics.lyrics.LocalLyrics;
import com.artlyrics.lyrics.Lyrics;
import com.artlyrics.lyrics.LyricsManager;
import com.artlyrics.nowplaying.NowPlaying;
import com.artlyrics.nowplaying.PlaybackTracker;
import com.artlyrics.ui.UiHost;
import net.minecraft.locale.Language;
import org.jetbrains.annotations.Nullable;

/** Holds the mod's long-lived pieces. Created once by {@link ArtlyricsClient}. */
public final class Artlyrics {
	public static final String MOD_ID = "artlyrics";

	private static Artlyrics instance;

	private final ArtlyricsConfig config;
	private final PlaybackTracker tracker;
	private final LyricsManager lyrics;
	private final TimingOffsets songTiming;
	private final CoverArt covers;
	private Updater updater;
	private SpotifyAccount spotify;
	private String lastSongKey;
	private final String version;
	/** Bumped whenever settings change, so cached text layouts get rebuilt. */
	private int settingsRevision;

	Artlyrics(ArtlyricsConfig config, PlaybackTracker tracker, LyricsManager lyrics, TimingOffsets songTiming,
			  CoverArt covers, String version) {
		this.config = config;
		this.tracker = tracker;
		this.lyrics = lyrics;
		this.songTiming = songTiming;
		this.covers = covers;
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

	public @Nullable Updater updater() {
		return updater;
	}

	void setUpdater(Updater updater) {
		this.updater = updater;
	}

	public @Nullable SpotifyAccount spotify() {
		return spotify;
	}

	void setSpotify(SpotifyAccount spotify) {
		this.spotify = spotify;
	}

	/** Every tick: lets the Spotify account know when the song changes. */
	void songTick() {
		String key = lyrics.trackKey();
		if (spotify != null && !Objects.equals(key, lastSongKey)) {
			spotify.songChanged();
		}
		lastSongKey = key;
	}

	public CoverArt covers() {
		return covers;
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

	private String holidayKey;

	/** Works out which holiday theme to show today (cheap: only redone when the day or a setting changes). */
	public void updateHoliday() {
		Region region = holidayRegion();
		LocalDate today = LocalDate.now();
		String key = today + "|" + config.holidays + "|" + region + "|" + config.holidayPreview;
		if (key.equals(holidayKey)) {
			return;
		}
		holidayKey = key;
		HolidayTheme preview = null;
		for (HolidayTheme t : HolidayTheme.values()) {
			if (t.name().equalsIgnoreCase(config.holidayPreview)) {
				preview = t;
			}
		}
		if (preview != null) {
			config.activeHoliday = HolidayFinder.preview(
					region != null ? region : Region.UNITED_STATES, preview);
		} else {
			config.activeHoliday = HolidayFinder.find(region, today, config.holidays);
		}
	}

	/** The region chosen in the settings, or the computer's own region for "auto" (null if it isn't known). */
	public @Nullable Region holidayRegion() {
		if (!"auto".equalsIgnoreCase(config.holidayRegion)) {
			Region chosen = Region.forCode(config.holidayRegion);
			if (chosen != null) {
				return chosen;
			}
		}
		return detectRegion();
	}

	/** The computer's region setting, or else the country of Minecraft's language. */
	public static @Nullable Region detectRegion() {
		Region r = Region.forCode(Locale.getDefault().getCountry());
		if (r == null) {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.options != null) {
				r = Region.forCode(mc.options.languageCode);
			}
		}
		return r;
	}

	public int settingsRevision() {
		return settingsRevision;
	}

	/** Applies changed settings right away. Call {@code config().save()} to persist them. */
	public void settingsChanged() {
		settingsRevision++;
		holidayKey = null;
		tracker.system().setPreferredPlayer(config.preferredPlayer);
	}

	/** The view of the mod that the settings screen and song card draw from. */
	public UiHost uiHost(Runnable close) {
		Artlyrics mod = this;
		return new UiHost() {
			@Override
			public float fovDegrees() {
				return net.minecraft.client.Minecraft.getInstance().options.fov().get().floatValue();
			}

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
				covers.refresh();
			}

			@Override
			public CoverImage cover() {
				return covers.current();
			}

			@Override
			public boolean sendMedia(MediaCommand command) {
				return tracker.send(command);
			}

			@Override
			public SpotifyAccount spotify() {
				return spotify;
			}

			@Override
			public String clipboard() {
				return Minecraft.getInstance().keyboardHandler.getClipboard();
			}

			@Override
			public void openUrl(String url) {
				Mc.openUrl(url);
			}

			@Override
			public Updater updater() {
				return updater;
			}

			@Override
			public void quitGame() {
				Minecraft.getInstance().stop();
			}

			@Override
			public Region holidayRegion() {
				return mod.holidayRegion();
			}

			@Override
			public String autoRegionName() {
				Region r = detectRegion();
				return r == null ? "" : r.displayName();
			}

			@Override
			public void openCoversFolder() {
				covers.createFolder();
				Mc.openFolder(covers.folder());
			}

			@Override
			public String version() {
				return version;
			}

			@Override
			public String tr(String key) {
				// The raw text: I18n.get would try to fill in "%s" itself and, with nothing to fill in, show
				// "Format error: ..." instead. Callers that need a value put it in with String.format.
				return Language.getInstance().getOrDefault(key, key);
			}

			@Override
			public void close() {
				close.run();
			}
		};
	}
}
