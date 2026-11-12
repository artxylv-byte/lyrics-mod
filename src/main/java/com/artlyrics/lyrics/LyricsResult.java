package com.artlyrics.lyrics;

import org.jetbrains.annotations.Nullable;

/**
 * Outcome of a lyrics lookup.
 *
 * @param plainLyrics the lyrics text without timestamps, when that's all a source has ({@link Status#UNSYNCED_ONLY})
 */
public record LyricsResult(Status status, @Nullable Lyrics lyrics, String detail, @Nullable String plainLyrics) {
	public LyricsResult(Status status, @Nullable Lyrics lyrics, String detail) {
		this(status, lyrics, detail, null);
	}

	public enum Status {
		/** Synced lyrics were found. */
		FOUND,
		/** The track is marked instrumental. */
		INSTRUMENTAL,
		/** Lyrics exist but only without timestamps, so they can't be synced. */
		UNSYNCED_ONLY,
		/** Nothing matched. */
		NOT_FOUND,
		/** Network or parse error. */
		ERROR
	}

	public static LyricsResult found(Lyrics lyrics) {
		return new LyricsResult(Status.FOUND, lyrics, lyrics.source());
	}

	public static LyricsResult of(Status status, String detail) {
		return new LyricsResult(status, null, detail);
	}

	/** Lyrics text without timestamps. */
	public static LyricsResult unsynced(String plainLyrics, String detail) {
		return new LyricsResult(Status.UNSYNCED_ONLY, null, detail, plainLyrics);
	}
}
