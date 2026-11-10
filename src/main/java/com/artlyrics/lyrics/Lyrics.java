package com.artlyrics.lyrics;

import java.util.List;

/**
 * Time-synced lyrics for one song.
 *
 * @param lines      lines sorted by start time
 * @param wordSynced true when word timings come from the source; false when they were estimated from line timings
 * @param source     a short human readable description of where the lyrics came from
 */
public record Lyrics(List<LyricLine> lines, boolean wordSynced, String source) {
	public Lyrics {
		lines = List.copyOf(lines);
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}

	/** Index of the last line that has started at {@code timeMs}, or -1 if the first line hasn't started yet. */
	public int lineIndexAt(long timeMs) {
		int lo = 0;
		int hi = lines.size() - 1;
		int result = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).startMs() <= timeMs) {
				result = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return result;
	}
}
