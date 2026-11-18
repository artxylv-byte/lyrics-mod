package com.artlyrics.nowplaying;

import org.jetbrains.annotations.Nullable;

/** Something that can tell us what music is playing on this computer. */
public interface MediaSource extends AutoCloseable {
	/** Starts background work. Called once. */
	void start();

	/** The latest known state, or null when nothing is playing (or it can't be determined). */
	@Nullable NowPlaying current();

	/** A short message when the source can't work (e.g. a missing tool), otherwise null. */
	@Nullable String problem();

	/** Asks the source to prefer this player when several are playing (e.g. "spotify"). */
	default void setPreferredPlayer(String preferred) {
	}

	/**
	 * Asks the player to play / pause or skip. Returns false if this source can't control players. The new state
	 * shows up with the next update of {@link #current()}.
	 */
	default boolean send(MediaCommand command) {
		return false;
	}

	@Override
	void close();
}
