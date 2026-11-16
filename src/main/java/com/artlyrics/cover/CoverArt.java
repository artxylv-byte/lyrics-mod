package com.artlyrics.cover;

import com.artlyrics.lyrics.LocalLyrics;
import com.artlyrics.lyrics.TextMatch;
import com.artlyrics.lyrics.TrackQuery;
import com.artlyrics.nowplaying.DemoMediaSource;
import com.artlyrics.nowplaying.NowPlaying;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Finds the cover picture for the playing song, in this order:
 * <ol>
 *   <li>a picture in {@code config/artlyrics/covers} named after the song or album (an animated GIF stays animated);</li>
 *   <li>the picture the music player shares (Spotify, browsers and most players do);</li>
 *   <li>a search on iTunes, when finding covers online is switched on.</li>
 * </ol>
 * Lookups run in the background and results are kept for recently played songs.
 */
public final class CoverArt {
	private static final Logger LOGGER = LoggerFactory.getLogger("Artlyrics");
	private static final long SETTLE_NANOS = 500_000_000L;
	private static final int MAX_BYTES = 8 * 1024 * 1024;
	private static final int CACHE_SIZE = 12;
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final String[] EXTENSIONS = {".gif", ".png", ".jpg", ".jpeg", ".bmp"};

	/** Whether covers are shown at all, and whether they may be looked up online. */
	public record Settings(boolean enabled, boolean online) {
	}

