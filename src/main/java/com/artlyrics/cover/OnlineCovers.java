package com.artlyrics.cover;

import com.artlyrics.lyrics.TextMatch;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

/**
 * Finds a cover picture online when the music player doesn't share one, using Apple's public iTunes search (no
 * account or key needed). Only a result with the same title and artist is used.
 */
final class OnlineCovers {
	static final String SEARCH = "https://itunes.apple.com/search?media=music&entity=song&limit=15&term=";

	private OnlineCovers() {
	}

	/** The cover address for this song from an iTunes search answer, or null. */
	static @Nullable String pickArtwork(String json, String title, String artist) {
		JsonElement root = JsonParser.parseString(json);
		if (!root.isJsonObject() || !root.getAsJsonObject().has("results")) {
			return null;
		}
		String fallback = null;
		for (JsonElement el : root.getAsJsonObject().getAsJsonArray("results")) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject o = el.getAsJsonObject();
			String art = str(o, "artworkUrl100");
			if (art.isEmpty() || !TextMatch.artistMatches(artist, str(o, "artistName"))) {
				continue;
			}
			// Ask for a bigger picture than the 100 × 100 thumbnail the search names.
			String big = art.replace("100x100bb", "300x300bb");
			if (TextMatch.sameTitle(TextMatch.withoutArtistPrefix(str(o, "trackName"), artist), title)) {
				return big;
			}
			if (fallback == null && TextMatch.similarTitle(str(o, "trackName"), title)) {
				fallback = big;
			}
		}
		return fallback;
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}
}
