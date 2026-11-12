package com.artlyrics.lyrics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyricsFinderTest {
	private static final TrackQuery SONG = new TrackQuery("Paper Lanterns", "Moon Harbor", "", 150_000);

	private record Fake(String name, LyricsResult result, List<String> calls) implements LyricsSource {
		@Override
		public LyricsResult lookup(TrackQuery track) {
			calls.add(name);
			return result;
		}
	}

	private static LyricsResult synced(String source) {
		return LyricsResult.found(LrcParser.parse("[00:05.00]Hello there\n", 150_000, source));
	}

	@Test
	void firstSyncedResultWins() throws Exception {
		List<String> calls = new ArrayList<>();
		LyricsFinder finder = new LyricsFinder(null,
				new Fake("lrclib", LyricsResult.unsynced("Hello there\nGoodbye", "x"), calls),
				new Fake("netease", synced("NetEase"), calls), () -> LyricsFinder.Settings.ALL);
		LyricsResult r = finder.find(SONG);
		assertEquals(LyricsResult.Status.FOUND, r.status());
		assertEquals("NetEase", r.lyrics().source());
		assertEquals(List.of("lrclib", "netease"), calls);
	}

	@Test
	void plainLyricsGetEstimatedTimingWhenNothingIsSynced() throws Exception {
		List<String> calls = new ArrayList<>();
		LyricsFinder finder = new LyricsFinder(null,
				new Fake("lrclib", LyricsResult.unsynced("Hello there\nGoodbye", "Moon Harbor – Paper Lanterns"), calls),
				new Fake("netease", LyricsResult.of(LyricsResult.Status.NOT_FOUND, "none"), calls), () -> LyricsFinder.Settings.ALL);
		LyricsResult r = finder.find(SONG);
		assertEquals(LyricsResult.Status.FOUND, r.status());
		assertTrue(r.lyrics().estimated());
		assertTrue(r.detail().contains("estimated"), r.detail());

		LyricsFinder noEstimate = new LyricsFinder(null,
				new Fake("lrclib", LyricsResult.unsynced("Hello there", "x"), calls), null,
				() -> new LyricsFinder.Settings(true, true, true, false));
		assertEquals(LyricsResult.Status.UNSYNCED_ONLY, noEstimate.find(SONG).status());
	}

	@Test
	void switchedOffSourcesAreSkippedAndErrorsReported() throws Exception {
		List<String> calls = new ArrayList<>();
		LyricsFinder finder = new LyricsFinder(null,
				new Fake("lrclib", LyricsResult.of(LyricsResult.Status.ERROR, "offline"), calls),
				new Fake("netease", synced("NetEase"), calls), () -> new LyricsFinder.Settings(true, true, false, true));
		assertEquals(LyricsResult.Status.ERROR, finder.find(SONG).status());
		assertEquals(List.of("lrclib"), calls);
	}
}
