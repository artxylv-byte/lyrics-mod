package com.artlyrics.lyrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Talks to the real LRCLIB API. Run with {@code ./gradlew test -Dartlyrics.liveTests=true}. */
@EnabledIfSystemProperty(named = "artlyrics.liveTests", matches = "true")
class LrclibLiveTest {
	private final LrclibClient client = new LrclibClient("Artlyrics-tests/1.0 (Minecraft Fabric mod)");

	@Test
	void findsWordSyncedLyricsByExactMatch() throws Exception {
		LyricsResult r = client.lookup(new TrackQuery("The One That Got Away", "Katy Perry", "Teenage Dream", 227_000));
		assertEquals(LyricsResult.Status.FOUND, r.status(), r.detail());
		assertNotNull(r.lyrics());
		assertTrue(r.lyrics().wordSynced());
		assertTrue(r.lyrics().lines().size() > 20);
		System.out.println("exact: " + r.lyrics().lines().size() + " lines, " + r.detail());
	}

	@Test
	void findsLyricsFromMessyVideoMetadata() throws Exception {
		LyricsResult r = client.lookup(new TrackQuery("The Weeknd - Blinding Lights (Official Video)", "TheWeekndVEVO", "", 0));
		assertEquals(LyricsResult.Status.FOUND, r.status(), r.detail());
		assertFalse(r.lyrics().isEmpty());
		System.out.println("messy: " + r.lyrics().lines().size() + " lines, wordSynced=" + r.lyrics().wordSynced());
	}

	@Test
	void matchesAccentsAndArtistPrefixedTitles() throws Exception {
		// LRCLIB stores this one as "Carnival Youth - Basās Kājas (Official Audio)".
		LyricsResult r = client.lookup(new TrackQuery("Basās Kājas", "Carnival Youth", "", 275_000));
		assertEquals(LyricsResult.Status.FOUND, r.status(), r.detail());
		// Typed without Latvian letters.
		LyricsResult plainName = client.lookup(new TrackQuery("Laikam", "Prata Vetra", "", 258_000));
		assertEquals(LyricsResult.Status.FOUND, plainName.status(), plainName.detail());
		System.out.println("latvian: " + r.detail() + " / " + plainName.detail());
	}

	@Test
	void lyricsWithoutTimestampsGetEstimatedTiming() throws Exception {
		LyricsFinder finder = new LyricsFinder(null, client, null, () -> LyricsFinder.Settings.ALL);
		LyricsResult r = finder.find(new TrackQuery("Deģenerāts", "Prāta Vētra", "", 203_000));
		assertEquals(LyricsResult.Status.FOUND, r.status(), r.detail());
		assertTrue(r.lyrics().estimated());
		System.out.println("estimated: " + r.lyrics().lines().size() + " lines, " + r.detail());
	}
}
