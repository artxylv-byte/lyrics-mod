package com.artweb.client.page;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/** Downloads pages and pictures over HTTP(S), with size and time limits. */
public final class Web {
	/** Looks like an ordinary desktop browser so sites send their normal pages; says it is ArtWeb at the end. */
	public static final String USER_AGENT =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36 ArtWeb/1.0";

	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10))
			.proxy(ProxySelector.getDefault())
			.build();

	private Web() {
	}

	/**
	 * A downloaded file. {@code url} is where it ended up after redirects; {@code truncated} is true when the file
	 * was bigger than the limit and only the start was kept.
	 */
	public record Response(String url, int status, String contentType, byte[] body, boolean truncated) {
		public String mimeType() {
			String t = contentType == null ? "" : contentType;
			int semi = t.indexOf(';');
			return (semi >= 0 ? t.substring(0, semi) : t).strip().toLowerCase(Locale.ROOT);
		}

		/** The charset named in the Content-Type header, or null. */
		public String charset() {
			if (contentType == null) {
				return null;
			}
			for (String part : contentType.split(";")) {
				String p = part.strip();
				if (p.toLowerCase(Locale.ROOT).startsWith("charset=")) {
					String cs = p.substring(8).strip().replace("\"", "");
					try {
						return java.nio.charset.Charset.isSupported(cs) ? cs : null;
					} catch (IllegalArgumentException e) {
						return null;
					}
				}
			}
			return null;
		}
	}

	public static Response get(String url, String accept, int maxBytes) throws IOException, InterruptedException {
		if (!Urls.isWeb(url)) {
			throw new IOException("Only http and https addresses can be opened");
		}
		int hash = url.indexOf('#');
		URI uri = URI.create(hash >= 0 ? url.substring(0, hash) : url);
		uri = URI.create(uri.toASCIIString()); // addresses with accents or other scripts
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", USER_AGENT)
				.header("Accept", accept)
				.header("Accept-Language", "en-US,en;q=0.8")
				.GET()
				.build();
		HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
		String type = response.headers().firstValue("Content-Type").orElse("");
		boolean truncated = false;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (InputStream in = response.body()) {
			byte[] buf = new byte[16384];
			int n;
			while ((n = in.read(buf)) > 0) {
				int room = maxBytes - out.size();
				if (n > room) {
					out.write(buf, 0, Math.max(0, room));
					truncated = true;
					break;
				}
				out.write(buf, 0, n);
			}
		}
		return new Response(response.uri().toString(), response.statusCode(), type, out.toByteArray(), truncated);
	}
}