	private final Path folder;
	private final String userAgent;
	private final Supplier<Settings> settings;
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Artlyrics-Covers");
		t.setDaemon(true);
		return t;
	});
	private final Map<String, Optional<CoverImage>> cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Optional<CoverImage>> eldest) {
			return size() > CACHE_SIZE;
		}
	});

	private @Nullable String trackKey;
	private @Nullable String artKey;
	private long trackSinceNanos;
	private @Nullable Future<?> pending;
	private volatile @Nullable CoverImage current;
	private volatile long changedNanos;

	public CoverArt(Path folder, String userAgent, Supplier<Settings> settings) {
		this.folder = folder;
		this.userAgent = userAgent;
		this.settings = settings;
	}

	public Path folder() {
		return folder;
	}

	/** The cover to show now, or null. */
	public @Nullable CoverImage current() {
		return current;
	}

	/** {@link System#nanoTime()} when the current cover appeared (animations start there). */
	public long changedNanos() {
		return changedNanos;
	}

	/** Creates the covers folder with a short how-to, if it isn't there yet. */
	public void createFolder() {
		try {
			Files.createDirectories(folder);
			Path help = folder.resolve("README.txt");
			if (!Files.exists(help)) {
				Files.writeString(help, """
						Put cover pictures here to show them on the Artlyrics song card instead of the player's own.

						Name each picture after the song or the album:
						  Artist - Title.gif   (animated GIFs play on the card)
						  Artist - Title.png   (.jpg and .bmp work too)
						  Album name.jpg       for every song of that album
						  Title.png            matches any artist

						Capital letters, accents and things like "(Remastered)" don't matter.
						""", StandardCharsets.UTF_8);
			}
		} catch (IOException ignored) {
			// only read when it exists
		}
	}

	/** Call every tick with what's playing. */
	public synchronized void update(@Nullable NowPlaying np, long nowNanos) {
		Settings s = settings.get();
		if (np == null || np.title().isBlank() || !s.enabled()) {
			if (trackKey != null) {
				cancel();
				trackKey = null;
				artKey = null;
				setCurrent(null);
			}
			return;
		}
		String tk = (np.artist() + "\u0000" + np.title() + "\u0000" + np.album()).toLowerCase(Locale.ROOT);
		String ak = tk + "\u0000" + np.artUrl() + "\u0000" + s.online();
		if (!tk.equals(trackKey)) {
			cancel();
			trackKey = tk;
			artKey = null;
			trackSinceNanos = nowNanos;
			setCurrent(null);
		}
		if (!ak.equals(artKey) && nowNanos - trackSinceNanos >= SETTLE_NANOS) {
			start(ak, np, s);
		}
	}

	/** Forgets found covers and looks again (after adding a picture to the folder, for example). */
	public synchronized void refresh() {
		cache.clear();
		artKey = null;
	}

	private void start(String ak, NowPlaying np, Settings s) {
		cancel();
		artKey = ak;
		Optional<CoverImage> cached = cache.get(ak);
		if (cached != null) {
			cached.ifPresent(this::setCurrent);
			return;
		}
		pending = executor.submit(() -> {
			CoverImage found = null;
			boolean failed = false;
			try {
				found = find(np, s);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (IOException | RuntimeException e) {
				failed = true;
				LOGGER.debug("[Artlyrics] Cover lookup failed", e);
			}
			if (!failed) {
				cache.put(ak, Optional.ofNullable(found));
			}
			synchronized (this) {
				// A new picture for the same song replaces the old one; finding nothing keeps what's there.
				if (ak.equals(artKey) && found != null) {
					setCurrent(found);
				}
			}
		});
	}

	private synchronized void setCurrent(@Nullable CoverImage image) {
		if (image != current) {
			current = image;
			changedNanos = System.nanoTime();
		}
	}

	private synchronized void cancel() {
		if (pending != null) {
			pending.cancel(true);
			pending = null;
		}
	}

	public void close() {
		executor.shutdownNow();
	}

	@Nullable CoverImage find(NowPlaying np, Settings s) throws IOException, InterruptedException {
		CoverImage fromFolder = fromFolder(np);
		if (fromFolder != null) {
			return fromFolder;
		}
		if (DemoMediaSource.isDemo(np)) {
			try (InputStream in = CoverArt.class.getResourceAsStream("/artlyrics/demo-cover.gif")) {
				return in == null ? null : CoverDecoder.decode(in.readAllBytes(), "Built-in demo");
			}
		}
		if (!np.artUrl().isEmpty()) {
			byte[] data = read(np.artUrl());
			CoverImage image = data == null ? null : CoverDecoder.decode(data, np.player().isBlank() ? "Player" : np.player());
			if (image != null) {
				return image;
			}
		}
		if (s.online()) {
			return online(np);
		}
		return null;
	}

	/** A picture in the covers folder named after the song (best) or its album. */
	@Nullable CoverImage fromFolder(NowPlaying np) {
		if (!Files.isDirectory(folder)) {
			return null;
		}
		TrackQuery query = np.toQuery();
		Path best = null;
		int bestScore = 0;
		try (Stream<Path> files = Files.walk(folder, 2)) {
			for (Path file : (Iterable<Path>) files::iterator) {
				String name = file.getFileName().toString();
				String ext = extension(name);
				if (ext == null || !Files.isRegularFile(file)) {
					continue;
				}
				String base = name.substring(0, name.length() - ext.length());
				int score = LocalLyrics.matchName(base, query);
				if (score == 0 && !np.album().isBlank() && TextMatch.sameTitle(base, np.album())) {
					score = 1;
				}
				if (score > bestScore) {
					bestScore = score;
					best = file;
				}
			}
		} catch (IOException e) {
			return null;
		}
		if (best == null) {
			return null;
		}
		try {
			if (Files.size(best) > MAX_BYTES) {
				return null;
			}
			return CoverDecoder.decode(Files.readAllBytes(best), "covers folder");
		} catch (IOException e) {
			return null;
		}
	}

	private static @Nullable String extension(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		for (String ext : EXTENSIONS) {
			if (lower.endsWith(ext) && lower.length() > ext.length()) {
				return name.substring(name.length() - ext.length());
			}
		}
		return null;
	}

	/** Searches iTunes for the song, trying the cleaned-up metadata variants, and downloads the picture. */
	private @Nullable CoverImage online(NowPlaying np) throws IOException, InterruptedException {
		List<TrackQuery> queries = new ArrayList<>();
		queries.add(np.toQuery());
		queries.addAll(np.toQuery().fallbacks());
		int tries = 0;
		for (TrackQuery q : queries) {
			List<String> artists = TextMatch.artists(q.artist());
			if (artists.isEmpty() || tries++ >= 2) {
				continue;
			}
			String term = artists.get(0) + " " + TextMatch.titleKey(q.title());
			byte[] json = read(OnlineCovers.SEARCH + URLEncoder.encode(term, StandardCharsets.UTF_8));
			if (json == null) {
				continue;
			}
			String art = OnlineCovers.pickArtwork(new String(json, StandardCharsets.UTF_8), q.title(), q.artist());
			if (art != null) {
				byte[] data = read(art);
				CoverImage image = data == null ? null : CoverDecoder.decode(data, "iTunes");
				if (image != null) {
					return image;
				}
			}
		}
		return null;
	}

	/** Reads a {@code file:} or downloads an {@code http(s):} address, up to {@link #MAX_BYTES}; null if it fails. */
	private byte[] read(String address) throws IOException, InterruptedException {
		URI uri;
		try {
			uri = URI.create(address);
		} catch (IllegalArgumentException e) {
			return null;
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (scheme.equals("file")) {
			Path path = Path.of(uri);
			if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) {
				return null;
			}
			return Files.readAllBytes(path);
		}
		if (!scheme.equals("http") && !scheme.equals("https")) {
			return null;
		}
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(TIMEOUT)
				.header("User-Agent", userAgent)
				.GET()
				.build();
		HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream in = response.body()) {
			if (response.statusCode() != 200) {
				return null;
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[16384];
			int n;
			while ((n = in.read(buf)) > 0) {
				if (out.size() + n > MAX_BYTES) {
					return null;
				}
				out.write(buf, 0, n);
			}
			return out.toByteArray();
		}
	}
}
