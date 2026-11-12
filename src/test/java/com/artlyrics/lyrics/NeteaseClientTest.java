package com.artlyrics.lyrics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeteaseClientTest {
	private static final String SEARCH = """
			{"result":{"songs":[
			  {"id":1,"name":"Paper Lanterns","artists":[{"name":"Other Band"}],"duration":200000},
			  {"id":2,"name":"Paper Lanterns (Live)","artists":[{"name":"Moon Harbor"}],"duration":260000},
			  {"id":3,"name":"Paper Lanterns","artists":[{"name":"Moon Harbor"},{"name":"Guest"}],"duration":201500}
			],"songCount":3},"code":200}""";

	@Test
	void picksTheSongWithTheSameTitleArtistAndLength() {
		List<NeteaseClient.Song> songs = NeteaseClient.parseSearch(JsonParser.parseString(SEARCH).getAsJsonObject());
		assertEquals(3, songs.size());
		assertEquals("Moon Harbor, Guest", songs.get(2).artists());
		NeteaseClient.Song pick = NeteaseClient.pickBest(songs, new TrackQuery("Paper Lanterns", "Moon Harbor", "", 0), 201_000);
		assertNotNull(pick);
		assertEquals(3, pick.id());
		// Wrong artist: nothing.
		assertNull(NeteaseClient.pickBest(songs, new TrackQuery("Paper Lanterns", "Nobody", "", 0), 201_000));
	}

	@Test
	void readsLyricsAndDropsCredits() {
		JsonObject o = JsonParser.parseString("""
				{"lrc":{"version":3,"lyric":"[00:00.00] 作词 : Someone\\n[00:00.50] Composer: Someone Else\\n{\\"t\\":0,\\"c\\":[{\\"tx\\":\\"x\\"}]}\\n[00:12.00]Lanterns floating on the river\\n[00:16.50]Every light a little wish\\n"},"code":200}
				""").getAsJsonObject();
		LyricsResult r = NeteaseClient.toResult(o, new NeteaseClient.Song(3, "Paper Lanterns", "Moon Harbor", 201_500), 201_000);
		assertEquals(LyricsResult.Status.FOUND, r.status());
		List<LyricLine> lines = r.lyrics().lines().stream().filter(l -> !l.isBlank()).toList();
		assertEquals(2, lines.size());
		assertEquals(12_000, lines.get(0).startMs());
		assertFalse(lines.get(0).text().contains("作词"));
	}

	@Test
	void instrumentalAndMissing() {
		NeteaseClient.Song song = new NeteaseClient.Song(1, "Calm", "Moon Harbor", 100_000);
		JsonObject inst = JsonParser.parseString("{\"lrc\":{\"lyric\":\"[00:00.00]纯音乐，请欣赏\\n\"}}").getAsJsonObject();
		assertEquals(LyricsResult.Status.INSTRUMENTAL, NeteaseClient.toResult(inst, song, 0).status());
		JsonObject none = JsonParser.parseString("{\"nolyric\":true,\"code\":200}").getAsJsonObject();
		assertEquals(LyricsResult.Status.NOT_FOUND, NeteaseClient.toResult(none, song, 0).status());
		JsonObject plain = JsonParser.parseString("{\"lrc\":{\"lyric\":\"First line\\nSecond line\"}}").getAsJsonObject();
		LyricsResult p = NeteaseClient.toResult(plain, song, 0);
		assertEquals(LyricsResult.Status.UNSYNCED_ONLY, p.status());
		assertTrue(p.plainLyrics().contains("Second line"));
	}
}
