package com.artlyrics.nowplaying;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NowPlayingTest {
	private static final long MS = 1_000_000L;

	@Test
	void extrapolatesWhilePlayingAndClampsToDuration() {
		NowPlaying np = new NowPlaying("Spotify", "T", "A", "", 10_000, 4_000, true, 0);
		assertEquals(4_500, np.positionAt(500 * MS));
		assertEquals(10_000, np.positionAt(60_000 * MS));
		NowPlaying paused = new NowPlaying("Spotify", "T", "A", "", 10_000, 4_000, false, 0);
		assertEquals(4_000, paused.positionAt(500 * MS));
	}

	@Test
	void choosesPlayingThenPreferred() {
		NowPlaying spotifyPaused = new NowPlaying("Spotify", "a", "", "", 0, 0, false, 0);
		NowPlaying chromePlaying = new NowPlaying("Chrome", "b", "", "", 0, 0, true, 0);
		NowPlaying spotifyPlaying = new NowPlaying("Spotify", "c", "", "", 0, 0, true, 0);
		assertEquals("b", PollingMediaSource.choose(List.of(spotifyPaused, chromePlaying), "spotify").title());
		assertEquals("c", PollingMediaSource.choose(List.of(chromePlaying, spotifyPlaying), "spotify").title());
		assertEquals("b", PollingMediaSource.choose(List.of(chromePlaying, spotifyPlaying), "chrome").title());
		assertNull(PollingMediaSource.choose(List.of(), "spotify"));
	}

	@Test
	void windowsPlayerNames() {
		assertEquals("Spotify", WindowsMediaSource.playerName("Spotify.exe"));
		assertEquals("Spotify", WindowsMediaSource.playerName("SpotifyAB.SpotifyMusic_zpdnekdrzrea0!Spotify"));
		assertEquals("Edge", WindowsMediaSource.playerName("MSEdge"));
		assertEquals("Chrome", WindowsMediaSource.playerName("Chrome"));
		assertEquals("vlc", WindowsMediaSource.playerName("vlc.exe"));
	}

	@Test
	void trackerSmoothsSmallJitterAndSnapsOnSeek() {
		FakeSource source = new FakeSource();
		PlaybackTracker tracker = new PlaybackTracker(source);

		source.np = new NowPlaying("Spotify", "Song", "Artist", "", 200_000, 10_000, true, 0);
		tracker.frame(0);
		assertEquals(10_000, tracker.positionMs());

		// 16 ms later the player re-samples 40 ms ahead: the clock moves forward smoothly, never backwards.
		source.np = new NowPlaying("Spotify", "Song", "Artist", "", 200_000, 10_056, true, 16 * MS);
		tracker.frame(16 * MS);
		long p = tracker.positionMs();
		assertTrue(p >= 10_016 && p <= 10_056, "smoothed position " + p);

		// A seek of several seconds is applied immediately.
		source.np = new NowPlaying("Spotify", "Song", "Artist", "", 200_000, 60_000, true, 32 * MS);
		tracker.frame(32 * MS);
		assertEquals(60_000, tracker.positionMs());

		// Nothing playing.
		source.np = null;
		tracker.frame(48 * MS);
		assertNull(tracker.track());
	}

	@Test
	void demoCanBeFrozen() {
		PlaybackTracker tracker = new PlaybackTracker(new FakeSource());
		tracker.seekDemo(12_345, true);
		tracker.frame(System.nanoTime());
		assertTrue(DemoMediaSource.isDemo(tracker.track()));
		assertEquals(12_345, tracker.positionMs());
	}

	private static final class FakeSource implements MediaSource {
		@Nullable NowPlaying np;

		@Override
		public void start() {
		}

		@Override
		public @Nullable NowPlaying current() {
			return np;
		}

		@Override
		public @Nullable String problem() {
			return null;
		}

		@Override
		public void close() {
		}
	}
}
