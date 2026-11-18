package com.artlyrics.nowplaying;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Windows: runs a small PowerShell helper that reads the System Media Transport Controls, which Spotify,
 * browsers and most other players publish to. The helper stays running and prints a JSON line about
 * twice a second; it exits by itself when Minecraft does.
 */
final class WindowsMediaSource implements MediaSource {
	private static final Logger LOGGER = LoggerFactory.getLogger("Artlyrics");
	private static final String SCRIPT_RESOURCE = "/artlyrics/media-windows.ps1";
	private static final int MAX_QUICK_FAILURES = 5;

	private final Path scriptPath;
	private final Path coverDir;
	private volatile boolean running;
	private volatile String preferred = "spotify";
	private volatile @Nullable Process process;
	private volatile @Nullable java.io.Writer commands;
	private volatile @Nullable NowPlaying current;
	private volatile @Nullable String problem;
	private Thread thread;

	WindowsMediaSource(Path dataDir) {
		this.scriptPath = dataDir.resolve("media-windows.ps1");
		this.coverDir = dataDir.resolve("player-covers");
	}

	@Override
	public void start() {
		running = true;
		thread = new Thread(this::loop, "Artlyrics-SMTC");
		thread.setDaemon(true);
		thread.start();
	}

	private void loop() {
		int quickFailures = 0;
		while (running) {
			long startedAt = System.nanoTime();
			try {
				writeScript();
				runHelper();
			} catch (IOException e) {
				problem = "Couldn't start PowerShell: " + e.getMessage();
				LOGGER.warn("[Artlyrics] {}", problem);
			} catch (InterruptedException e) {
				return;
			}
			current = null;
			if (!running) {
				return;
			}
			boolean quick = System.nanoTime() - startedAt < 30_000_000_000L;
			quickFailures = quick ? quickFailures + 1 : 0;
			if (quickFailures >= MAX_QUICK_FAILURES) {
				if (problem == null) {
					problem = "Windows media helper keeps stopping";
				}
				LOGGER.warn("[Artlyrics] Giving up on the Windows media helper: {}", problem);
				return;
			}
			try {
				Thread.sleep(2000L * quickFailures + 500);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private void runHelper() throws IOException, InterruptedException {
		String prefer = preferred;
		Process p = new ProcessBuilder(List.of(
				"powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
				"-ExecutionPolicy", "Bypass",
				"-File", scriptPath.toAbsolutePath().toString(),
				"-ParentPid", Long.toString(ProcessHandle.current().pid()),
				"-Prefer", prefer.isBlank() ? "spotify" : prefer,
				"-CoverDir", coverDirArg()))
				.redirectErrorStream(true)
				.start();
		process = p;
		commands = new java.io.OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while (running && (line = reader.readLine()) != null) {
				handleLine(line.strip());
				if (!Objects.equals(prefer, preferred)) {
					break; // restart with the new preference
				}
			}
		} finally {
			commands = null;
			p.destroy();
			process = null;
		}
	}

	/** Where the helper saves cover pictures ("" if the folder can't be made, which turns covers off). */
	private String coverDirArg() {
		try {
			Files.createDirectories(coverDir);
			return coverDir.toAbsolutePath().toString();
		} catch (IOException e) {
			return "";
		}
	}

	private void handleLine(String line) {
		if (!line.startsWith("{")) {
			return; // PowerShell noise
		}
		long now = System.nanoTime();
		try {
			JsonObject o = JsonParser.parseString(line).getAsJsonObject();
			if (o.has("error")) {
				problem = o.get("error").getAsString();
				current = null;
				return;
			}
			problem = null;
			if (o.has("none")) {
				current = null;
				return;
			}
			int status = (int) num(o, "status");
			if (status != 4 && status != 5) { // 4 = playing, 5 = paused
				current = null;
				return;
			}
			boolean playing = status == 4;
			long pos = num(o, "posMs");
			long updated = num(o, "updatedMs");
			long nowMs = num(o, "nowMs");
			if (playing && updated > 0 && nowMs >= updated && nowMs - updated < 6 * 3_600_000L) {
				// Players only refresh the timeline now and then; account for the time since.
				pos += nowMs - updated;
			}
			current = new NowPlaying(playerName(str(o, "app")), str(o, "title"), str(o, "artist"), str(o, "album"),
					Math.max(0, num(o, "endMs")), pos, playing, now, coverUrl(str(o, "cover")));
		} catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
			// ignore malformed lines
		}
	}

	@Override
	public boolean send(MediaCommand command) {
		java.io.Writer w = commands;
		if (w == null) {
			return false;
		}
		String line = switch (command) {
			case PLAY_PAUSE -> "toggle";
			case NEXT -> "next";
			case PREVIOUS -> "previous";
		};
		try {
			synchronized (this) {
				w.write(line + "\n");
				w.flush();
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/** The helper's saved cover file as a {@code file:} address, or "". */
	static String coverUrl(String path) {
		if (path == null || path.isBlank()) {
			return "";
		}
		try {
			return Path.of(path).toUri().toString();
		} catch (java.nio.file.InvalidPathException e) {
			return "";
		}
	}

	static String playerName(String appId) {
		String a = appId;
		int bang = a.lastIndexOf('!');
		if (bang >= 0) {
			a = a.substring(bang + 1);
		}
		if (a.toLowerCase(Locale.ROOT).endsWith(".exe")) {
			a = a.substring(0, a.length() - 4);
		}
		String lower = a.toLowerCase(Locale.ROOT);
		if (lower.contains("spotify")) {
			return "Spotify";
		}
		if (lower.contains("msedge")) {
			return "Edge";
		}
		if (lower.contains("chrome")) {
			return "Chrome";
		}
		if (lower.contains("firefox")) {
			return "Firefox";
		}
		return a.isBlank() ? "Player" : a;
	}

	private void writeScript() throws IOException {
		byte[] script;
		try (InputStream in = WindowsMediaSource.class.getResourceAsStream(SCRIPT_RESOURCE)) {
			if (in == null) {
				throw new IOException("missing " + SCRIPT_RESOURCE);
			}
			script = in.readAllBytes();
		}
		Files.createDirectories(scriptPath.getParent());
		if (!Files.exists(scriptPath) || !java.util.Arrays.equals(Files.readAllBytes(scriptPath), script)) {
			Files.write(scriptPath, script);
		}
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private static long num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsLong() : 0;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	@Override
	public @Nullable NowPlaying current() {
		return current;
	}

	@Override
	public @Nullable String problem() {
		return problem;
	}

	@Override
	public void setPreferredPlayer(String preferred) {
		this.preferred = preferred == null ? "" : preferred.strip();
	}

	@Override
	public void close() {
		running = false;
		Process p = process;
		if (p != null) {
			p.destroy();
		}
		if (thread != null) {
			thread.interrupt();
		}
	}
}
