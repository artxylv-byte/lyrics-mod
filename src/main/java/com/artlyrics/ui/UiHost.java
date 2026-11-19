package com.artlyrics.ui;

import com.artlyrics.cover.CoverImage;
import com.artlyrics.holiday.HolidayFinder;
import com.artlyrics.holiday.Region;
import com.artlyrics.nowplaying.MediaCommand;
import com.artlyrics.spotify.SpotifyAccount;
import com.artlyrics.update.Updater;
import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.lyrics.Lyrics;
import com.artlyrics.lyrics.LyricsManager;
import com.artlyrics.nowplaying.NowPlaying;
import org.jetbrains.annotations.Nullable;

/** What the settings screen and song card need from the running mod (faked in previews). */
public interface UiHost {
	ArtlyricsConfig config();

	/** Called after any setting changes. */
	void settingsChanged();

	@Nullable NowPlaying track();

	long positionMs();

	/** Song time the lyrics are shown for: position plus the general and per-song timing corrections. */
	long lyricsTimeMs();

	/** The current song's own timing correction in milliseconds. */
	int songOffsetMs();

	void resetSongOffset();

	/** Opens the folder where players can put their own lyrics files. */
	void openLyricsFolder();

	/** Where the current lyrics came from, or why there are none. */
	String lyricsDetail();

	/** The current song's cover picture, or null. */
	default @Nullable CoverImage cover() {
		return null;
	}

	/** Checks for and installs new versions, or null where that isn't possible. */
	default @Nullable Updater updater() {
		return null;
	}

	/** Closes Minecraft (to finish an update). */
	default void quitGame() {
	}

	/** Sends play / pause or skip to the music player. */
	default boolean sendMedia(MediaCommand command) {
		return false;
	}

	/** The player's Spotify account (for liking and playlists), or null where that isn't available. */
	default @Nullable SpotifyAccount spotify() {
		return null;
	}

	/** Text on the clipboard, or "". */
	default String clipboard() {
		return "";
	}

	/** Opens a web page in the browser. */
	default void openUrl(String url) {
	}

	/** The holiday theme shown now, or null. */
	default @Nullable HolidayFinder.Active holiday() {
		return config().activeHoliday;
	}

	/** The region whose holidays are used, or null if it isn't known. */
	default @Nullable Region holidayRegion() {
		return Region.forCode(config().holidayRegion);
	}

	/** Name of the region found automatically (for "Auto (Latvia)"), or "" if unknown. */
	default String autoRegionName() {
		return "";
	}

	/** Opens the folder where players can put their own cover pictures. */
	default void openCoversFolder() {
	}

	@Nullable Lyrics lyrics();

	LyricsManager.State lyricsState();

	/** Why music can't be detected, or null. */
	@Nullable String problem();

	boolean demoActive();

	void setDemoActive(boolean active);

	void refreshLyrics();

	String version();

	/** The game's field of view in degrees (vertical), to place things on screen like the game does. */
	default float fovDegrees() {
		return 70f;
	}

	/** Translates a language key. */
	String tr(String key);

	void close();
}
