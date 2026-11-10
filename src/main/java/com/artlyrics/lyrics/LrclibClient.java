package com.artlyrics.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Looks up time-synced lyrics on <a href="https://lrclib.net">LRCLIB</a>, a free lyrics database with no API key.
 *
 * <p>Strategy: an exact {@code /api/get} match when the song length is known, then {@code /api/search}
 * with the original metadata and a few cleaned-up variants. Word-synced lyrics are preferred over
 * line-synced ones, and results whose length differs a lot from the playing track are skipped.
 */
public final class LrclibClient {
	private static final String BASE = "https://lrclib.net/api";
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	/** Results whose length differs by more than this are probably a different version of the song. */
	private static final long MAX_DURATION_DIFF_MS = 15_000;
	private static final int MAX_SEARCHES = 4;

	private final HttpClient http;
	private final String userAgent;

	public LrclibClient(String userAgent) {
		this.userAgent = userAgent;
		this.http = HttpClient.newBuilder()
				.connectTimeout(TIMEOUT)
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	public LyricsResult lookup(TrackQuery track) throws InterruptedException {
		if (!track.isUsable()) {
			return LyricsResult.of(LyricsResult.Status.NOT_FOUND, "No track title");
		}
		try {
			LyricsResult fallback = null;

			if (track.durationMs() > 0 && !track.artist().isBlank()) {
				JsonObject exact = getExact(track);
				if (exact != null) {
					LyricsResult r = toResult(exact, track.durationMs());
					if (isFinal(r)) {
						return r;
					}
					fallback = r;
				}
			}

			List<TrackQuery> queries = new ArrayList<>();
			queries.add(track);
			queries.addAll(track.fallbacks());
			int searches = 0;
			for (TrackQuery q : queries) {
				if (searches++ >= MAX_SEARCHES) {
					break;
				}
				JsonObject pick = pickBest(search(q), track);
				if (pick == null) {
					continue;
				}
				LyricsResult r = toResult(pick, track.durationMs());
				if (isFinal(r)) {
					return r;
				}
				if (fallback == null || fallback.status() == LyricsResult.Status.NOT_FOUND) {
					fallback = r;
				}
			}
			return fallback != null ? fallback : LyricsResult.of(LyricsResult.Status.NOT_FOUND, "No lyrics on LRCLIB");
		} catch (IOException | JsonParseException | IllegalStateException e) {
			return LyricsResult.of(LyricsResult.Status.ERROR, e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static boolean isFinal(LyricsResult r) {
		return r.status() == LyricsResult.Status.FOUND || r.status() == LyricsResult.Status.INSTRUMENTAL;
	}

	private @Nullable JsonObject getExact(TrackQuery track) throws IOException, InterruptedException {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("track_name", track.title());
		params.put("artist_name", track.artist());
		if (!track.album().isBlank()) {
			params.put("album_name", track.album());
		}
		params.put("duration", Long.toString(Math.round(track.durationMs() / 1000.0)));
		JsonElement json = request("/get", params);
		return json != null && json.isJsonObject() ? json.getAsJsonObject() : null;
	}

	private JsonArray search(TrackQuery q) throws IOException, InterruptedException {
		Map<String, String> params = new LinkedHashMap<>();
		if (q.artist().isBlank()) {
			params.put("q", q.title());
		} else {
			params.put("track_name", q.title());
			params.put("artist_name", q.artist());
		}
		JsonElement json = request("/search", params);
		return json != null && json.isJsonArray() ? json.getAsJsonArray() : new JsonArray();
	}

	private @Nullable JsonElement request(String path, Map<String, String> params) throws IOException, InterruptedException {
		StringJoiner query = new StringJoiner("&");
		params.forEach((k, v) -> query.add(k + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
		HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path + "?" + query))
				.timeout(TIMEOUT)
				.header("User-Agent", userAgent)
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (res.statusCode() == 404) {
			return null;
		}
		if (res.statusCode() != 200) {
			throw new IOException("LRCLIB returned HTTP " + res.statusCode());
		}
		return JsonParser.parseString(res.body());
	}

	/** Chooses the most useful search result for the playing track, or null if none fits. */
	static @Nullable JsonObject pickBest(JsonArray results, TrackQuery track) {
		JsonObject best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		String wantTitle = TrackQuery.cleanTitle(track.title()).toLowerCase(Locale.ROOT);
		for (JsonElement el : results) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject o = el.getAsJsonObject();
			double score = 0;
			if (track.durationMs() > 0) {
				double dur = number(o, "duration");
				if (dur > 0) {
					long diff = Math.abs(Math.round(dur * 1000) - track.durationMs());
					if (diff > MAX_DURATION_DIFF_MS) {
						continue;
					}
					score -= diff / 100.0;
				}
			}
			if (!string(o, "syncedLyrics").isBlank()) {
				score += 1000;
			} else if (bool(o, "instrumental")) {
				score += 300;
			} else if (!string(o, "plainLyrics").isBlank()) {
				score += 100;
			}
			if (bool(o, "hasWordSync") && !string(o, "lyricsfile").isBlank()) {
				score += 200;
			}
			if (TrackQuery.cleanTitle(string(o, "trackName")).toLowerCase(Locale.ROOT).equals(wantTitle)) {
				score += 50;
			}
			if (score > bestScore) {
				bestScore = score;
				best = o;
			}
		}
		return best;
	}

	static LyricsResult toResult(JsonObject o, long playingDurationMs) {
		long durationMs = playingDurationMs > 0 ? playingDurationMs : Math.round(number(o, "duration") * 1000);
		String name = string(o, "artistName") + " – " + string(o, "trackName");

		if (bool(o, "instrumental")) {
			return LyricsResult.of(LyricsResult.Status.INSTRUMENTAL, name);
		}

		String lyricsFile = string(o, "lyricsfile");
		if (bool(o, "hasWordSync") && !lyricsFile.isBlank()) {
			try {
				Lyrics lyrics = LyricsFileParser.parse(lyricsFile, durationMs, "LRCLIB (word-synced)");
				if (hasText(lyrics)) {
					return LyricsResult.found(lyrics);
				}
			} catch (RuntimeException ignored) {
				// fall back to the line-synced lyrics below
			}
		}

		String synced = string(o, "syncedLyrics");
		if (!synced.isBlank()) {
			Lyrics lyrics = LrcParser.parse(synced, durationMs, "LRCLIB");
			if (hasText(lyrics)) {
				return LyricsResult.found(lyrics);
			}
		}

		if (!string(o, "plainLyrics").isBlank()) {
			return LyricsResult.of(LyricsResult.Status.UNSYNCED_ONLY, name);
		}
		return LyricsResult.of(LyricsResult.Status.NOT_FOUND, name);
	}

	private static boolean hasText(Lyrics lyrics) {
		return lyrics.lines().stream().anyMatch(l -> !l.isBlank());
	}

	private static String string(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private static double number(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsDouble() : 0;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private static boolean bool(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
	}
}
