package com.artlyrics.lyrics;

import java.util.List;

/**
 * One lyric line.
 *
 * @param startMs      song time when the line starts
 * @param endMs        song time when the last word has been sung
 * @param visibleUntil song time after which the line should fade away (never past the next line's start)
 * @param text         the full line text
 * @param words        the words of the line with their timings; empty for instrumental breaks
 */
public record LyricLine(long startMs, long endMs, long visibleUntil, String text, List<LyricWord> words) {
	public LyricLine {
		words = List.copyOf(words);
	}

	public boolean isBlank() {
		return words.isEmpty() || text.isBlank();
	}

	/** Index of the word being sung at {@code timeMs}, the last sung word if between words, or -1 before the first. */
	public int wordIndexAt(long timeMs) {
		int index = -1;
		for (int i = 0; i < words.size(); i++) {
			if (words.get(i).startMs() <= timeMs) {
				index = i;
			} else {
				break;
			}
		}
		return index;
	}
}
