package com.artlyrics.lyrics;

import com.artlyrics.nowplaying.DemoMediaSource;
import com.artlyrics.nowplaying.NowPlaying;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Keeps the lyrics for the current track: notices track changes, looks lyrics up in the background,
 * caches results, and retries after network errors.
 */
public final class LyricsManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("Artlyrics");
	/** Wait this long after a track change before searching, so skipping through songs doesn't spam LRCLIB. */
	private static final long SETTLE_NANOS = 700_000_000L;
	private static final long RETRY_AFTER_ERROR_NANOS = 30_000_000_000L;
	private static final int CACHE_SIZE = 64;

	public enum State {
		IDLE, WAITING, SEARCHING, FOUND, INSTRUMENTAL, UNSYNCED_ONLY, NOT_FOUND, ERROR
	}

	private final LrclibClient client;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Artlyrics-Lyrics");
		t.setDaemon(true);
		return t;
	});
	private final Map<String, LyricsResult> cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, LyricsResult> eldest) {
			return size() > CACHE_SIZE;
		}
	});

	private @Nullable String trackId;
	private long trackSinceNanos;
	private long lastErrorNanos;
	private @Nullable Future<?> pending;

	private volatile State state = State.IDLE;
	private volatile @Nullable Lyrics lyrics;
	private volatile String detail = "";
	private @Nullable Lyrics demoLyrics;

	public LyricsManager(LrclibClient client) {
		this.client = client;
	}

	/** Call regularly (every client tick is fine) with what's playing. */
	public synchronized void update(@Nullable NowPlaying np, long nowNanos) {
		if (np == null || np.title().isBlank()) {
			if (trackId != null) {
				cancelPending();
				trackId = null;
				lyrics = null;
				state = State.IDLE;
				detail = "";
			}
			return;
		}

		String id = (np.artist() + "\u0000" + np.title()).toLowerCase(Locale.ROOT);
		if (!id.equals(trackId)) {
			cancelPending();
			trackId = id;
			trackSinceNanos = nowNanos;
			lyrics = null;
			detail = "";
			state = State.WAITING;
		}

		if (DemoMediaSource.isDemo(np)) {
			if (state != State.FOUND) {
				lyrics = demoLyrics();
				state = lyrics != null ? State.FOUND : State.NOT_FOUND;
				detail = "Built-in demo";
			}
			return;
		}

		if (state == State.WAITING && nowNanos - trackSinceNanos >= SETTLE_NANOS) {
			startLookup(np.toQuery(), id);
		} else if (state == State.ERROR && nowNanos - lastErrorNanos >= RETRY_AFTER_ERROR_NANOS) {
			startLookup(np.toQuery(), id);
		}
	}

	private void startLookup(TrackQuery query, String id) {
		LyricsResult cached = cache.get(query.cacheKey());
		if (cached != null) {
			apply(cached);
			return;
		}
		state = State.SEARCHING;
		pending = executor.submit(() -> {
			try {
				LyricsResult result = client.lookup(query);
				if (result.status() != LyricsResult.Status.ERROR) {
					cache.put(query.cacheKey(), result);
				} else {
					LOGGER.warn("[Artlyrics] Lyrics lookup failed for {} – {}: {}", query.artist(), query.title(), result.detail());
				}
				synchronized (this) {
					if (id.equals(trackId)) {
						apply(result);
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
	}

	private synchronized void apply(LyricsResult result) {
		lyrics = result.lyrics();
		detail = result.detail();
		state = switch (result.status()) {
			case FOUND -> State.FOUND;
			case INSTRUMENTAL -> State.INSTRUMENTAL;
			case UNSYNCED_ONLY -> State.UNSYNCED_ONLY;
			case NOT_FOUND -> State.NOT_FOUND;
			case ERROR -> {
				lastErrorNanos = System.nanoTime();
				yield State.ERROR;
			}
		};
	}

	private synchronized void cancelPending() {
		if (pending != null) {
			pending.cancel(true);
			pending = null;
		}
	}

	/** Forgets cached results and searches again for the current track. */
	public synchronized void refresh() {
		cache.clear();
		if (trackId != null) {
			cancelPending();
			lyrics = null;
			state = State.WAITING;
			trackSinceNanos = 0;
		}
	}

	public @Nullable Lyrics lyrics() {
		return lyrics;
	}

	public State state() {
		return state;
	}

	public String detail() {
		return detail;
	}

	public void close() {
		executor.shutdownNow();
	}

	private @Nullable Lyrics demoLyrics() {
		if (demoLyrics == null) {
			try (InputStream in = LyricsManager.class.getResourceAsStream("/artlyrics/demo.lrc")) {
				if (in != null) {
					demoLyrics = LrcParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), DemoMediaSource.DURATION_MS, "Built-in demo");
				}
			} catch (IOException e) {
				LOGGER.warn("[Artlyrics] Couldn't load demo lyrics", e);
			}
		}
		return demoLyrics;
	}
}
