package com.artlyrics.lyrics;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Loose comparison of song titles and artist names from different places: ignores case, accents ("Prāta Vētra"
 * matches "Prata Vetra"), punctuation, "feat." credits and tags like "(Remastered)".
 */
public final class TextMatch {
	private static final Pattern MARKS = Pattern.compile("\\p{M}+");
	private static final Pattern ARTIST_SPLIT = Pattern.compile("\\s*(,|&|;|/|\\+|\\bx\\b|\\bfeat\\.?|\\bft\\.?|\\band\\b|\\bwith\\b|\\bun\\b)\\s*",
			Pattern.CASE_INSENSITIVE);

	private TextMatch() {
	}

	/** Lower case, no accents, only letters and digits separated by single spaces. Keeps any script. */
	public static String normalize(String s) {
		if (s == null) {
			return "";
		}
		String t = Normalizer.normalize(s, Normalizer.Form.NFKD);
		t = MARKS.matcher(t).replaceAll("");
		t = t.toLowerCase(Locale.ROOT).replace("&", " and ").replace("ł", "l").replace("ø", "o").replace("ß", "ss");
		StringBuilder out = new StringBuilder(t.length());
		boolean space = false;
		for (int i = 0; i < t.length(); ) {
			int cp = t.codePointAt(i);
			i += Character.charCount(cp);
			if (Character.isLetterOrDigit(cp)) {
				if (space && !out.isEmpty()) {
					out.append(' ');
				}
				space = false;
				out.appendCodePoint(cp);
			} else {
				space = true;
			}
		}
		return out.toString();
	}

	/** A title reduced for comparing: tags and credits removed, then normalized. */
	public static String titleKey(String title) {
		String key = normalize(TrackQuery.cleanTitle(title == null ? "" : title));
		return key.isEmpty() ? normalize(title) : key;
	}

	/** True when both are the same song title (after cleaning). */
	public static boolean sameTitle(String a, String b) {
		String ka = titleKey(a);
		return !ka.isEmpty() && ka.equals(titleKey(b));
	}

	/**
	 * True when the titles are the same, or one is the other plus a little extra (e.g. "Song" and "Song Pt 2" don't
	 * match, but "Song" and "Song Radio Edit" do when the extra is short).
	 */
	public static boolean similarTitle(String a, String b) {
		String ka = titleKey(a);
		String kb = titleKey(b);
		if (ka.isEmpty() || kb.isEmpty()) {
			return false;
		}
		if (ka.equals(kb)) {
			return true;
		}
		String shorter = ka.length() <= kb.length() ? ka : kb;
		String longer = ka.length() <= kb.length() ? kb : ka;
		return shorter.length() >= 3 && longer.startsWith(shorter + " ") && shorter.length() >= longer.length() * 0.6;
	}

	/**
	 * A title as some sites store it, "Artist - Title (Official Audio)", without the artist part when that part is
	 * the given artist.
	 */
	public static String withoutArtistPrefix(String title, String artist) {
		if (title == null) {
			return "";
		}
		int dash = title.indexOf(" - ");
		if (dash > 0 && artist != null && !artist.isBlank() && artistMatches(title.substring(0, dash), artist)) {
			return title.substring(dash + 3);
		}
		return title;
	}

	/** The credited artists, normalized: "A feat. B & C" gives [a, b, c]. */
	public static List<String> artists(String artist) {
		List<String> out = new ArrayList<>();
		if (artist == null) {
			return out;
		}
		for (String part : ARTIST_SPLIT.split(TrackQuery.cleanArtist(artist))) {
			String n = normalize(part);
			if (!n.isEmpty()) {
				out.add(n);
			}
		}
		String whole = normalize(artist);
		if (!whole.isEmpty() && !out.contains(whole)) {
			out.add(whole);
		}
		return out;
	}

	/** True when any artist credited in one also appears in the other. */
	public static boolean artistMatches(String wanted, String found) {
		List<String> a = artists(wanted);
		List<String> b = artists(found);
		for (String x : a) {
			for (String y : b) {
				if (x.equals(y) || (x.length() >= 4 && y.length() >= 4 && (x.contains(y) || y.contains(x)))) {
					return true;
				}
			}
		}
		return false;
	}
}
