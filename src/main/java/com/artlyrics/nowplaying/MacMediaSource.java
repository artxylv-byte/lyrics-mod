package com.artlyrics.nowplaying;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * macOS: asks Spotify and Apple Music directly via JavaScript for Automation.
 * The first time, macOS asks the user to allow Minecraft (Java) to control those apps.
 */
final class MacMediaSource extends PollingMediaSource {
	// Application(bundleId) never launches the app; running() checks without starting it.
	private static final String SCRIPT = """
			function info(id, kind) {
			  try {
			    var app = Application(id);
			    if (!app.running()) return null;
			    var st = app.playerState();
			    if (st !== 'playing' && st !== 'paused') return null;
			    var t = app.currentTrack;
			    var dur = Number(t.duration()) || 0;
			    return { player: kind, title: t.name(), artist: t.artist(), album: t.album(),
			             durMs: kind === 'Spotify' ? Math.round(dur) : Math.round(dur * 1000),
			             posMs: Math.round((Number(app.playerPosition()) || 0) * 1000),
			             playing: st === 'playing' };
			  } catch (e) { return null; }
			}
			JSON.stringify([info('com.spotify.client', 'Spotify'), info('com.apple.Music', 'Music')].filter(function (x) { return x; }));
			""";

	MacMediaSource() {
		super("Artlyrics-osascript", 1000);
	}

	@Override
	protected @Nullable NowPlaying poll() throws Exception {
		String out = run(List.of("osascript", "-l", "JavaScript", "-e", SCRIPT), 4000).strip();
		long now = System.nanoTime();
		problem = null;
		if (out.isEmpty() || !out.startsWith("[")) {
			return null;
		}
		List<NowPlaying> players = new ArrayList<>();
		for (JsonElement el : JsonParser.parseString(out).getAsJsonArray()) {
			JsonObject o = el.getAsJsonObject();
			players.add(new NowPlaying(
					str(o, "player"), str(o, "title"), str(o, "artist"), str(o, "album"),
					o.has("durMs") ? o.get("durMs").getAsLong() : 0,
					o.has("posMs") ? o.get("posMs").getAsLong() : 0,
					o.has("playing") && o.get("playing").getAsBoolean(),
					now));
		}
		return choose(players, preferred);
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}
}
