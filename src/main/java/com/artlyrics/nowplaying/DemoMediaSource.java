package com.artlyrics.nowplaying;

import org.jetbrains.annotations.Nullable;

/**
 * A pretend player that loops the built-in demo song, so the look can be previewed without any music.
 */
public final class DemoMediaSource implements MediaSource {
	public static final String PLAYER = "Artlyrics";
	public static final String TITLE = "Written in the Sky";
	public static final String ARTIST = "Artlyrics Demo";
	public static final long DURATION_MS = 32_000;

	private long startNanos = System.nanoTime();
	/** Position the demo is frozen at, or -1 while it plays. */
	private long pausedAtMs = -1;

	@Override
	public void start() {
		startNanos = System.nanoTime();
	}

	public void restart() {
		startNanos = System.nanoTime();
		pausedAtMs = -1;
	}

	/** Jumps to {@code positionMs} into the demo song; with {@code pause} it stays frozen there. */
	public void seek(long positionMs, boolean pause) {
		startNanos = System.nanoTime() - positionMs * 1_000_000L;
		pausedAtMs = pause ? positionMs : -1;
	}

	@Override
	public @Nullable NowPlaying current() {
		long now = System.nanoTime();
		if (pausedAtMs >= 0) {
			return new NowPlaying(PLAYER, TITLE, ARTIST, "", DURATION_MS, pausedAtMs, false, now);
		}
		long pos = ((now - startNanos) / 1_000_000L) % DURATION_MS;
		return new NowPlaying(PLAYER, TITLE, ARTIST, "", DURATION_MS, pos, true, now);
	}

	@Override
	public @Nullable String problem() {
		return null;
	}

	public static boolean isDemo(@Nullable NowPlaying np) {
		return np != null && PLAYER.equals(np.player()) && TITLE.equals(np.title());
	}

	@Override
	public void close() {
	}
}
