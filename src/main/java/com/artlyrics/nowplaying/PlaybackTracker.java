package com.artlyrics.nowplaying;

import org.jetbrains.annotations.Nullable;

/**
 * Turns the jumpy, occasionally-sampled player position into a smooth clock for animations.
 * Call {@link #frame(long)} once per rendered frame (calling it more often is harmless).
 */
public final class PlaybackTracker {
	/** Differences larger than this are treated as a seek and applied instantly. */
	private static final double SNAP_MS = 450;
	/** Differences smaller than this are just sampling noise and are left alone. */
	private static final double DEADBAND_MS = 25;
	/** A difference of this many milliseconds speeds the clock up (or slows it down) by 100 %... */
	private static final double CORRECT_MS = 500;
	/** ...but never by more than this, so words never visibly rush or stall while it catches up. */
	private static final double MAX_RATE_CHANGE = 0.08;

	private final MediaSource system;
	private final DemoMediaSource demo = new DemoMediaSource();
	private boolean demoActive;

	private @Nullable NowPlaying track;
	private double clockMs;
	private long lastFrameNanos;
	private boolean hasFrame;
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

	/** Turns the demo on and jumps to {@code positionMs}; with {@code pause} it stays frozen there. */
	public void seekDemo(long positionMs, boolean pause) {
		setDemoActive(true);
		demo.seek(positionMs, pause);
	}

	/** Advances the clock to {@code nowNanos}. */
	public void frame(long nowNanos) {
		NowPlaying sample = demoActive ? demo.current() : system.current();
		if (sample == null || sample.title().isBlank()) {
			track = null;
			lastFrameNanos = nowNanos;
			hasFrame = true;
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

		double dt = hasFrame ? Math.max(0, (nowNanos - lastFrameNanos) / 1_000_000.0) : 0;
		lastFrameNanos = nowNanos;
		hasFrame = true;

		double error = target - clockMs;
		if (newTrack || !sample.playing() || Math.abs(error) > SNAP_MS) {
			clockMs = target;
		} else {
			// Run at real speed and correct small differences by running a little faster or slower for a while,
			// instead of jumping, so the music and the words stay in step without the words wobbling.
			double rate = 1.0;
			if (Math.abs(error) > DEADBAND_MS) {
				rate += Math.max(-MAX_RATE_CHANGE, Math.min(MAX_RATE_CHANGE, error / CORRECT_MS));
			}
			clockMs += dt * rate;
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
