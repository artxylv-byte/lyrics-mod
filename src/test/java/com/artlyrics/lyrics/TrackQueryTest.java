package com.artlyrics.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackQueryTest {
	@Test
	void cleansTitles() {
		assertEquals("Song Name", TrackQuery.cleanTitle("Song Name (Official Music Video)"));
		assertEquals("Song Name", TrackQuery.cleanTitle("Song Name - 2011 Remaster"));
		assertEquals("Song Name", TrackQuery.cleanTitle("Song Name (feat. Someone Else)"));
		assertEquals("Song Name", TrackQuery.cleanTitle("Song Name [Lyric Video]"));
		assertEquals("Live Forever", TrackQuery.cleanTitle("Live Forever"));
	}

	@Test
	void fallbacksIncludeVideoStyleTitles() {
		TrackQuery q = new TrackQuery("Some Artist - Great Song (Official Video)", "SomeArtistVEVO", "", 200_000);
		List<TrackQuery> f = q.fallbacks();
		assertTrue(f.stream().anyMatch(t -> t.title().equals("Great Song") && t.artist().equals("Some Artist")), f.toString());
	}

	@Test
	void firstArtist() {
		assertEquals("Alpha", TrackQuery.firstArtist("Alpha, Beta"));
		assertEquals("Alpha", TrackQuery.firstArtist("Alpha & Beta"));
		assertEquals("Alpha", TrackQuery.firstArtist("Alpha feat. Beta"));
	}

	@Test
	void pickBestPrefersSyncedAndCloseDuration() {
		JsonArray results = JsonParser.parseString("""
				[
				  {"trackName":"Song","artistName":"A","duration":200,"plainLyrics":"x","syncedLyrics":null},
				  {"trackName":"Song","artistName":"A","duration":260,"syncedLyrics":"[00:01.00]far"},
				  {"trackName":"Song","artistName":"A","duration":201,"syncedLyrics":"[00:01.00]close"},
				  {"trackName":"Song","artistName":"A","duration":202,"syncedLyrics":"[00:01.00]word","hasWordSync":true,"lyricsfile":"lines:\\n- text: word\\n  start_ms: 1000\\n"}
				]
				""").getAsJsonArray();
		JsonObject best = LrclibClient.pickBest(results, new TrackQuery("Song", "A", "", 200_000));
		assertEquals("[00:01.00]word", best.get("syncedLyrics").getAsString());

		JsonObject none = LrclibClient.pickBest(JsonParser.parseString("""
				[{"trackName":"Song","artistName":"A","duration":300,"syncedLyrics":"[00:01.00]x"}]
				""").getAsJsonArray(), new TrackQuery("Song", "A", "", 200_000));
		assertNull(none);
	}

	@Test
	void toResultHandlesInstrumentalAndUnsynced() {
		JsonObject instrumental = JsonParser.parseString("{\"trackName\":\"T\",\"artistName\":\"A\",\"instrumental\":true}").getAsJsonObject();
		assertEquals(LyricsResult.Status.INSTRUMENTAL, LrclibClient.toResult(instrumental, 0).status());

		JsonObject plain = JsonParser.parseString("{\"trackName\":\"T\",\"artistName\":\"A\",\"plainLyrics\":\"la la\"}").getAsJsonObject();
		assertEquals(LyricsResult.Status.UNSYNCED_ONLY, LrclibClient.toResult(plain, 0).status());

		JsonObject synced = JsonParser.parseString("{\"trackName\":\"T\",\"artistName\":\"A\",\"syncedLyrics\":\"[00:01.00]la la\"}").getAsJsonObject();
		LyricsResult r = LrclibClient.toResult(synced, 0);
		assertEquals(LyricsResult.Status.FOUND, r.status());
		assertEquals("la la", r.lyrics().lines().get(0).text());
	}
}
