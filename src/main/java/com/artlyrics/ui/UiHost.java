package com.artlyrics.ui;

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

	@Nullable Lyrics lyrics();

	LyricsManager.State lyricsState();

	/** Why music can't be detected, or null. */
	@Nullable String problem();

	boolean demoActive();

	void setDemoActive(boolean active);

	void refreshLyrics();

	String version();

	/** Translates a language key. */
	String tr(String key);

	void close();
}
