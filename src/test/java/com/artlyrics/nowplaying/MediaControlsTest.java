package com.artlyrics.nowplaying;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaControlsTest {
	@Test
	void demoSongPausesResumesAndRestarts() throws Exception {
		DemoMediaSource demo = new DemoMediaSource();
		demo.seek(5_000, false);
		assertTrue(demo.current().playing());
		assertTrue(demo.send(MediaCommand.PLAY_PAUSE));
		assertFalse(demo.current().playing());
		long pausedAt = demo.current().positionMs();
		Thread.sleep(50);
		assertTrue(demo.current().positionMs() == pausedAt, "stays put while paused");
		demo.send(MediaCommand.PLAY_PAUSE);
		assertTrue(demo.current().playing());
		demo.send(MediaCommand.NEXT);
		assertTrue(demo.current().positionMs() < 1_000);
	}

	@Test
	void trackerSendsToTheDemoWhenItsOn() {
		PlaybackTracker tracker = new PlaybackTracker(new MediaSource() {
			@Override
			public void start() {
			}

			@Override
			public NowPlaying current() {
				return null;
			}

			@Override
			public String problem() {
				return null;
			}

			@Override
			public void close() {
			}
		});
		assertFalse(tracker.send(MediaCommand.NEXT), "a source without controls says so");
		tracker.setDemoActive(true);
		assertTrue(tracker.send(MediaCommand.PLAY_PAUSE));
	}
}
