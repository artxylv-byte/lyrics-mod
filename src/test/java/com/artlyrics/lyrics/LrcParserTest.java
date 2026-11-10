package com.artlyrics.lyrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrcParserTest {
	@Test
	void parsesBasicLrc() {
		String lrc = """
				[ar:Test Artist]
				[ti:Test Song]
				[00:03.30] First line here
				[00:07.20] Second line
				[00:10.1]Third
				[00:15.250]
				[00:20.00] After the break
				""";
		Lyrics lyrics = LrcParser.parse(lrc, 60_000, "test");
		assertFalse(lyrics.wordSynced());
		assertEquals(5, lyrics.lines().size());
		assertEquals(3300, lyrics.lines().get(0).startMs());
		assertEquals("First line here", lyrics.lines().get(0).text());
		assertEquals(10_100, lyrics.lines().get(2).startMs());
		assertTrue(lyrics.lines().get(3).isBlank(), "empty timestamp line marks a break");
		assertEquals(15_250, lyrics.lines().get(3).startMs());
	}

	@Test
	void estimatedWordsStayInsideTheLine() {
		Lyrics lyrics = LrcParser.parse("[00:01.00]one two three four\n[00:05.00]next", 0, "test");
		LyricLine line = lyrics.lines().get(0);
		assertEquals(4, line.words().size());
		long prevStart = -1;
		for (LyricWord w : line.words()) {
			assertTrue(w.startMs() >= 1000 && w.endMs() <= 5000, w.toString());
			assertTrue(w.startMs() > prevStart);
			prevStart = w.startMs();
		}
		assertEquals("one ", line.words().get(0).text());
		assertEquals("four", line.words().get(3).text());
		assertTrue(line.visibleUntil() <= 5000);
	}

	@Test
	void appliesOffsetAndRepeatedTimestamps() {
		String lrc = """
				[offset:+500]
				[00:10.00][00:30.00]Chorus line
				[00:20.00]Verse line
				""";
		Lyrics lyrics = LrcParser.parse(lrc, 0, "test");
		assertEquals(3, lyrics.lines().size());
		assertEquals(9_500, lyrics.lines().get(0).startMs());
		assertEquals("Verse line", lyrics.lines().get(1).text());
		assertEquals(29_500, lyrics.lines().get(2).startMs());
		assertEquals("Chorus line", lyrics.lines().get(2).text());
	}

	@Test
	void parsesEnhancedWordTags() {
		Lyrics lyrics = LrcParser.parse("[00:01.00]<00:01.00>Hello <00:01.50>bright <00:02.25>world<00:03.00>\n[00:05.00]x", 0, "test");
		assertTrue(lyrics.wordSynced());
		LyricLine line = lyrics.lines().get(0);
		assertEquals("Hello bright world", line.text());
		assertEquals(3, line.words().size());
		assertEquals("Hello ", line.words().get(0).text());
		assertEquals(1500, line.words().get(1).startMs());
		assertEquals(2250, line.words().get(1).endMs());
		assertEquals(3000, line.words().get(2).endMs());
	}

	@Test
	void lineAndWordLookup() {
		Lyrics lyrics = LrcParser.parse("[00:01.00]a b\n[00:04.00]c d", 10_000, "test");
		assertEquals(-1, lyrics.lineIndexAt(500));
		assertEquals(0, lyrics.lineIndexAt(1000));
		assertEquals(0, lyrics.lineIndexAt(3999));
		assertEquals(1, lyrics.lineIndexAt(9000));
		LyricLine first = lyrics.lines().get(0);
		assertEquals(-1, first.wordIndexAt(999));
		assertEquals(0, first.wordIndexAt(1000));
	}

	@Test
	void splitsCjkIntoCharacters() {
		Lyrics lyrics = LrcParser.parse("[00:01.00]夜空の星\n[00:05.00]x", 0, "test");
		LyricLine line = lyrics.lines().get(0);
		assertEquals(4, line.words().size());
		assertEquals("夜", line.words().get(0).text());
	}
}
