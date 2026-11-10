package com.artlyrics.lyrics;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the word-synced "lyricsfile" document that LRCLIB returns for tracks with {@code hasWordSync}.
 *
 * <p>The document is YAML, but only a small, regular subset is used, so this is a purpose-built reader
 * rather than a general YAML parser (Minecraft doesn't ship one):
 * <pre>
 * lines:
 * - text: Some line
 *   start_ms: 3570
 *   end_ms: 6165
 *   words:
 *   - text: 'Some '
 *     start_ms: 3635
 * </pre>
 * Indentation is detected rather than assumed, scalars may be plain, single- or double-quoted,
 * and quoted scalars may continue over several lines. Anything unrecognised is skipped.
 */
public final class LyricsFileParser {
	private LyricsFileParser() {
	}

	public static Lyrics parse(String document, long songDurationMs, String source) {
		List<LineAssembler.RawLine> lines = parseRaw(document);
		boolean wordSynced = lines.stream().anyMatch(l -> !l.words().isEmpty());
		return new Lyrics(LineAssembler.assemble(lines, songDurationMs), wordSynced, source);
	}

	static List<LineAssembler.RawLine> parseRaw(String document) {
		List<LineAssembler.RawLine> result = new ArrayList<>();
		String[] rows = document.split("\\r?\\n");

		boolean inLines = false;
		int lineItemIndent = -1;
		int wordItemIndent = -1;
		boolean inWords = false;

		LineBuilder line = null;
		WordBuilder word = null;

		for (int r = 0; r < rows.length; r++) {
			String row = rows[r];
			if (row.isBlank() || row.stripLeading().startsWith("#")) {
				continue;
			}
			int indent = indentOf(row);
			String content = row.substring(indent);

			if (indent == 0 && !content.startsWith("-")) {
				// Top-level key: only the "lines" section matters.
				inLines = content.startsWith("lines:");
				inWords = false;
				continue;
			}
			if (!inLines) {
				continue;
			}

			boolean isItem = content.startsWith("- ") || content.equals("-");
			if (isItem) {
				int itemIndent = indent;
				if (lineItemIndent < 0) {
					lineItemIndent = itemIndent;
				}
				String rest = content.length() > 1 ? content.substring(2) : "";
				int keyIndent = itemIndent + 2;
				if (itemIndent == lineItemIndent) {
					finishWord(line, word);
					word = null;
					finishLine(result, line);
					line = new LineBuilder();
					inWords = false;
					wordItemIndent = -1;
					if (!rest.isBlank()) {
						r = applyKey(rest, rows, r, keyIndent, line, null);
					}
				} else if (inWords && line != null && itemIndent > lineItemIndent) {
					if (wordItemIndent < 0) {
						wordItemIndent = itemIndent;
					}
					finishWord(line, word);
					word = new WordBuilder();
					if (!rest.isBlank()) {
						r = applyKey(rest, rows, r, keyIndent, null, word);
					}
				}
				continue;
			}

			if (line == null) {
				continue;
			}
			if (word != null && wordItemIndent >= 0 && indent > wordItemIndent) {
				r = applyKey(content, rows, r, indent, null, word);
				continue;
			}
			if (indent > lineItemIndent) {
				finishWord(line, word);
				word = null;
				String key = keyOf(content);
				inWords = "words".equals(key);
				if (!inWords) {
					r = applyKey(content, rows, r, indent, line, null);
				}
			}
		}
		finishWord(line, word);
		finishLine(result, line);
		return result;
	}

	/** Applies one {@code key: value} pair, consuming continuation rows of multi-line quoted scalars. */
	private static int applyKey(String content, String[] rows, int r, int keyIndent, LineBuilder line, WordBuilder word) {
		String key = keyOf(content);
		if (key == null) {
			return r;
		}
		String value = content.substring(content.indexOf(':') + 1).strip();

		char quote = value.isEmpty() ? 0 : value.charAt(0);
		if ((quote == '\'' || quote == '"') && !isClosedQuoted(value)) {
			StringBuilder sb = new StringBuilder(value);
			while (r + 1 < rows.length && !isClosedQuoted(sb.toString())) {
				r++;
				sb.append(' ').append(rows[r].strip());
			}
			value = sb.toString();
		}

		switch (key) {
			case "text" -> {
				String text = unquote(value);
				if (line != null) {
					line.text = text;
				}
				if (word != null) {
					word.text = text;
				}
			}
			case "start_ms" -> {
				long v = parseLong(value);
				if (line != null) {
					line.start = v;
				}
				if (word != null) {
					word.start = v;
				}
			}
			case "end_ms" -> {
				long v = parseLong(value);
				if (line != null) {
					line.end = v;
				}
				if (word != null) {
					word.end = v;
				}
			}
			default -> {
			}
		}
		return r;
	}

	private static void finishWord(LineBuilder line, WordBuilder word) {
		if (line != null && word != null && word.text != null && word.start >= 0) {
			line.words.add(new LineAssembler.RawWord(word.text, word.start, word.end));
		}
	}

	private static void finishLine(List<LineAssembler.RawLine> out, LineBuilder line) {
		if (line == null) {
			return;
		}
		long start = line.start;
		if (start < 0 && !line.words.isEmpty()) {
			start = line.words.get(0).startMs();
		}
		if (start < 0) {
			return;
		}
		out.add(new LineAssembler.RawLine(start, line.end, line.text == null ? "" : line.text, line.words));
	}

	private static String keyOf(String content) {
		int colon = content.indexOf(':');
		if (colon <= 0) {
			return null;
		}
		String key = content.substring(0, colon).strip();
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (!(Character.isLetterOrDigit(c) || c == '_')) {
				return null;
			}
		}
		return key;
	}

	private static int indentOf(String row) {
		int i = 0;
		while (i < row.length() && row.charAt(i) == ' ') {
			i++;
		}
		return i;
	}

	private static boolean isClosedQuoted(String value) {
		if (value.length() < 2) {
			return false;
		}
		char q = value.charAt(0);
		if (q == '\'') {
			// Closed if it ends with a quote that isn't part of an escaped '' pair.
			int i = 1;
			while (i < value.length()) {
				if (value.charAt(i) == '\'') {
					if (i + 1 < value.length() && value.charAt(i + 1) == '\'') {
						i += 2;
						continue;
					}
					return i == value.length() - 1;
				}
				i++;
			}
			return false;
		}
		if (q == '"') {
			int i = 1;
			while (i < value.length()) {
				char c = value.charAt(i);
				if (c == '\\') {
					i += 2;
					continue;
				}
				if (c == '"') {
					return i == value.length() - 1;
				}
				i++;
			}
			return false;
		}
		return true;
	}

	static String unquote(String value) {
		if (value.length() >= 2 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
			return value.substring(1, value.length() - 1).replace("''", "'");
		}
		if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
			return unescapeDoubleQuoted(value.substring(1, value.length() - 1));
		}
		// Plain scalar: drop a trailing comment.
		int comment = value.indexOf(" #");
		return (comment >= 0 ? value.substring(0, comment) : value).strip();
	}

	private static String unescapeDoubleQuoted(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c != '\\' || i + 1 >= s.length()) {
				sb.append(c);
				continue;
			}
			char n = s.charAt(++i);
			switch (n) {
				case 'n' -> sb.append('\n');
				case 't' -> sb.append('\t');
				case 'r' -> sb.append('\r');
				case '0' -> sb.append('\0');
				case ' ' -> sb.append(' ');
				case '"' -> sb.append('"');
				case '/' -> sb.append('/');
				case '\\' -> sb.append('\\');
				case 'x' -> i = appendHex(s, i, 2, sb);
				case 'u' -> i = appendHex(s, i, 4, sb);
				case 'U' -> i = appendHex(s, i, 8, sb);
				default -> sb.append('\\').append(n);
			}
		}
		return sb.toString();
	}

	private static int appendHex(String s, int i, int digits, StringBuilder sb) {
		if (i + digits < s.length()) {
			try {
				int cp = Integer.parseInt(s.substring(i + 1, i + 1 + digits), 16);
				sb.appendCodePoint(cp);
				return i + digits;
			} catch (IllegalArgumentException ignored) {
				// not valid hex / not a valid code point: keep the escape as written
			}
		}
		sb.append('\\').append(s.charAt(i));
		return i;
	}

	private static long parseLong(String value) {
		try {
			return Long.parseLong(unquote(value).strip());
		} catch (NumberFormatException e) {
			try {
				return Math.round(Double.parseDouble(unquote(value).strip()));
			} catch (NumberFormatException e2) {
				return -1;
			}
		}
	}

	private static final class LineBuilder {
		String text;
		long start = -1;
		long end = -1;
		final List<LineAssembler.RawWord> words = new ArrayList<>();
	}

	private static final class WordBuilder {
		String text;
		long start = -1;
		long end = -1;
	}
}
