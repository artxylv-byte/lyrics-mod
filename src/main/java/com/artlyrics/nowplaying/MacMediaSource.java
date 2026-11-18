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
			    var art = '';
			    try { if (kind === 'Spotify') art = t.artworkUrl() || ''; } catch (e) {}
			    return { player: kind, title: t.name(), artist: t.artist(), album: t.album(),
			             durMs: kind === 'Spotify' ? Math.round(dur) : Math.round(dur * 1000),
			             posMs: Math.round((Number(app.playerPosition()) || 0) * 1000),
			             playing: st === 'playing', art: art };
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
					now, str(o, "art")));
		}
		return choose(players, preferred);
	}

	@Override
	public boolean send(MediaCommand command) {
		NowPlaying np = current;
		String id = np != null && np.player().equals("Music") ? "com.apple.Music" : "com.spotify.client";
		boolean music = id.equals("com.apple.Music");
		String call = switch (command) {
			case PLAY_PAUSE -> "playpause()";
			case NEXT -> "nextTrack()";
			case PREVIOUS -> music ? "backTrack()" : "previousTrack()";
		};
		String script = "var a = Application('" + id + "'); if (a.running()) a." + call + ";";
		try {
			new ProcessBuilder("osascript", "-l", "JavaScript", "-e", script)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			return true;
		} catch (java.io.IOException e) {
			return false;
		}
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}
}
