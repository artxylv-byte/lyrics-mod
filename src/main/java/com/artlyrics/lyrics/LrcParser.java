package com.artlyrics.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LRC lyrics: {@code [mm:ss.xx]line}, several timestamps per line, the {@code [offset:]} tag,
 * and "enhanced" LRC word timestamps ({@code <mm:ss.xx>word}).
 */
public final class LrcParser {
	private static final Pattern LINE_TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
	private static final Pattern OFFSET_TAG = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);
	private static final Pattern WORD_TAG = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");

	private LrcParser() {
	}

	public static Lyrics parse(String lrc, long songDurationMs, String source) {
		List<LineAssembler.RawLine> raw = new ArrayList<>();
		long offset = 0;
		boolean anyWordTags = false;

		for (String rawLine : lrc.split("\\r?\\n")) {
			String line = rawLine.strip();
			if (line.isEmpty()) {
				continue;
			}
			Matcher offsetMatcher = OFFSET_TAG.matcher(line);
			if (offsetMatcher.matches()) {
				offset = Long.parseLong(offsetMatcher.group(1));
				continue;
			}

			List<Long> times = new ArrayList<>();
			Matcher m = LINE_TAG.matcher(line);
			int pos = 0;
			while (m.find(pos) && m.start() == pos) {
				times.add(toMillis(m.group(1), m.group(2), m.group(3)));
				pos = m.end();
			}
			if (times.isEmpty()) {
				continue; // metadata tag ([ar:], [ti:], ...) or garbage
			}
			String text = line.substring(pos);

			List<LineAssembler.RawWord> words = List.of();
			if (WORD_TAG.matcher(text).find()) {
				words = parseWordTags(text);
				anyWordTags |= !words.isEmpty();
				text = WORD_TAG.matcher(text).replaceAll("");
			}
			text = text.strip();

			for (long t : times) {
				List<LineAssembler.RawWord> shifted = words;
				if (times.size() > 1 && !words.isEmpty()) {
					// Repeated lines with word tags: shift the words to each repetition.
					long delta = t - times.get(0);
					shifted = words.stream()
							.map(w -> new LineAssembler.RawWord(w.text(), w.startMs() + delta, w.endMs() < 0 ? -1 : w.endMs() + delta))
							.toList();
				}
				raw.add(new LineAssembler.RawLine(t, -1, text, shifted));
			}
		}

		if (offset != 0) {
			// A positive [offset:] means the lyrics should appear sooner.
			final long shift = -offset;
			raw.replaceAll(l -> new LineAssembler.RawLine(
					Math.max(0, l.startMs() + shift),
					-1,
					l.text(),
					l.words().stream()
							.map(w -> new LineAssembler.RawWord(w.text(), Math.max(0, w.startMs() + shift), w.endMs() < 0 ? -1 : w.endMs() + shift))
							.toList()));
		}

		return new Lyrics(LineAssembler.assemble(raw, songDurationMs), anyWordTags, source);
	}

	private static List<LineAssembler.RawWord> parseWordTags(String text) {
		List<LineAssembler.RawWord> words = new ArrayList<>();
		Matcher m = WORD_TAG.matcher(text);
		long pendingStart = -1;
		int textStart = -1;
		while (m.find()) {
			long t = toMillis(m.group(1), m.group(2), m.group(3));
			if (pendingStart >= 0) {
				String w = text.substring(textStart, m.start());
				if (!w.isBlank()) {
					words.add(new LineAssembler.RawWord(w, pendingStart, t));
				} else if (!words.isEmpty()) {
					// A closing timestamp after whitespace ends the previous word.
					LineAssembler.RawWord prev = words.remove(words.size() - 1);
					words.add(new LineAssembler.RawWord(prev.text() + w, prev.startMs(), t));
				}
			}
			pendingStart = t;
			textStart = m.end();
		}
		if (pendingStart >= 0 && textStart < text.length()) {
			String w = text.substring(textStart);
			if (!w.isBlank()) {
				words.add(new LineAssembler.RawWord(w, pendingStart, -1));
			}
		}
		// Normalise spacing: keep exactly one trailing space between words, none at the end.
		List<LineAssembler.RawWord> out = new ArrayList<>(words.size());
		for (int i = 0; i < words.size(); i++) {
			LineAssembler.RawWord w = words.get(i);
			String s = w.text().strip();
			if (i < words.size() - 1 && (Character.isWhitespace(w.text().charAt(w.text().length() - 1))
					|| Character.isWhitespace(words.get(i + 1).text().charAt(0)))) {
				s += " ";
			}
			out.add(new LineAssembler.RawWord(s, w.startMs(), w.endMs()));
		}
		return out;
	}

	static long toMillis(String min, String sec, String frac) {
		long ms = Long.parseLong(min) * 60_000L + Long.parseLong(sec) * 1000L;
		if (frac != null && !frac.isEmpty()) {
			switch (frac.length()) {
				case 1 -> ms += Long.parseLong(frac) * 100L;
				case 2 -> ms += Long.parseLong(frac) * 10L;
				default -> ms += Long.parseLong(frac.substring(0, 3));
			}
		}
		return ms;
	}
}
