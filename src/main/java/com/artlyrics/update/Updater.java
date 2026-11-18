package com.artlyrics.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Updates Artlyrics from its GitHub repository. New versions are the {@code Artlyrics-<version>-mc<minecraft>.jar}
 * files in the repository's {@code downloads} folder. A newer one for this Minecraft version is downloaded next to
 * the installed jar under a name Fabric ignores ({@code .jar.download}), checked (size, Git checksum, and that it
 * really is Artlyrics of that version), and swapped in by a small helper that waits for Minecraft to close: it
 * deletes the old jar first and only then renames the new one, so there are never two copies for Fabric to load.
 * If anything goes wrong, the old version simply keeps working.
 */
public final class Updater {
	private static final Logger LOGGER = LoggerFactory.getLogger("Artlyrics");
	private static final Pattern JAR = Pattern.compile("^Artlyrics-(\\d+(?:\\.\\d+)*)-(mc[0-9.]+)\\.jar$");
	private static final String PENDING = ".download";
	private static final long MAX_BYTES = 64L * 1024 * 1024;
	private static final Duration TIMEOUT = Duration.ofSeconds(20);

	public enum State {
		/** Not checked yet. */
		IDLE,
		CHECKING,
		UP_TO_DATE,
		/** A newer version is on GitHub. */
		AVAILABLE,
		DOWNLOADING,
		/** Downloaded; it's swapped in when Minecraft closes. */
		READY,
		FAILED
	}

	/** A version found on GitHub. {@code sha} is Git's checksum of the file. */
	public record Release(String version, String fileName, String url, long size, String sha) {
	}

