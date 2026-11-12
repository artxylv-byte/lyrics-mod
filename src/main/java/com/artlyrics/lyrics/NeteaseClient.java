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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Looks up synced lyrics on NetEase Cloud Music, a large music service whose public web API has time-synced lyrics
 * for many songs that LRCLIB doesn't have (pop, rock, K-pop, anime, game music, ...). Only results whose title and
 * artist clearly match the playing song are used.
 */
public final class NeteaseClient implements LyricsSource {
	private static final String BASE = "https://music.163.com/api";
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final long MAX_DURATION_DIFF_MS = 15_000;
	/** Credit lines NetEase puts at the top of lyrics ("作词 : ...", "Composer: ..."), which aren't sung. */
	private static final Pattern CREDIT = Pattern.compile(
			"^(\\[[^]]*])+\\s*(作词|作曲|编曲|制作人|作詞|編曲|词|曲|监制|混音|录音|和声|吉他|贝斯|鼓|弦乐|母带|出品|发行|Lyricist|Lyrics|Composer|Composed|Producer|Produced|Arranger|Arranged|Written|Mixed|Mastered|Vocals?)\\b?\\s*(by)?\\s*[:：].*$",
			Pattern.CASE_INSENSITIVE);
	private static final String INSTRUMENTAL = "纯音乐，请欣赏";

	private final HttpClient http;
	private final String userAgent;

	public NeteaseClient(String userAgent) {
		this.userAgent = userAgent;
		this.http = HttpClient.newBuilder()
				.connectTimeout(TIMEOUT)
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	@Override
	public String name() {
		return "NetEase Cloud Music";
	}

	@Override
	public LyricsResult lookup(TrackQuery track) throws InterruptedException {
		if (!track.isUsable()) {
			return LyricsResult.of(LyricsResult.Status.NOT_FOUND, "No track title");
		}
		try {
			Set<String> tried = new LinkedHashSet<>();
			List<TrackQuery> queries = new ArrayList<>();
			queries.add(track);
			queries.addAll(track.fallbacks());
			for (TrackQuery q : queries) {
				String title = TrackQuery.cleanTitle(q.title());
				String artist = TrackQuery.firstArtist(TrackQuery.cleanArtist(q.artist()));
				String text = (artist + " " + title).strip();
				if (text.isEmpty() || !tried.add(TextMatch.normalize(text)) || tried.size() > 3) {
					continue;
				}
				Song song = pickBest(search(text), q, track.durationMs());
				if (song == null) {
					continue;
				}
				LyricsResult r = lyrics(song, track.durationMs());
				if (r.status() != LyricsResult.Status.NOT_FOUND) {
					return r;
				}
			}
			return LyricsResult.of(LyricsResult.Status.NOT_FOUND, "No lyrics on NetEase Cloud Music");
		} catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException e) {
			return LyricsResult.of(LyricsResult.Status.ERROR, "NetEase: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	/** A search result. */
	record Song(long id, String name, String artists, long durationMs) {
	}

	private JsonObject get(String pathAndQuery) throws IOException, InterruptedException {
		HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + pathAndQuery))
				.timeout(TIMEOUT)
				.header("User-Agent", userAgent)
				.header("Referer", "https://music.163.com/")
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (res.statusCode() != 200) {
			throw new IOException("HTTP " + res.statusCode());
		}
		JsonElement json = JsonParser.parseString(res.body());
		if (!json.isJsonObject()) {
			throw new IOException("unexpected answer");
		}
		return json.getAsJsonObject();
	}

	private List<Song> search(String text) throws IOException, InterruptedException {
		JsonObject o = get("/search/get?type=1&limit=12&offset=0&s=" + URLEncoder.encode(text, StandardCharsets.UTF_8));
		return parseSearch(o);
	}

	static List<Song> parseSearch(JsonObject o) {
		List<Song> out = new ArrayList<>();
		JsonObject result = o.has("result") && o.get("result").isJsonObject() ? o.getAsJsonObject("result") : null;
		if (result == null || !result.has("songs") || !result.get("songs").isJsonArray()) {
			return out;
		}
		for (JsonElement el : result.getAsJsonArray("songs")) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject s = el.getAsJsonObject();
			StringBuilder artists = new StringBuilder();
			JsonElement ar = s.has("artists") ? s.get("artists") : s.get("ar");
			if (ar != null && ar.isJsonArray()) {
				for (JsonElement a : (JsonArray) ar) {
					if (a.isJsonObject() && a.getAsJsonObject().has("name") && a.getAsJsonObject().get("name").isJsonPrimitive()) {
						if (!artists.isEmpty()) {
							artists.append(", ");
						}
						artists.append(a.getAsJsonObject().get("name").getAsString());
					}
				}
			}
			long duration = s.has("duration") ? s.get("duration").getAsLong() : s.has("dt") ? s.get("dt").getAsLong() : 0;
			out.add(new Song(s.get("id").getAsLong(), s.has("name") ? s.get("name").getAsString() : "", artists.toString(), duration));
		}
		return out;
	}

