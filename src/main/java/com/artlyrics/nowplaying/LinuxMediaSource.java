package com.artlyrics.nowplaying;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Linux: asks MPRIS-compatible players (Spotify, browsers, VLC, ...) through {@code playerctl}.
 */
final class LinuxMediaSource extends PollingMediaSource {
	private static final String FORMAT = String.join("\t",
			"{{playerName}}", "{{status}}", "{{position}}", "{{mpris:length}}", "{{xesam:title}}", "{{artist}}", "{{xesam:album}}",
			"{{mpris:artUrl}}");

	LinuxMediaSource() {
		super("Artlyrics-playerctl", 750);
	}

	@Override
	protected @Nullable NowPlaying poll() throws Exception {
		String out = run(List.of("playerctl", "--all-players", "metadata", "--format", FORMAT), 3000);
		long now = System.nanoTime();
		problem = null;

		List<NowPlaying> players = new ArrayList<>();
		java.util.Map<String, String> names = new java.util.HashMap<>();
		for (String row : out.split("\\r?\\n")) {
			String[] f = row.split("\t", -1);
			if (f.length < 7 || f[4].isBlank()) {
				continue;
			}
			String status = f[1].strip().toLowerCase(Locale.ROOT);
			if (!status.equals("playing") && !status.equals("paused")) {
				continue;
			}
			names.put(prettyName(f[0]), f[0].strip());
			players.add(new NowPlaying(
					prettyName(f[0]), f[4], f[5], f[6],
					micros(f[3]) / 1000, micros(f[2]) / 1000,
					status.equals("playing"), now, f.length > 7 ? f[7].strip() : ""));
		}
		NowPlaying chosen = choose(players, preferred);
		playerName = chosen == null ? null : names.get(chosen.player());
		return chosen;
	}

	private volatile @Nullable String playerName;

	@Override
	public boolean send(MediaCommand command) {
		List<String> cmd = new ArrayList<>(List.of("playerctl"));
		String name = playerName;
		if (name != null) {
			cmd.add("--player=" + name);
		}
		cmd.add(switch (command) {
			case PLAY_PAUSE -> "play-pause";
			case NEXT -> "next";
			case PREVIOUS -> "previous";
		});
		try {
			new ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static long micros(String s) {
		try {
			return Math.max(0, (long) Double.parseDouble(s.strip()));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String prettyName(String player) {
		String p = player.strip();
		int dot = p.indexOf('.');
		if (dot > 0) {
			p = p.substring(0, dot); // "chromium.instance1234" -> "chromium"
		}
		return p.isEmpty() ? "Player" : Character.toUpperCase(p.charAt(0)) + p.substring(1);
	}

	@Override
	protected String describe(Exception e) {
		if (e instanceof IOException && String.valueOf(e.getMessage()).contains("Cannot run program")) {
			return "Install 'playerctl' so Artlyrics can see what's playing";
		}
		return super.describe(e);
	}
}
