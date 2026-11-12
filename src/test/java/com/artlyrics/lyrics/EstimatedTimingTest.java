package com.artlyrics.lyrics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstimatedTimingTest {
	private static final String PLAIN = """
			[Verse 1]
			Lanterns floating on the river
			Every light a little wish

			[Chorus]
			Carry me home
			Carry me home tonight
			""";

	@Test
	void spreadsLinesOverTheSong() {
		Lyrics lyrics = EstimatedTiming.build(PLAIN, 180_000, "test");
		assertTrue(lyrics.estimated());
		List<LyricLine> lines = lyrics.lines().stream().filter(l -> !l.isBlank()).toList();
		assertEquals(4, lines.size());
		assertEquals("Lanterns floating on the river", lines.get(0).text());
		// An intro before the first line, and everything finished before the song ends.
		assertTrue(lines.get(0).startMs() >= 4000, "intro");
		assertTrue(lines.get(3).startMs() < 180_000 - 3000, "outro");
		for (int i = 1; i < lines.size(); i++) {
			assertTrue(lines.get(i).startMs() > lines.get(i - 1).startMs());
		}
		// The break between verses makes a bigger gap than between lines of a verse.
		long inVerse = lines.get(1).startMs() - lines.get(0).startMs();
		long overBreak = lines.get(2).startMs() - lines.get(1).startMs();
		long lineTwoLength = lines.get(1).endMs() - lines.get(1).startMs();
		assertTrue(overBreak > lineTwoLength, "pause between verses");
		assertTrue(inVerse > 0);
		// Every line gets word timings.
		assertTrue(lines.get(0).words().size() >= 5);
	}

	@Test
	void countsSyllablesRoughly() {
		assertEquals(4, EstimatedTiming.syllables("Every light"));
		assertEquals(3, EstimatedTiming.syllables("你好吗"));
		assertTrue(EstimatedTiming.syllables("Saule spīd") >= 3);
	}

	@Test
	void unknownLengthStillWorks() {
		Lyrics lyrics = EstimatedTiming.build("One line\nTwo line", 0, "test");
		assertEquals(2, lyrics.lines().size());
		assertTrue(lyrics.lines().get(1).startMs() < EstimatedTiming.ASSUMED_DURATION_MS);
	}
}