	/** The result that is clearly this song: same title, a shared artist (when known) and a similar length. */
	static @Nullable Song pickBest(List<Song> songs, TrackQuery wanted, long durationMs) {
		Song best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (Song s : songs) {
			String title = TextMatch.withoutArtistPrefix(s.name(), s.artists());
			if (!TextMatch.similarTitle(title, wanted.title())) {
				continue;
			}
			boolean artistKnown = !wanted.artist().isBlank();
			if (artistKnown && !TextMatch.artistMatches(wanted.artist(), s.artists())) {
				continue;
			}
			double score = TextMatch.sameTitle(title, wanted.title()) ? 100 : 0;
			if (durationMs > 0 && s.durationMs() > 0) {
				long diff = Math.abs(s.durationMs() - durationMs);
				if (diff > MAX_DURATION_DIFF_MS) {
					continue;
				}
				score -= diff / 100.0;
			} else if (!artistKnown) {
				continue; // neither artist nor length to confirm it's the right song
			}
			if (score > bestScore) {
				bestScore = score;
				best = s;
			}
		}
		return best;
	}

	private LyricsResult lyrics(Song song, long durationMs) throws IOException, InterruptedException {
		JsonObject o = get("/song/lyric?lv=1&kv=1&tv=-1&id=" + song.id());
		return toResult(o, song, durationMs);
	}

	static LyricsResult toResult(JsonObject o, Song song, long durationMs) {
		String name = song.artists() + " – " + song.name();
		if (bool(o, "nolyric") || bool(o, "uncollected")) {
			return LyricsResult.of(LyricsResult.Status.NOT_FOUND, name);
		}
		String lrc = "";
		if (o.has("lrc") && o.get("lrc").isJsonObject()) {
			JsonElement l = o.getAsJsonObject("lrc").get("lyric");
			lrc = l != null && l.isJsonPrimitive() ? l.getAsString() : "";
		}
		if (lrc.isBlank()) {
			return LyricsResult.of(LyricsResult.Status.NOT_FOUND, name);
		}
		if (lrc.contains(INSTRUMENTAL)) {
			return LyricsResult.of(LyricsResult.Status.INSTRUMENTAL, name);
		}
		String cleaned = clean(lrc);
		long length = durationMs > 0 ? durationMs : song.durationMs();
		Lyrics lyrics = LrcParser.parse(cleaned, length, "NetEase Cloud Music");
		if (lyrics.lines().stream().anyMatch(l -> !l.isBlank())) {
			return LyricsResult.found(lyrics);
		}
		// No timestamps at all: it's plain text.
		String plain = cleaned.replaceAll("(?m)^(\\[[^]]*])+", "").strip();
		return plain.isEmpty() ? LyricsResult.of(LyricsResult.Status.NOT_FOUND, name) : LyricsResult.unsynced(plain, name);
	}

	/** Drops NetEase's credit lines and the JSON lines it sometimes mixes in. */
	static String clean(String lrc) {
		StringBuilder out = new StringBuilder();
		for (String line : lrc.split("\\r?\\n")) {
			String l = line.strip();
			if (l.startsWith("{") || CREDIT.matcher(l).matches()) {
				continue;
			}
			out.append(l).append('\n');
		}
		return out.toString();
	}

	private static boolean bool(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
	}
}
