package com.artlyrics.lyrics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What we know about the playing track, used to look up lyrics.
 *
 * @param durationMs 0 when unknown
 */
public record TrackQuery(String title, String artist, String album, long durationMs) {
	private static final Pattern BRACKETED_JUNK = Pattern.compile(
			"\\s*[(\\[][^)\\]]*(official|video|audio|lyric|visuali[sz]er|remaster|explicit|clean|hd|hq|4k|mv|m/v)[^)\\]]*[)\\]]",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern DASH_SUFFIX = Pattern.compile(
			"\\s+-\\s+(\\d{4}\\s+)?(remaster(ed)?|mono|stereo|single version|radio edit|live|bonus track)\\b.*$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern FEAT = Pattern.compile("\\s*[(\\[]?\\s*(feat\\.?|ft\\.?|featuring)\\s+[^)\\]]*[)\\]]?", Pattern.CASE_INSENSITIVE);
	private static final Pattern ARTIST_SPLIT = Pattern.compile("\\s*(,|&|;|/|\\bx\\b|\\bfeat\\.?|\\bft\\.?|\\band\\b)\\s*", Pattern.CASE_INSENSITIVE);

	public TrackQuery {
		title = title == null ? "" : title.strip();
		artist = artist == null ? "" : artist.strip();
		album = album == null ? "" : album.strip();
	}

	/** A stable key for caching: identical tracks map to the same key. */
	public String cacheKey() {
		return (artist + "\u0000" + title + "\u0000" + (durationMs / 2000)).toLowerCase(Locale.ROOT);
	}

	public boolean isUsable() {
		return !title.isBlank();
	}

	/**
	 * Alternative (title, artist) pairs to try when the exact metadata doesn't match anything, from most to least
	 * specific: cleaned-up titles, the first credited artist only, and "Artist - Title" video titles.
	 */
	public List<TrackQuery> fallbacks() {
		Set<TrackQuery> out = new LinkedHashSet<>();
		String cleanTitle = cleanTitle(title);
		String cleanArtist = cleanArtist(artist);
		String firstArtist = firstArtist(cleanArtist);

		out.add(new TrackQuery(cleanTitle, cleanArtist, album, durationMs));
		out.add(new TrackQuery(cleanTitle, firstArtist, album, durationMs));

		// Browser / YouTube style: title "Artist - Song", artist is a channel name.
		int dash = title.indexOf(" - ");
		if (dash > 0) {
			String a = title.substring(0, dash).strip();
			String t = cleanTitle(title.substring(dash + 3));
			if (!t.isBlank()) {
				out.add(new TrackQuery(t, a, "", durationMs));
				out.add(new TrackQuery(t, firstArtist(a), "", durationMs));
			}
		}
		out.remove(this);
		List<TrackQuery> list = new ArrayList<>();
		for (TrackQuery q : out) {
			if (q.isUsable()) {
				list.add(q);
			}
		}
		return list;
	}

	static String cleanTitle(String title) {
		String t = BRACKETED_JUNK.matcher(title).replaceAll("");
		t = FEAT.matcher(t).replaceAll("");
		t = DASH_SUFFIX.matcher(t).replaceAll("");
		return t.strip();
	}

	static String cleanArtist(String artist) {
		String a = artist.strip();
		if (a.endsWith(" - Topic")) {
			a = a.substring(0, a.length() - " - Topic".length());
		}
		if (a.endsWith("VEVO") && a.length() > 4) {
			a = a.substring(0, a.length() - 4);
		}
		return a.strip();
	}

	static String firstArtist(String artist) {
		String[] parts = ARTIST_SPLIT.split(artist, 2);
		return parts.length > 0 && !parts[0].isBlank() ? parts[0].strip() : artist;
	}
}
