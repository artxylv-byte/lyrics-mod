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
