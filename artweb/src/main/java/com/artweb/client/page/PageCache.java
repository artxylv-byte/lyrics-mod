package com.artweb.client.page;

import com.artweb.client.page.PageModel.Kind;
import com.artweb.client.page.PageModel.Para;
import com.artweb.client.page.PageModel.Span;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Downloads and reads pages in the background and keeps the most recent ones, so many screens showing the same
 * site only load it once.
 */
public final class PageCache {
	public static final int MAX_PAGE_BYTES = 4 * 1024 * 1024;
	private static final int MAX_PAGES = 24;
	private static final int MAX_TEXT_CHARS = 200_000;
	private static final String ACCEPT = "text/html,application/xhtml+xml,text/plain;q=0.9,image/png,image/jpeg,image/gif;q=0.8,*/*;q=0.5";

	public enum State {
		LOADING, READY, ERROR
	}

	/** One page. The fields change once, from LOADING to READY or ERROR, on the loader thread. */
	public static final class Entry {
		public final String url;
		private volatile State state = State.LOADING;
		private volatile PageModel page;
		private volatile String error;

		Entry(String url) {
			this.url = url;
		}

		public State state() {
			return state;
		}

		public PageModel page() {
			return page;
		}

		public String error() {
			return error;
		}
	}

	private static final Map<String, Entry> PAGES = new LinkedHashMap<>(32, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
			return size() > MAX_PAGES;
		}
	};
	private static final AtomicInteger THREADS = new AtomicInteger();
	private static final ExecutorService LOADER = Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "ArtWeb page loader " + THREADS.incrementAndGet());
		t.setDaemon(true);
		return t;
	});

	private PageCache() {
	}

	/** The page at this address, starting to load it if it isn't here yet. */
	public static synchronized Entry get(String url) {
		Entry e = PAGES.get(url);
		if (e == null) {
			e = new Entry(url);
			PAGES.put(url, e);
			Entry entry = e;
			LOADER.execute(() -> load(entry));
		}
		return e;
	}

	/** Forgets the page and loads it again. */
	public static synchronized void reload(String url) {
		PAGES.remove(url);
		get(url);
	}

	private static void load(Entry e) {
		try {
			e.page = fetch(e.url);
			e.state = State.READY;
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			fail(e, "Stopped");
		} catch (java.net.ConnectException | java.nio.channels.UnresolvedAddressException ex) {
			fail(e, "Couldn't connect to " + Urls.host(e.url));
		} catch (IllegalArgumentException ex) {
			fail(e, "That address doesn't look right");
		} catch (java.net.http.HttpTimeoutException ex) {
			fail(e, "The site took too long to answer");
		} catch (IOException ex) {
			String msg = ex.getMessage();
			fail(e, msg == null || msg.isBlank() ? "Couldn't load the page (" + ex.getClass().getSimpleName() + ")" : msg);
		} catch (RuntimeException ex) {
			fail(e, "Couldn't read the page (" + ex.getClass().getSimpleName() + ")");
		}
	}

	private static void fail(Entry e, String message) {
		e.error = message;
		e.state = State.ERROR;
	}

	/** Downloads a page and turns it into a {@link PageModel}. Follows up to three "refresh" redirects. */
	public static PageModel fetch(String url) throws IOException, InterruptedException {
		String current = url;
		for (int hop = 0; ; hop++) {
			Web.Response r = Web.get(current, ACCEPT, MAX_PAGE_BYTES);
			String type = r.mimeType();
			if (type.startsWith("image/")) {
				String name = r.url().substring(r.url().lastIndexOf('/') + 1);
				return new PageModel(r.url(), name.isEmpty() ? Urls.host(r.url()) : name,
						List.of(new PageModel.Image(r.url(), "", 0, 0)));
			}
			if (type.equals("text/plain") || type.equals("text/markdown") || type.equals("text/csv")) {
				return plainText(r);
			}
			if (!type.isEmpty() && !type.contains("html") && !type.contains("xml")) {
				throw new IOException("ArtWeb can't show this kind of file (" + type + ")");
			}
			Document doc = Jsoup.parse(new ByteArrayInputStream(r.body()), r.charset(), r.url());
			String refresh = hop < 3 ? metaRefresh(doc, r.url()) : null;
			if (refresh != null && !refresh.equals(current)) {
				current = refresh;
				continue;
			}
			PageModel page = PageParser.parse(doc, r.url());
			return withNotes(page, r, doc);
		}
	}

	private static PageModel plainText(Web.Response r) {
		String cs = r.charset();
		String text = new String(r.body(), cs != null ? java.nio.charset.Charset.forName(cs) : StandardCharsets.UTF_8);
		if (text.length() > MAX_TEXT_CHARS) {
			text = text.substring(0, MAX_TEXT_CHARS);
		}
		String name = r.url().substring(r.url().lastIndexOf('/') + 1);
		return new PageModel(r.url(), name.isEmpty() ? Urls.host(r.url()) : name,
				List.of(new Para(Kind.PRE, 0, null, List.of(new Span(text, 0, null)))));
	}

	/** Adds a short explanation when the page came back empty, as an error, or cut off. */
	private static PageModel withNotes(PageModel page, Web.Response r, Document doc) {
		List<PageModel.Block> blocks = new ArrayList<>();
		if (r.status() >= 400) {
			blocks.add(note("The site answered with error " + r.status() + "."));
		}
		blocks.addAll(page.blocks());
		if (page.blocks().isEmpty()) {
			boolean scripts = !doc.select("script").isEmpty();
			blocks.add(note(scripts
					? "This page is built with JavaScript, which ArtWeb's simple viewer doesn't run. Try another site or a search."
					: "This page has no text to show."));
		}
		if (r.truncated()) {
			blocks.add(note("The page is very long; only the first part is shown."));
		}
		return blocks.size() == page.blocks().size() ? page : new PageModel(page.url(), page.title(), blocks);
	}

	private static Para note(String text) {
		return new Para(Kind.CAPTION, 0, null, List.of(new Span(text, PageModel.ITALIC, null)));
	}

	/** The address in {@code <meta http-equiv="refresh" content="0; url=...">}, if there is one. */
	static String metaRefresh(Document doc, String base) {
		for (Element meta : doc.select("meta[http-equiv]")) {
			if (!meta.attr("http-equiv").equalsIgnoreCase("refresh")) {
				continue;
			}
			String content = meta.attr("content");
			int i = content.toLowerCase(Locale.ROOT).indexOf("url=");
			if (i < 0) {
				continue;
			}
			String target = content.substring(i + 4).strip().replaceAll("^['\"]|['\"]$", "");
			try {
				String abs = URI.create(base).resolve(target.replace(" ", "%20")).toString();
				return Urls.isWeb(abs) ? abs : null;
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
		return null;
	}
}
