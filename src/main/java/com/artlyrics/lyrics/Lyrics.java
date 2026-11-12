package com.artlyrics.lyrics;

import java.util.List;

/**
 * Time-synced lyrics for one song.
 *
 * @param lines      lines sorted by start time
 * @param wordSynced true when word timings come from the source; false when they were estimated from line timings
 * @param source     a short human readable description of where the lyrics came from
 * @param estimated  true when the source had no timestamps at all and line timings were guessed from the song length
 */
public record Lyrics(List<LyricLine> lines, boolean wordSynced, String source, boolean estimated) {
	public Lyrics {
		lines = List.copyOf(lines);
	}

	public Lyrics(List<LyricLine> lines, boolean wordSynced, String source) {
		this(lines, wordSynced, source, false);
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}

	/**
	 * Song time at which line {@code index} should start leaving: {@code lingerMs} after its last word (but shown
	 * for at least 1.5 seconds), and never later than the next line's start.
	 */
	public long visibleEnd(int index, long lingerMs) {
		LyricLine line = lines.get(index);
		long end = Math.max(line.endMs() + lingerMs, line.startMs() + 1500);
		if (index + 1 < lines.size()) {
			end = Math.min(end, Math.max(line.startMs(), lines.get(index + 1).startMs()));
		}
		return end;
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