	private final String currentVersion;
	private final String minecraftTag;
	private final @Nullable Path installedJar;
	private final String repository;
	private final List<String> branches;
	private final String userAgent;
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Artlyrics-Updater");
		t.setDaemon(true);
		return t;
	});

	private volatile State state = State.IDLE;
	private volatile @Nullable Release latest;
	private volatile String message = "";
	private volatile float progress;

	/**
	 * @param fullVersion  this mod's version, e.g. "1.5.0+mc1.21.11"
	 * @param installedJar the jar this mod was loaded from, or null when running from a development build
	 */
	public Updater(String fullVersion, @Nullable Path installedJar, String repository, List<String> branches, String userAgent) {
		int plus = fullVersion.indexOf('+');
		this.currentVersion = plus >= 0 ? fullVersion.substring(0, plus) : fullVersion;
		this.minecraftTag = plus >= 0 ? fullVersion.substring(plus + 1) : "";
		this.installedJar = installedJar;
		this.repository = repository;
		this.branches = List.copyOf(branches);
		this.userAgent = userAgent;
	}

	public State state() {
		return state;
	}

	public @Nullable Release latest() {
		return latest;
	}

	/** A short explanation of the last problem, or "". */
	public String message() {
		return message;
	}

	/** 0..1 while downloading. */
	public float progress() {
		return progress;
	}

	public String currentVersion() {
		return currentVersion;
	}

	/** Whether this copy can replace itself (it was loaded from a jar file). */
	public boolean canInstall() {
		return installedJar != null;
	}

	public boolean busy() {
		return state == State.CHECKING || state == State.DOWNLOADING;
	}

	/** Looks for a newer version in the background; with {@code install} it's downloaded and set up too. */
	public synchronized void check(boolean install) {
		if (busy() || state == State.READY) {
			return;
		}
		state = State.CHECKING;
		message = "";
		worker.execute(() -> {
			try {
				Release best = null;
				for (String branch : branches) {
					try {
						for (Release r : parseListing(fetch(listingUrl(branch)), minecraftTag)) {
							if (best == null || compare(r.version(), best.version()) > 0) {
								best = r;
							}
						}
					} catch (IOException e) {
						LOGGER.debug("[Artlyrics] Update check of {} failed", branch, e);
					}
				}
				if (best == null || compare(best.version(), currentVersion) <= 0) {
					latest = best;
					state = State.UP_TO_DATE;
					return;
				}
				latest = best;
				state = State.AVAILABLE;
				if (install && canInstall()) {
					download(best);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				state = State.FAILED;
			} catch (RuntimeException e) {
				fail("Couldn't check for updates", e);
			}
		});
	}

	/** Downloads and sets up the newer version found by {@link #check}. */
	public synchronized void install() {
		Release r = latest;
		if (r == null || state != State.AVAILABLE || !canInstall()) {
			return;
		}
		state = State.DOWNLOADING;
		worker.execute(() -> {
			try {
				download(r);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				state = State.FAILED;
			}
		});
	}

	private void download(Release r) throws InterruptedException {
		state = State.DOWNLOADING;
		progress = 0;
		Path folder = installedJar.getParent();
		Path pending = folder.resolve(r.fileName() + PENDING);
		Path dest = folder.resolve(r.fileName());
		try {
			Path tmp = folder.resolve(r.fileName() + ".part");
			HttpRequest request = HttpRequest.newBuilder(URI.create(r.url())).timeout(Duration.ofMinutes(5))
					.header("User-Agent", userAgent).GET().build();
			HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				response.body().close();
				throw new IOException("GitHub answered " + response.statusCode());
			}
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			sha1.update(("blob " + r.size() + "\0").getBytes(StandardCharsets.US_ASCII));
			long total = 0;
			try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(tmp)) {
				byte[] buf = new byte[65536];
				int n;
				while ((n = in.read(buf)) > 0) {
					total += n;
					if (total > MAX_BYTES) {
						throw new IOException("download too big");
					}
					out.write(buf, 0, n);
					sha1.update(buf, 0, n);
					progress = r.size() > 0 ? Math.min(1f, total / (float) r.size()) : 0f;
				}
			}
			if (total != r.size() || (!r.sha().isEmpty() && !HexFormat.of().formatHex(sha1.digest()).equalsIgnoreCase(r.sha()))) {
				Files.deleteIfExists(tmp);
				throw new IOException("the download was damaged");
			}
			verifyJar(tmp, r.version());
			Files.move(tmp, pending, StandardCopyOption.REPLACE_EXISTING);
			scheduleSwap(installedJar, pending, dest);
			state = State.READY;
			LOGGER.info("[Artlyrics] Version {} downloaded; it will be used after Minecraft restarts.", r.version());
		} catch (IOException | NoSuchAlgorithmException e) {
			fail("Couldn't download the update: " + e.getMessage(), e);
		}
	}

	private void fail(String what, Exception e) {
		LOGGER.warn("[Artlyrics] {}", what, e);
		message = what;
		state = State.FAILED;
	}

	/**
	 * At startup: forgets downloads that are already installed, and if a downloaded update didn't get swapped in
	 * last time (the helper couldn't run), sets the swap up again.
	 */
	public void resumePending() {
		if (installedJar == null) {
			return;
		}
		Path folder = installedJar.getParent();
		try (Stream<Path> files = Files.list(folder)) {
			for (Path p : (Iterable<Path>) files::iterator) {
				String name = p.getFileName().toString();
				if (!name.endsWith(".jar" + PENDING) && !name.endsWith(".jar.part")) {
					continue;
				}
				String jarName = name.substring(0, name.lastIndexOf(".jar") + 4);
				Matcher m = JAR.matcher(jarName);
				if (!m.matches() || !m.group(2).equals(minecraftTag) || name.endsWith(".part")
						|| compare(m.group(1), currentVersion) <= 0) {
					Files.deleteIfExists(p);
					continue;
				}
				scheduleSwap(installedJar, p, folder.resolve(jarName));
				latest = new Release(m.group(1), jarName, "", Files.size(p), "");
				state = State.READY;
			}
		} catch (IOException e) {
			LOGGER.debug("[Artlyrics] Couldn't look for pending updates", e);
		}
	}

	private String listingUrl(String branch) {
		return "https://api.github.com/repos/" + repository + "/contents/downloads?ref="
				+ URLEncoder.encode(branch, StandardCharsets.UTF_8);
	}

	private String fetch(String url) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
				.header("User-Agent", userAgent)
				.header("Accept", "application/vnd.github+json")
				.GET().build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() != 200) {
			throw new IOException("GitHub answered " + response.statusCode());
		}
		return response.body();
	}

	// ---- pieces that are tested on their own ----

	/** The Artlyrics jars for this Minecraft version in a GitHub folder listing. */
	static List<Release> parseListing(String json, String minecraftTag) {
		List<Release> out = new ArrayList<>();
		JsonElement root = JsonParser.parseString(json);
		if (!root.isJsonArray()) {
			return out;
		}
		for (JsonElement el : root.getAsJsonArray()) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject o = el.getAsJsonObject();
			String name = str(o, "name");
			Matcher m = JAR.matcher(name);
			String url = str(o, "download_url");
			if (!m.matches() || !m.group(2).equals(minecraftTag) || !url.startsWith("https://")) {
				continue;
			}
			long size = o.has("size") ? o.get("size").getAsLong() : 0;
			out.add(new Release(m.group(1), name, url, size, str(o, "sha")));
		}
		return out;
	}

	/** Compares dotted version numbers: "1.10.0" is newer than "1.9.2". */
	static int compare(String a, String b) {
		String[] x = a.split("\\.");
		String[] y = b.split("\\.");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int p = i < x.length ? parse(x[i]) : 0;
			int q = i < y.length ? parse(y[i]) : 0;
			if (p != q) {
				return Integer.compare(p, q);
			}
		}
		return 0;
	}

	private static int parse(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Checks that a downloaded file is the Artlyrics jar of that version. */
	static void verifyJar(Path jar, String version) throws IOException {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			var entry = zip.getEntry("fabric.mod.json");
			if (entry == null) {
				throw new IOException("not a Fabric mod");
			}
			JsonObject meta;
			try (InputStream in = zip.getInputStream(entry)) {
				meta = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			}
			if (!"artlyrics".equals(str(meta, "id")) || !str(meta, "version").startsWith(version + "+")) {
				throw new IOException("not the expected Artlyrics version");
			}
		} catch (RuntimeException e) {
			throw new IOException("not a valid jar", e);
		}
	}

	/** Starts the helper that swaps the jars once this Minecraft has closed. */
	static void scheduleSwap(Path oldJar, Path pending, Path dest) throws IOException {
		long pid = ProcessHandle.current().pid();
		ProcessBuilder pb;
		if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
			String script = "$p=" + pid + ";$old=" + ps(oldJar) + ";$new=" + ps(pending) + ";$dest=" + ps(dest) + ";"
					+ "try{Wait-Process -Id $p -ErrorAction Stop}catch{};"
					+ "for($i=0;$i -lt 60;$i++){try{"
					+ "if(Test-Path -LiteralPath $old){Remove-Item -LiteralPath $old -Force -ErrorAction Stop};"
					+ "Move-Item -LiteralPath $new -Destination $dest -Force -ErrorAction Stop;break"
					+ "}catch{Start-Sleep -Seconds 1}}";
			String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
			pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
					"-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded);
		} else {
			pb = new ProcessBuilder("/bin/sh", "-c", unixSwapScript(), Long.toString(pid),
					oldJar.toString(), pending.toString(), dest.toString());
		}
		pb.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
		pb.start();
	}

	/** Waits for process $0 to end, deletes $1, then renames $2 to $3 (retrying for a minute). */
	static String unixSwapScript() {
		return "trap '' HUP; while kill -0 \"$0\" 2>/dev/null; do sleep 1; done; i=0; "
				+ "while [ $i -lt 60 ]; do rm -f -- \"$1\" && mv -f -- \"$2\" \"$3\" && break; i=$((i+1)); sleep 1; done";
	}

	private static String ps(Path p) {
		return "'" + p.toAbsolutePath().toString().replace("'", "''") + "'";
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}
}
