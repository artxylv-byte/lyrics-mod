package com.artlyrics.nowplaying;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** A media source that runs a short command every so often and parses its output. */
abstract class PollingMediaSource implements MediaSource {
	protected static final Logger LOGGER = LoggerFactory.getLogger("Artlyrics");

	private final String threadName;
	private final long intervalMs;
	private ScheduledExecutorService executor;

	protected volatile @Nullable NowPlaying current;
	protected volatile @Nullable String problem;
	protected volatile String preferred = "spotify";

	PollingMediaSource(String threadName, long intervalMs) {
		this.threadName = threadName;
		this.intervalMs = intervalMs;
	}

	/** Reads the player state; returns null when nothing is playing. May throw if the tool is missing. */
	protected abstract @Nullable NowPlaying poll() throws Exception;

	@Override
	public void start() {
		executor = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, threadName);
			t.setDaemon(true);
			return t;
		});
		executor.scheduleWithFixedDelay(this::safePoll, 0, intervalMs, TimeUnit.MILLISECONDS);
	}

	private void safePoll() {
		try {
			current = poll();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			current = null;
			String message = describe(e);
			if (!message.equals(problem)) {
				LOGGER.warn("[Artlyrics] {}: {}", threadName, message);
			}
			problem = message;
		}
	}

	protected String describe(Exception e) {
		return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
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
		if (executor != null) {
			executor.shutdownNow();
		}
	}

	/** Picks the player to follow: playing beats paused, and the preferred player wins ties. */
	static @Nullable NowPlaying choose(List<NowPlaying> players, String preferred) {
		String pref = preferred == null ? "" : preferred.toLowerCase(Locale.ROOT);
		NowPlaying best = null;
		int bestScore = Integer.MIN_VALUE;
		for (NowPlaying p : players) {
			int score = 0;
			if (p.playing()) {
				score += 10;
			}
			if (!pref.isEmpty() && p.player().toLowerCase(Locale.ROOT).contains(pref)) {
				score += 5;
			}
			if (score > bestScore) {
				bestScore = score;
				best = p;
			}
		}
		return best;
	}

	/** Runs a command and returns its standard output, killing it if it takes longer than {@code timeoutMs}. */
	static String run(List<String> command, long timeoutMs) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
		try {
			process.getOutputStream().close();
			// Read on a helper thread so a hung command can't block us past the timeout.
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Thread reader = new Thread(() -> copyLimited(process.getInputStream(), out, 256 * 1024), "Artlyrics-cmd-output");
			reader.setDaemon(true);
			reader.start();
			if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
				throw new IOException(command.get(0) + " timed out");
			}
			reader.join(1000);
			synchronized (out) {
				return out.toString(StandardCharsets.UTF_8);
			}
		} finally {
			process.destroyForcibly();
		}
	}

	private static void copyLimited(InputStream in, ByteArrayOutputStream out, int limit) {
		byte[] chunk = new byte[4096];
		try (in) {
			int n;
			while ((n = in.read(chunk)) != -1) {
				synchronized (out) {
					if (out.size() + n <= limit) {
						out.write(chunk, 0, n);
					}
				}
			}
		} catch (IOException ignored) {
			// process ended or was killed
		}
	}
}
