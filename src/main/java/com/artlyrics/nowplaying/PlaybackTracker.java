package com.artlyrics.nowplaying;

import org.jetbrains.annotations.Nullable;

/**
 * Turns the jumpy, occasionally-sampled player position into a smooth clock for animations.
 * Call {@link #frame(long)} once per rendered frame (calling it more often is harmless).
 */
public final class PlaybackTracker {
	/** Differences larger than this are treated as a seek and applied instantly. */
	private static final double SNAP_MS = 750;
	/** How quickly small differences are smoothed away. */
	private static final double CONVERGE_MS = 300;

	private final MediaSource system;
	private final DemoMediaSource demo = new DemoMediaSource();
	private boolean demoActive;

	private @Nullable NowPlaying track;
	private double clockMs;
	private long lastFrameNanos;
	private long trackFirstSeenNanos;

	public PlaybackTracker(MediaSource system) {
		this.system = system;
	}

	public void start() {
		system.start();
	}

	public void close() {
		system.close();
	}

	public MediaSource system() {
		return system;
	}

	public boolean isDemoActive() {
		return demoActive;
	}

	public void setDemoActive(boolean active) {
		if (active && !demoActive) {
			demo.restart();
		}
		demoActive = active;
	}

	/** Advances the clock to {@code nowNanos}. */
	public void frame(long nowNanos) {
		NowPlaying sample = demoActive ? demo.current() : system.current();
		if (sample == null || sample.title().isBlank()) {
			track = null;
			lastFrameNanos = nowNanos;
			return;
		}

		boolean newTrack = track == null || !track.sameTrackAs(sample);
		if (newTrack) {
			trackFirstSeenNanos = nowNanos;
		}

		double target = sample.positionAt(nowNanos);
		if (sample.playing() && sample.positionMs() == 0 && sample.durationMs() == 0) {
			// Some players publish no timeline at all; count from when the track appeared instead.
			target = (nowNanos - trackFirstSeenNanos) / 1_000_000.0;
		}

		double dt = lastFrameNanos == 0 ? 0 : Math.max(0, (nowNanos - lastFrameNanos) / 1_000_000.0);
		lastFrameNanos = nowNanos;

		if (newTrack || !sample.playing() || Math.abs(target - clockMs) > SNAP_MS) {
			clockMs = target;
		} else {
			clockMs += dt;
			clockMs += (target - clockMs) * Math.min(1.0, dt / CONVERGE_MS);
		}
		track = sample;
	}

	/** The track being followed, or null if nothing is playing. */
	public @Nullable NowPlaying track() {
		return track;
	}

	/** Smoothed playback position in milliseconds. */
	public long positionMs() {
		return Math.max(0, Math.round(clockMs));
	}

	public @Nullable String problem() {
		return demoActive ? null : system.problem();
	}
}
