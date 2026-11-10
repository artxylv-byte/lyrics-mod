package com.artweb.client.page;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Turns what the player types into a web address, and cleans up search-engine redirect links. */
public final class Urls {
	/** A search engine page that works without JavaScript. */
	public static final String SEARCH = "https://html.duckduckgo.com/html/?q=";

	private Urls() {
	}

	/** "minecraft.net" becomes https://minecraft.net, "how to build a castle" becomes a web search. */
	public static String fromInput(String input) {
		String s = input == null ? "" : input.strip();
		if (s.isEmpty()) {
			return null;
		}
		String lower = s.toLowerCase(Locale.ROOT);
		if (lower.startsWith("http://") || lower.startsWith("https://")) {
			return s;
		}
		if (!s.contains(" ") && (lower.startsWith("localhost") || lower.matches("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$"))) {
			return "http://" + s;
		}
		if (!s.contains(" ") && s.contains(".") && !s.startsWith(".") && !s.endsWith(".")) {
			return "https://" + s;
		}
		return search(s);
	}

	public static String search(String query) {
		return SEARCH + URLEncoder.encode(query, StandardCharsets.UTF_8);
	}

	/** Search results link through a redirect page; go straight to the real address instead. */
	public static String unwrapRedirect(String url) {
		try {
			URI uri = URI.create(url);
			String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
			if (host.endsWith("duckduckgo.com") && uri.getPath() != null && uri.getPath().startsWith("/l/") && uri.getRawQuery() != null) {
				for (String part : uri.getRawQuery().split("&")) {
					if (part.startsWith("uddg=")) {
						return URLDecoder.decode(part.substring(5), StandardCharsets.UTF_8);
					}
				}
			}
		} catch (IllegalArgumentException ignored) {
			// not a valid URI: leave it
		}
		return url;
	}

	/** Only http and https pages are loaded. */
	public static boolean isWeb(String url) {
		if (url == null) {
			return false;
		}
		String lower = url.toLowerCase(Locale.ROOT);
		return lower.startsWith("http://") || lower.startsWith("https://");
	}

	/** The host part, for display ("en.wikipedia.org"). */
	public static String host(String url) {
		try {
			String host = URI.create(url).getHost();
			return host == null ? url : host;
		} catch (IllegalArgumentException e) {
			return url;
		}
	}
}
