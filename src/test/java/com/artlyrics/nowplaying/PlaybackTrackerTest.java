package com.artlyrics.nowplaying;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackTrackerTest {
	/** A player that reports its position with a delay that varies, like a real one polled every half second. */
	private static final class JitteryPlayer implements MediaSource {
		NowPlaying current;

		@Override
		public void start() {
		}

		@Override
		public NowPlaying current() {
			return current;
		}

		@Override
		public String problem() {
			return null;
		}

		@Override
		public void close() {
		}
	}

	@Test
	void clockRunsSmoothlyThroughNoisySamples() {
		JitteryPlayer player = new JitteryPlayer();
		PlaybackTracker tracker = new PlaybackTracker(player);
		long frame = 16_666_667L; // 60 fps
		long t = 1_000_000_000L;
		long songStart = t;
		double lastPos = -1;
		double maxStep = 0;
		double minStep = Double.MAX_VALUE;
		for (int i = 0; i < 60 * 20; i++) {
			if (i % 30 == 0) {
				// New sample every half second, up to ±60 ms off.
				long jitter = ((i / 30) % 3 - 1) * 60L;
				long truePos = (t - songStart) / 1_000_000L;
				player.current = new NowPlaying("Test", "Song", "Artist", "", 200_000, truePos + jitter, true, t);
			}
			tracker.frame(t);
			double pos = tracker.positionMs();
			if (lastPos >= 0 && i > 30) {
				double step = pos - lastPos;
				maxStep = Math.max(maxStep, step);
				minStep = Math.min(minStep, step);
			}
			lastPos = pos;
			t += frame;
		}
		// Every frame moves the clock forward by about one frame (16.7 ms), never jumping or standing still.
		assertTrue(maxStep <= 16.7 * 1.1 + 1, "max step " + maxStep);
		assertTrue(minStep >= 16.7 * 0.9 - 1, "min step " + minStep);
		// And it stays close to the real position.
		long truePos = (t - frame - songStart) / 1_000_000L;
		assertTrue(Math.abs(tracker.positionMs() - truePos) < 90, "drift " + (tracker.positionMs() - truePos));
	}

	@Test
	void seeksJumpStraightThere() {
		JitteryPlayer player = new JitteryPlayer();
		PlaybackTracker tracker = new PlaybackTracker(player);
		long t = 5_000_000_000L;
		player.current = new NowPlaying("Test", "Song", "Artist", "", 200_000, 10_000, true, t);
		tracker.frame(t);
		t += 16_666_667L;
		player.current = new NowPlaying("Test", "Song", "Artist", "", 200_000, 90_000, true, t);
		tracker.frame(t);
		assertEquals(90_000, tracker.positionMs(), 1);
	}
}
