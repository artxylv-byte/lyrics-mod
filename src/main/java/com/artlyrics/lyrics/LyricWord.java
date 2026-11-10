package com.artlyrics.lyrics;

/**
 * One sung word (or syllable / character for languages without spaces).
 *
 * @param text    the word as displayed, including any trailing space that separates it from the next word
 * @param startMs song time in milliseconds when this word starts being sung
 * @param endMs   song time in milliseconds when the next word starts (or the line ends)
 */
public record LyricWord(String text, long startMs, long endMs) {
	/** The word without surrounding whitespace, for highlighting and measuring. */
	public String trimmed() {
		return text.strip();
	}

	public boolean isActiveAt(long timeMs) {
		return timeMs >= startMs && timeMs < endMs;
	}
}
