package com.artlyrics.lyrics;

/** A place lyrics can come from (the lyrics folder, LRCLIB, NetEase Cloud Music). */
public interface LyricsSource {
	/** Short name shown to the player, e.g. "LRCLIB". */
	String name();

	/**
	 * Looks for lyrics. Returns {@link LyricsResult.Status#UNSYNCED_ONLY} with the text when only lyrics without
	 * timestamps exist, and {@link LyricsResult.Status#ERROR} when the source couldn't be reached.
	 */
	LyricsResult lookup(TrackQuery track) throws InterruptedException;
}
