package com.artweb.client.page;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlsTest {
	@Test
	void addressesAndSearches() {
		assertEquals("https://minecraft.net", Urls.fromInput("minecraft.net"));
		assertEquals("https://example.com/a?b=1", Urls.fromInput("  https://example.com/a?b=1 "));
		assertEquals("http://localhost:8080/x", Urls.fromInput("localhost:8080/x"));
		assertEquals("http://192.168.1.10/", Urls.fromInput("192.168.1.10/"));
		assertEquals(Urls.SEARCH + "how+to+build+a+castle", Urls.fromInput("how to build a castle"));
		assertEquals(Urls.SEARCH + "redstone", Urls.fromInput("redstone"));
		assertNull(Urls.fromInput("   "));
	}

	@Test
	void unwrapsSearchRedirects() {
		String wrapped = "https://duckduckgo.com/l/?uddg=https%3A%2F%2Fen.wikipedia.org%2Fwiki%2FRedstone&rut=abc";
		assertEquals("https://en.wikipedia.org/wiki/Redstone", Urls.unwrapRedirect(wrapped));
		assertEquals("https://example.com/", Urls.unwrapRedirect("https://example.com/"));
	}

	@Test
	void onlyWebAddresses() {
		assertTrue(Urls.isWeb("HTTPS://example.com"));
		assertFalse(Urls.isWeb("file:///etc/passwd"));
		assertFalse(Urls.isWeb("javascript:alert(1)"));
		assertEquals("en.wikipedia.org", Urls.host("https://en.wikipedia.org/wiki/Minecraft"));
	}
}
