package com.artlyrics.lyrics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyricsFileParserTest {
	// Same shape as LRCLIB's "lyricsfile" field, with made-up lyrics.
	private static final String DOC = """
			version: '1.0'
			metadata:
			  title: Paper Lanterns
			  artist: Test Artist
			  duration_ms: 180000
			  instrumental: false
			lines:
			- text: Paper lanterns over the bay
			  start_ms: 3500
			  end_ms: 6000
			  words:
			  - text: 'Paper '
			    start_ms: 3550
			  - text: 'lanterns '
			    start_ms: 3900
			  - text: 'over '
			    start_ms: 4500
			  - text: 'the '
			    start_ms: 4800
			  - text: bay
			    start_ms: 5100
			- text: We'd drift until the morning's gray
			  start_ms: 7000
			  end_ms: 9500
			  words:
			  - text: 'We''d '
			    start_ms: 7050
			  - text: 'drift '
			    start_ms: 7300
			  - text: 'until '
			    start_ms: 7700
			  - text: 'the '
			    start_ms: 8000
			  - text: 'morning''s '
			    start_ms: 8200
			  - text: gray
			    start_ms: 8900
			- text: "Caf\\u00e9 lights: \\"glow\\""
			  start_ms: 11000
			  end_ms: 12500
			  words:
			  - text: "Caf\\u00e9 "
			    start_ms: 11000
			    end_ms: 11400
			  - text: 'lights: '
			    start_ms: 11400
			  - text: '"glow"'
			    start_ms: 12000
			plain: "Paper lanterns over the bay\\nWe'd drift until the morning's gray"
			""";

	@Test
	void parsesLinesAndWords() {
		Lyrics lyrics = LyricsFileParser.parse(DOC, 180_000, "test");
		assertTrue(lyrics.wordSynced());
		assertEquals(3, lyrics.lines().size());

		LyricLine first = lyrics.lines().get(0);
		assertEquals("Paper lanterns over the bay", first.text());
		assertEquals(3500, first.startMs());
		assertEquals(6000, first.endMs());
		assertEquals(5, first.words().size());
		assertEquals("Paper ", first.words().get(0).text());
		assertEquals(3550, first.words().get(0).startMs());
		// A word ends where the next one starts...
		assertEquals(3900, first.words().get(0).endMs());
		// ...and the last word ends with the line.
		assertEquals(6000, first.words().get(4).endMs());
	}

	@Test
	void unescapesQuotedScalars() {
		Lyrics lyrics = LyricsFileParser.parse(DOC, 180_000, "test");
		LyricLine second = lyrics.lines().get(1);
		assertEquals("We'd drift until the morning's gray", second.text());
		assertEquals("We'd ", second.words().get(0).text());
		assertEquals("morning's ", second.words().get(4).text());

		LyricLine third = lyrics.lines().get(2);
		assertEquals("Café lights: \"glow\"", third.text());
		assertEquals("Café ", third.words().get(0).text());
		assertEquals(11400, third.words().get(0).endMs());
		assertEquals("\"glow\"", third.words().get(2).text());
	}

	@Test
	void ignoresSectionsOutsideLines() {
		List<LineAssembler.RawLine> raw = LyricsFileParser.parseRaw(DOC);
		assertEquals(3, raw.size());
		assertFalse(raw.stream().anyMatch(l -> l.text().contains("\n")));
	}

	@Test
	void toleratesIndentedSequences() {
		String doc = """
				lines:
				  - text: Hello there
				    start_ms: 1000
				    words:
				      - text: 'Hello '
				        start_ms: 1000
				      - text: there
				        start_ms: 1400
				  - text: Second line
				    start_ms: 3000
				""";
		Lyrics lyrics = LyricsFileParser.parse(doc, 0, "test");
		assertEquals(2, lyrics.lines().size());
		assertEquals(2, lyrics.lines().get(0).words().size());
		assertEquals(1400, lyrics.lines().get(0).words().get(1).startMs());
		// No word timings on the second line: they get estimated.
		assertEquals(2, lyrics.lines().get(1).words().size());
	}

	@Test
	void joinsMultiLineQuotedScalars() {
		String doc = """
				lines:
				- text: 'A very long line that an emitter
				    decided to wrap'
				  start_ms: 500
				""";
		Lyrics lyrics = LyricsFileParser.parse(doc, 0, "test");
		assertEquals("A very long line that an emitter decided to wrap", lyrics.lines().get(0).text());
	}
}
