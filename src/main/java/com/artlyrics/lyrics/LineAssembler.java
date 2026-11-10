package com.artlyrics.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns raw parsed lines (from LRC or LRCLIB's word-synced format) into finished {@link LyricLine}s:
 * fills in missing end times, estimates word timings when the source only has line timings,
 * and decides how long each line stays on screen.
 */
public final class LineAssembler {
	/** How long a line lingers after its last word before fading, unless the next line starts sooner. */
	static final long LINGER_MS = 2500;
	/** Minimum time a line stays visible. */
	static final long MIN_VISIBLE_MS = 3000;
	/** Used for the last line when the song length is unknown. */
	static final long LAST_LINE_FALLBACK_MS = 6000;

	private LineAssembler() {
	}

	/** A word as read from the source. {@code endMs} is -1 when unknown. */
	public record RawWord(String text, long startMs, long endMs) {
	}

	/** A line as read from the source. {@code endMs} is -1 when unknown; {@code words} may be empty. */
	public record RawLine(long startMs, long endMs, String text, List<RawWord> words) {
	}

	public static List<LyricLine> assemble(List<RawLine> input, long songDurationMs) {
		List<RawLine> raw = new ArrayList<>(input);
		raw.sort(Comparator.comparingLong(RawLine::startMs));

		List<LyricLine> out = new ArrayList<>(raw.size());
		for (int i = 0; i < raw.size(); i++) {
			RawLine line = raw.get(i);
			long start = Math.max(0, line.startMs());
			long nextStart;
			if (i + 1 < raw.size()) {
				nextStart = Math.max(start, raw.get(i + 1).startMs());
			} else if (songDurationMs > start) {
				nextStart = songDurationMs;
			} else {
				nextStart = start + LAST_LINE_FALLBACK_MS;
			}

			String text = line.text() == null ? "" : line.text().strip();
			List<LyricWord> words;
			if (!line.words().isEmpty()) {
				words = finishWords(line.words(), start, line.endMs(), nextStart);
				if (text.isEmpty()) {
					StringBuilder sb = new StringBuilder();
					for (LyricWord w : words) {
						sb.append(w.text());
					}
					text = sb.toString().strip();
				}
			} else if (!text.isEmpty()) {
				words = estimateWords(text, start, line.endMs(), nextStart);
			} else {
				words = List.of();
			}

			if (words.isEmpty()) {
				// Instrumental break / empty line: nothing to show.
				out.add(new LyricLine(start, start, start, "", List.of()));
				continue;
			}

			long end = Math.max(line.endMs(), words.get(words.size() - 1).endMs());
			long visibleUntil = Math.min(nextStart, Math.max(end + LINGER_MS, start + MIN_VISIBLE_MS));
			visibleUntil = Math.max(visibleUntil, Math.min(end, nextStart));
			out.add(new LyricLine(start, end, visibleUntil, text, words));
		}
		return out;
	}

	private static List<LyricWord> finishWords(List<RawWord> raw, long lineStart, long lineEnd, long nextStart) {
		List<RawWord> sorted = new ArrayList<>(raw);
		sorted.sort(Comparator.comparingLong(RawWord::startMs));
		List<LyricWord> words = new ArrayList<>(sorted.size());
		for (int i = 0; i < sorted.size(); i++) {
			RawWord w = sorted.get(i);
			long start = Math.max(lineStart, w.startMs());
			long end;
			if (w.endMs() > start) {
				end = w.endMs();
			} else if (i + 1 < sorted.size()) {
				end = Math.max(start, sorted.get(i + 1).startMs());
			} else if (lineEnd > start) {
				end = lineEnd;
			} else {
				long guess = start + clamp(codePoints(w.text().strip()) * 90L, 250, 1200);
				end = Math.max(start, Math.min(guess, nextStart));
			}
			words.add(new LyricWord(w.text(), start, end));
		}
		return words;
	}

	/**
	 * Spreads a line's words over the time it is probably being sung, weighting longer words more.
	 * Used when the source only has line-level timestamps.
	 */
	static List<LyricWord> estimateWords(String text, long start, long knownEnd, long nextStart) {
		List<String> tokens = tokenize(text);
		if (tokens.isEmpty()) {
			return List.of();
		}
		long gap = Math.max(1, nextStart - start);
		long window;
		if (knownEnd > start) {
			window = knownEnd - start;
		} else {
			int chars = 0;
			for (String t : tokens) {
				chars += codePoints(t.strip());
			}
			long natural = Math.max(600, tokens.size() * 300L + chars * 25L);
			window = Math.min(natural, (long) (gap * 0.9));
			window = Math.max(window, Math.min(gap, 300));
		}

		double[] weights = new double[tokens.size()];
		double total = 0;
		for (int i = 0; i < tokens.size(); i++) {
			weights[i] = codePoints(tokens.get(i).strip()) + 2;
			total += weights[i];
		}

		List<LyricWord> words = new ArrayList<>(tokens.size());
		double acc = 0;
		for (int i = 0; i < tokens.size(); i++) {
			long wStart = start + Math.round(window * (acc / total));
			acc += weights[i];
			long wEnd = start + Math.round(window * (acc / total));
			words.add(new LyricWord(tokens.get(i), wStart, Math.max(wStart, wEnd)));
		}
		return words;
	}

	/**
	 * Splits a line into display tokens. Each token keeps a trailing space when it is followed by one.
	 * Runs of CJK characters (which have no spaces) are split per character so they can still animate.
	 */
	static List<String> tokenize(String text) {
		String[] parts = text.strip().split("\\s+");
		List<String> tokens = new ArrayList<>();
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i];
			if (part.isEmpty()) {
				continue;
			}
			boolean last = i == parts.length - 1;
			if (part.codePointCount(0, part.length()) > 1 && isAllCjk(part)) {
				int[] cps = part.codePoints().toArray();
				for (int j = 0; j < cps.length; j++) {
					String ch = new String(cps, j, 1);
					tokens.add(j == cps.length - 1 && !last ? ch + " " : ch);
				}
			} else {
				tokens.add(last ? part : part + " ");
			}
		}
		return tokens;
	}

	private static boolean isAllCjk(String s) {
		return s.codePoints().allMatch(cp -> {
			Character.UnicodeScript script = Character.UnicodeScript.of(cp);
			return script == Character.UnicodeScript.HAN
					|| script == Character.UnicodeScript.HIRAGANA
					|| script == Character.UnicodeScript.KATAKANA
					|| (cp >= 0x3000 && cp <= 0x303F) // CJK punctuation
					|| (cp >= 0xFF00 && cp <= 0xFFEF); // full-width forms
		});
	}

	private static int codePoints(String s) {
		return s.codePointCount(0, s.length());
	}

	private static long clamp(long v, long min, long max) {
		return Math.max(min, Math.min(max, v));
	}
}
