package com.artlyrics.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The player's own Spotify account, for the two things the system media controls can't do: liking the current
 * song and adding it to a playlist. It uses Spotify's login for apps that run on your own computer (PKCE): you
 * create a free app on developer.spotify.com, paste its Client ID, and log in once in the browser. Your password
 * never passes through Artlyrics; only a token that lets it save songs and edit your playlists is kept, in
 * {@code config/artlyrics/spotify.json}.
 */
public final class SpotifyAccount {
	private static final Logger LOGGER = LoggerFactory.getLogger("Artlyrics");
	public static final int PORT = 43821;
	public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
	public static final String DASHBOARD = "https://developer.spotify.com/dashboard";
	static final String SCOPES = "user-library-read user-library-modify user-read-currently-playing "
			+ "playlist-read-private playlist-read-collaborative playlist-modify-private playlist-modify-public";
	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	/** A playlist the player can add songs to. */
	public record Playlist(String id, String name) {
	}

	private final Path tokenFile;
	private final Consumer<String> openBrowser;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Artlyrics-Spotify");
		t.setDaemon(true);
		return t;
	});

	private volatile String clientId = "";
	private volatile @Nullable String refreshToken;
	private volatile @Nullable String accessToken;
	private volatile long accessExpiresAt;
	private volatile String userName = "";
	private volatile String userId = "";
	private volatile String message = "";
	private volatile boolean busy;
	private volatile @Nullable Boolean liked;
	private volatile String likedTrackId = "";
	private volatile List<Playlist> playlists = List.of();
	private @Nullable HttpServer callbackServer;

	public SpotifyAccount(Path tokenFile, Consumer<String> openBrowser) {
		this.tokenFile = tokenFile;
		this.openBrowser = openBrowser;
		load();
	}

	public void setClientId(String id) {
		clientId = id == null ? "" : id.strip();
	}

	public String clientId() {
		return clientId;
	}

	public boolean connected() {
		return refreshToken != null;
	}

	public String userName() {
		return userName;
	}

	/** What happened last ("Liked", "Added to Road trip", an error...), or "". */
	public String message() {
		return message;
	}

	public boolean busy() {
		return busy;
	}

	/** Whether the current song is in Liked Songs, or null when unknown. */
	public @Nullable Boolean liked() {
		return liked;
	}

	public List<Playlist> playlists() {
		return playlists;
	}

	private volatile boolean checkedThisSong;

	/** Call when a new song starts: forgets whether the last one was liked, and looks again once. */
	public void songChanged() {
		liked = null;
		checkedThisSong = false;
	}

	/** Looks up whether the current song is liked (and the playlists), once per song. */
	public void checkSong() {
		if (connected() && !checkedThisSong && !busy) {
			checkedThisSong = true;
			refresh();
		}
	}

	// ---- logging in ----

	/** Opens Spotify's login page in the browser and waits for it to send the player back. */
	public synchronized void connect() {
		if (!clientId.matches("[0-9a-fA-F]{32}")) {
			message = "Paste your app's Client ID first (32 letters and digits)";
			return;
		}
		stopCallbackServer();
		String verifier = randomToken(64);
		String state = randomToken(16);
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 0);
			server.createContext("/callback", exchange -> {
				Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
				boolean ok = state.equals(q.get("state")) && q.containsKey("code");
				byte[] page = ("<!doctype html><meta charset=utf-8><title>Artlyrics</title>"
						+ "<body style='font-family:sans-serif;background:#14111c;color:#f2f0fa;text-align:center;padding-top:15vh'>"
						+ (ok ? "<h1>Connected to Spotify</h1><p>You can go back to Minecraft now.</p>"
						: "<h1>Not connected</h1><p>" + escape(q.getOrDefault("error", "Something went wrong")) + "</p>")
						+ "</body>").getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
				exchange.sendResponseHeaders(200, page.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(page);
				}
				if (ok) {
					String code = q.get("code");
					worker.execute(() -> finishLogin(code, verifier));
				} else {
					message = "Spotify login was cancelled";
				}
				worker.execute(this::stopCallbackServer);
			});
			server.start();
			callbackServer = server;
		} catch (IOException e) {
			message = "Couldn't start the login (port " + PORT + " is busy)";
			return;
		}
		message = "Log in to Spotify in your browser…";
		openBrowser.accept(authorizeUrl(clientId, challenge(verifier), state));
	}

	private synchronized void stopCallbackServer() {
		if (callbackServer != null) {
			callbackServer.stop(0);
			callbackServer = null;
		}
	}

	private void finishLogin(String code, String verifier) {
		try {
			JsonObject token = postForm("https://accounts.spotify.com/api/token", Map.of(
					"grant_type", "authorization_code", "code", code, "redirect_uri", REDIRECT,
					"client_id", clientId, "code_verifier", verifier));
			takeTokens(token);
			JsonObject me = get("https://api.spotify.com/v1/me");
			userName = str(me, "display_name").isBlank() ? str(me, "id") : str(me, "display_name");
			userId = str(me, "id");
			save();
			message = "Connected as " + userName;
			loadPlaylists();
		} catch (IOException | InterruptedException e) {
			fail("Couldn't finish the Spotify login", e);
		}
	}

	public synchronized void disconnect() {
		refreshToken = null;
		accessToken = null;
		userName = "";
		userId = "";
		playlists = List.of();
		liked = null;
		try {
			Files.deleteIfExists(tokenFile);
		} catch (IOException ignored) {
			// nothing to keep
		}
		message = "Disconnected";
	}

	// ---- actions ----

	/** Likes (or, if it's already liked, un-likes) the song playing in Spotify now. */
	public void toggleLike() {
		run(() -> {
			JsonObject track = currentTrack();
			String id = str(track, "id");
			boolean isLiked = isLiked(id);
			request(isLiked ? "DELETE" : "PUT", "https://api.spotify.com/v1/me/tracks?ids=" + id, null);
			liked = !isLiked;
			likedTrackId = id;
			message = (isLiked ? "Removed from Liked Songs: " : "Liked: ") + str(track, "name");
		});
	}

	/** Adds the song playing in Spotify now to a playlist. */
	public void addToPlaylist(Playlist playlist) {
		run(() -> {
			JsonObject track = currentTrack();
			JsonObject body = new JsonObject();
			JsonArray uris = new JsonArray();
			uris.add(str(track, "uri"));
			body.add("uris", uris);
			request("POST", "https://api.spotify.com/v1/playlists/" + playlist.id() + "/tracks", body.toString());
			message = "Added " + str(track, "name") + " to " + playlist.name();
		});
	}

	/** Refreshes the playlist list and whether the current song is liked. */
	public void refresh() {
		run(() -> {
			loadPlaylists();
			JsonObject track = currentTrack();
			likedTrackId = str(track, "id");
			liked = isLiked(likedTrackId);
			message = "";
		});
	}

	private interface Action {
		void run() throws IOException, InterruptedException;
	}

	private void run(Action action) {
		if (!connected() || busy) {
			return;
		}
		busy = true;
		worker.execute(() -> {
			try {
				action.run();
			} catch (IOException | InterruptedException e) {
				fail(e.getMessage() == null ? "Spotify didn't answer" : e.getMessage(), e);
			} finally {
				busy = false;
			}
		});
	}

	private void fail(String what, Exception e) {
		LOGGER.warn("[Artlyrics] Spotify: {}", what, e);
		message = what;
	}

	private JsonObject currentTrack() throws IOException, InterruptedException {
		JsonElement now = request("GET", "https://api.spotify.com/v1/me/player/currently-playing", null);
		if (now == null || !now.isJsonObject() || !now.getAsJsonObject().has("item") || !now.getAsJsonObject().get("item").isJsonObject()) {
			throw new IOException("Nothing is playing in Spotify");
		}
		JsonObject item = now.getAsJsonObject().getAsJsonObject("item");
		if (!"track".equals(str(item, "type"))) {
			throw new IOException("This isn't a song");
		}
		return item;
	}

	private boolean isLiked(String trackId) throws IOException, InterruptedException {
		JsonElement r = request("GET", "https://api.spotify.com/v1/me/tracks/contains?ids=" + trackId, null);
		return r != null && r.isJsonArray() && !r.getAsJsonArray().isEmpty() && r.getAsJsonArray().get(0).getAsBoolean();
	}

	private void loadPlaylists() throws IOException, InterruptedException {
		List<Playlist> out = new ArrayList<>();
		String url = "https://api.spotify.com/v1/me/playlists?limit=50";
		for (int page = 0; page < 4 && url != null; page++) {
			JsonElement r = request("GET", url, null);
			if (r == null || !r.isJsonObject()) {
				break;
			}
			out.addAll(parsePlaylists(r.getAsJsonObject(), userId));
			url = str(r.getAsJsonObject(), "next");
			url = url.isBlank() ? null : url;
		}
		playlists = List.copyOf(out);
	}

	/** Playlists the player can change: their own, and collaborative ones. */
	static List<Playlist> parsePlaylists(JsonObject page, String userId) {
		List<Playlist> out = new ArrayList<>();
		if (!page.has("items") || !page.get("items").isJsonArray()) {
			return out;
		}
		for (JsonElement el : page.getAsJsonArray("items")) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject p = el.getAsJsonObject();
			String owner = p.has("owner") && p.get("owner").isJsonObject() ? str(p.getAsJsonObject("owner"), "id") : "";
			boolean collaborative = p.has("collaborative") && p.get("collaborative").getAsBoolean();
			if (owner.equals(userId) || collaborative) {
				out.add(new Playlist(str(p, "id"), str(p, "name")));
			}
		}
		return out;
	}

	// ---- HTTP and tokens ----

	private String token() throws IOException, InterruptedException {
		if (accessToken != null && System.currentTimeMillis() < accessExpiresAt - 30_000) {
			return accessToken;
		}
		String refresh = refreshToken;
		if (refresh == null) {
			throw new IOException("Not connected to Spotify");
		}
		takeTokens(postForm("https://accounts.spotify.com/api/token", Map.of(
				"grant_type", "refresh_token", "refresh_token", refresh, "client_id", clientId)));
		save();
		return accessToken;
	}

	private void takeTokens(JsonObject token) throws IOException {
		String access = str(token, "access_token");
		if (access.isBlank()) {
			throw new IOException("Spotify refused: " + str(token, "error_description"));
		}
		accessToken = access;
		accessExpiresAt = System.currentTimeMillis() + (token.has("expires_in") ? token.get("expires_in").getAsLong() : 3600) * 1000;
		String refresh = str(token, "refresh_token");
		if (!refresh.isBlank()) {
			refreshToken = refresh; // Spotify may hand out a new one each time
		}
	}

	private JsonObject get(String url) throws IOException, InterruptedException {
		JsonElement r = request("GET", url, null);
		if (r == null || !r.isJsonObject()) {
			throw new IOException("Unexpected answer from Spotify");
		}
		return r.getAsJsonObject();
	}

	private @Nullable JsonElement request(String method, String url, @Nullable String json) throws IOException, InterruptedException {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
				.header("Authorization", "Bearer " + token());
		if (json != null) {
			b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
		} else {
			b.method(method, method.equals("GET") ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(""));
		}
		HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (res.statusCode() == 204 || res.body().isBlank()) {
			return null;
		}
		if (res.statusCode() == 401) {
			accessToken = null;
		}
		if (res.statusCode() >= 400) {
			JsonElement err = JsonParser.parseString(res.body());
			String why = err.isJsonObject() && err.getAsJsonObject().has("error") && err.getAsJsonObject().get("error").isJsonObject()
					? str(err.getAsJsonObject().getAsJsonObject("error"), "message") : "HTTP " + res.statusCode();
			throw new IOException("Spotify: " + why);
		}
		return JsonParser.parseString(res.body());
	}

	private JsonObject postForm(String url, Map<String, String> form) throws IOException, InterruptedException {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(formBody(form)))
				.build();
		HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		JsonElement json = JsonParser.parseString(res.body());
		if (!json.isJsonObject()) {
			throw new IOException("Unexpected answer from Spotify");
		}
		return json.getAsJsonObject();
	}

	private void load() {
		try {
			if (Files.exists(tokenFile)) {
				JsonObject o = JsonParser.parseString(Files.readString(tokenFile)).getAsJsonObject();
				String r = str(o, "refresh_token");
				refreshToken = r.isBlank() ? null : r;
				userName = str(o, "user");
				userId = str(o, "user_id");
				clientId = str(o, "client_id");
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("[Artlyrics] Couldn't read {}", tokenFile);
		}
	}

	private void save() {
		JsonObject o = new JsonObject();
		o.addProperty("client_id", clientId);
		o.addProperty("refresh_token", refreshToken == null ? "" : refreshToken);
		o.addProperty("user", userName);
		o.addProperty("user_id", userId);
		try {
			Files.createDirectories(tokenFile.getParent());
			Files.writeString(tokenFile, o.toString());
		} catch (IOException e) {
			LOGGER.warn("[Artlyrics] Couldn't save {}", tokenFile, e);
		}
	}

	// ---- small pieces (tested on their own) ----

	static String authorizeUrl(String clientId, String challenge, String state) {
		return "https://accounts.spotify.com/authorize?" + formBody(new LinkedHashMap<>(Map.of(
				"client_id", clientId, "response_type", "code", "redirect_uri", REDIRECT,
				"code_challenge_method", "S256", "code_challenge", challenge, "state", state, "scope", SCOPES)));
	}

	/** The PKCE code challenge: base64url(SHA-256(verifier)) without padding. */
	static String challenge(String verifier) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String randomToken(int bytes) {
		byte[] b = new byte[bytes];
		new SecureRandom().nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	static String formBody(Map<String, String> form) {
		StringBuilder sb = new StringBuilder();
		form.forEach((k, v) -> {
			if (!sb.isEmpty()) {
				sb.append('&');
			}
			sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8));
		});
		return sb.toString();
	}

	static Map<String, String> query(@Nullable String raw) {
		Map<String, String> out = new LinkedHashMap<>();
		if (raw == null) {
			return out;
		}
		for (String part : raw.split("&")) {
			int eq = part.indexOf('=');
			if (eq > 0) {
				out.put(URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8), URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
			}
		}
		return out;
	}

	private static String escape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}
}
