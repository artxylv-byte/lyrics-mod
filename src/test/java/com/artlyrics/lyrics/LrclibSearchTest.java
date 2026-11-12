package com.artlyrics.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrclibSearchTest {
	private static final JsonArray RESULTS = JsonParser.parseString("""
			[{"id":1,"trackName":"Paper Lanterns","artistName":"Other Band","duration":200,"syncedLyrics":"[00:01.00]x"},
			 {"id":2,"trackName":"Paper Lanterns","artistName":"Mōon Harbor","duration":201,"plainLyrics":"x"},
			 {"id":3,"trackName":"Paper Lanterns (Remastered)","artistName":"Moon Harbor","duration":202,"syncedLyrics":"[00:01.00]x"}]
			""").getAsJsonArray();

	@Test
	void freeTextResultsMustBeTheSameSong() {
		TrackQuery want = new TrackQuery("Paper Lanterns", "Moon Harbor", "", 0);
		JsonObject pick = LrclibClient.pickBest(RESULTS, want, 201_000, true);
		assertEquals(3, pick.get("id").getAsInt()); // synced, same artist (despite the accent) and title
		assertNull(LrclibClient.pickBest(RESULTS, new TrackQuery("Paper Lanterns", "Nobody", "", 0), 201_000, true));
		assertNull(LrclibClient.pickBest(RESULTS, new TrackQuery("Lanterns", "Moon Harbor", "", 0), 201_000, true));
	}

	@Test
	void triesFreeTextSearchesLast() {
		var searches = LrclibClient.searches(new TrackQuery("Paper Lanterns (Official Video)", "Moon Harbor feat. Guest", "", 0));
		var last = searches.get(searches.size() - 1);
		assertEquals("Paper Lanterns", last.freeText());
		assertTrue(last.strict());
		assertEquals("Moon Harbor Paper Lanterns", searches.get(searches.size() - 2).freeText());
	}
}
