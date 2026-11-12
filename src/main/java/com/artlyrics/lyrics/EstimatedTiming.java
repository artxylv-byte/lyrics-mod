package com.artlyrics.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gives lyrics without timestamps a best-guess timing, so songs that only have plain lyrics somewhere still show
 * something. Lines are spread over the song by how long they are to sing (roughly, their syllables), with a pause
 * between verses, an instrumental intro at the start and an outro at the end. It can't know where the singer
 * really is, so the result is approximate; the per-song timing keys help line it up.
 */
public final class EstimatedTiming {
	/** "[Chorus]", "(Verse 2)", "[Refrain: Artist]" and similar section labels, which aren't sung. */
	private static final Pattern SECTION = Pattern.compile("^\\s*[\\[(][^\\])]{1,40}[\\])]\\s*:?\\s*$");
	private static final Pattern VOWELS = Pattern.compile(
			"[aeiouyàáâãäåāăąæèéêëēėęěìíîïīįıòóôõöøōőœùúûüūůűųÿýаеёиоуыэюяіїєѣ]+", Pattern.CASE_INSENSITIVE);
	/** Used when the player doesn't say how long the song is. */
	static final long ASSUMED_DURATION_MS = 210_000;

	private EstimatedTiming() {
	}

	public static Lyrics build(String plain, long songDurationMs, String source) {
		List<String> items = new ArrayList<>(); // lines, with null for a break between verses
		for (String raw : plain.split("\\r?\\n")) {
			String line = raw.strip();
			if (line.isEmpty() || SECTION.matcher(line).matches()) {
				if (!items.isEmpty() && items.get(items.size() - 1) != null) {
					items.add(null);
				}
				continue;
			}
			items.add(line);
		}
		while (!items.isEmpty() && items.get(items.size() - 1) == null) {
			items.remove(items.size() - 1);
		}
		long duration = songDurationMs > 0 ? songDurationMs : ASSUMED_DURATION_MS;
		if (items.isEmpty()) {
			return new Lyrics(List.of(), false, source, true);
		}

		double[] weight = new double[items.size()];
		double total = 0;
		int lines = 0;
		for (int i = 0; i < items.size(); i++) {
			String line = items.get(i);
			weight[i] = line == null ? 4.0 : syllables(line) + 1.5;
			total += weight[i];
			if (line != null) {
				lines++;
			}
		}

		long intro = clamp(Math.round(duration * 0.08), 4000, 20000);
		long outro = clamp(Math.round(duration * 0.07), 3000, 15000);
		if (duration - intro - outro < lines * 1500L) {
			intro = Math.min(intro, 1500);
			outro = Math.min(outro, 1500);
		}
		double span = Math.max(lines * 800.0, duration - intro - outro);
		double msPerWeight = span / total;

		List<LineAssembler.RawLine> raw = new ArrayList<>();
		double t = intro;
		for (int i = 0; i < items.size(); i++) {
			String line = items.get(i);
			if (line != null) {
				long start = Math.round(t);
				// Leave a small breath before the next line: the line ends a bit before its slot does.
				long end = start + Math.round(weight[i] * msPerWeight * 0.85);
				raw.add(new LineAssembler.RawLine(start, end, line, List.of()));
			}
			t += weight[i] * msPerWeight;
		}
		return new Lyrics(LineAssembler.assemble(raw, duration), false, source, true);
	}

	/** Roughly how many syllables a line has (vowel groups; one per character for Chinese, Japanese and Korean). */
	static int syllables(String line) {
		int count = 0;
		int cjk = 0;
		int letters = 0;
		for (int i = 0; i < line.length(); ) {
			int cp = line.codePointAt(i);
			i += Character.charCount(cp);
			if (Character.isIdeographic(cp) || Character.UnicodeScript.of(cp) == Character.UnicodeScript.HIRAGANA
					|| Character.UnicodeScript.of(cp) == Character.UnicodeScript.KATAKANA
					|| Character.UnicodeScript.of(cp) == Character.UnicodeScript.HANGUL) {
				cjk++;
			} else if (Character.isLetter(cp)) {
				letters++;
			}
		}
		Matcher m = VOWELS.matcher(line);
		while (m.find()) {
			count++;
		}
		if (count == 0 && letters > 0) {
			count = Math.max(1, letters / 3);
		}
		return Math.max(1, count + cjk);
	}

	private static long clamp(long v, long min, long max) {
		return Math.max(min, Math.min(max, v));
	}
}
