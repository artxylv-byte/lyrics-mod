package com.artweb.client.page;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loads pages from a small web server running inside the test. */
class PageCacheTest {
	private static HttpServer server;
	private static String base;

	@BeforeAll
	static void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		serve("/page", "text/html; charset=utf-8", "<html><head><title>Test</title></head><body><h2>Hello ārtweb</h2><p>Text</p></body></html>");
		serve("/moved", "text/html", "<meta http-equiv='refresh' content='0; url=/page'>");
		serve("/notes.txt", "text/plain", "line one\nline two");
		serve("/app", "text/html", "<div id=root></div><script>start()</script>");
		serve("/file.pdf", "application/pdf", "%PDF-1.4");
		server.createContext("/missing", ex -> {
			byte[] b = "<h1>Not found</h1>".getBytes(StandardCharsets.UTF_8);
			ex.getResponseHeaders().add("Content-Type", "text/html");
			ex.sendResponseHeaders(404, b.length);
			ex.getResponseBody().write(b);
			ex.close();
		});
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private static void serve(String path, String type, String body) {
		server.createContext(path, ex -> {
			byte[] b = body.getBytes(StandardCharsets.UTF_8);
			ex.getResponseHeaders().add("Content-Type", type);
			ex.sendResponseHeaders(200, b.length);
			try (OutputStream out = ex.getResponseBody()) {
				out.write(b);
			}
		});
	}

	@AfterAll
	static void stop() {
		server.stop(0);
	}

	@Test
	void loadsHtml() throws Exception {
		PageModel m = PageCache.fetch(base + "/page#section");
		assertEquals("Test", m.title());
		assertEquals("Hello ārtweb", ((PageModel.Para) m.blocks().get(0)).text());
	}

	@Test
	void followsRefresh() throws Exception {
		assertEquals("Test", PageCache.fetch(base + "/moved").title());
	}

	@Test
	void plainTextAndErrors() throws Exception {
		PageModel txt = PageCache.fetch(base + "/notes.txt");
		assertEquals("line one\nline two", ((PageModel.Para) txt.blocks().get(0)).text());
		PageModel missing = PageCache.fetch(base + "/missing");
		assertTrue(((PageModel.Para) missing.blocks().get(0)).text().contains("404"));
		PageModel app = PageCache.fetch(base + "/app");
		assertTrue(((PageModel.Para) app.blocks().get(0)).text().contains("JavaScript"));
		IOException e = assertThrows(IOException.class, () -> PageCache.fetch(base + "/file.pdf"));
		assertTrue(e.getMessage().contains("application/pdf"));
	}

	@Test
	void backgroundLoading() throws Exception {
		PageCache.Entry e = PageCache.get(base + "/page");
		for (int i = 0; i < 100 && e.state() == PageCache.State.LOADING; i++) {
			Thread.sleep(50);
		}
		assertEquals(PageCache.State.READY, e.state());
		assertInstanceOf(PageModel.Para.class, e.page().blocks().get(0));
		PageCache.Entry bad = PageCache.get("http://127.0.0.1:1/nothing");
		for (int i = 0; i < 100 && bad.state() == PageCache.State.LOADING; i++) {
			Thread.sleep(50);
		}
		assertEquals(PageCache.State.ERROR, bad.state());
	}
}
