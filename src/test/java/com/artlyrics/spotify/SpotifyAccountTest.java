package com.artlyrics.spotify;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotifyAccountTest {
	@TempDir
	Path dir;

	@Test
	void pkceChallengeMatchesTheStandardExample() {
		// RFC 7636, appendix B.
		assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
				SpotifyAccount.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
	}

	@Test
	void loginAddressAsksForTheRightThings() {
		String url = SpotifyAccount.authorizeUrl("0123456789abcdef0123456789abcdef", "CHAL", "st8");
		Map<String, String> q = SpotifyAccount.query(url.substring(url.indexOf('?') + 1));
		assertEquals("0123456789abcdef0123456789abcdef", q.get("client_id"));
		assertEquals("code", q.get("response_type"));
		assertEquals(SpotifyAccount.REDIRECT, q.get("redirect_uri"));
		assertEquals("S256", q.get("code_challenge_method"));
		assertEquals("CHAL", q.get("code_challenge"));
		assertEquals("st8", q.get("state"));
		assertTrue(q.get("scope").contains("user-library-modify"));
		assertTrue(q.get("scope").contains("playlist-modify-private"));
	}

	@Test
	void onlyPlaylistsYouCanChange() {
		var page = JsonParser.parseString("""
				{"items":[{"id":"a","name":"Mine","owner":{"id":"me"},"collaborative":false},
				          {"id":"b","name":"Someone's","owner":{"id":"other"},"collaborative":false},
				          {"id":"c","name":"Shared","owner":{"id":"other"},"collaborative":true}],"next":null}
				""").getAsJsonObject();
		List<SpotifyAccount.Playlist> lists = SpotifyAccount.parsePlaylists(page, "me");
		assertEquals(List.of(new SpotifyAccount.Playlist("a", "Mine"), new SpotifyAccount.Playlist("c", "Shared")), lists);
	}

	@Test
	void needsAClientIdBeforeConnecting() {
		SpotifyAccount account = new SpotifyAccount(dir.resolve("spotify.json"), url -> {
			throw new AssertionError("must not open the browser");
		});
		account.setClientId("not an id");
		account.connect();
		assertFalse(account.connected());
		assertTrue(account.message().contains("Client ID"));
	}
}
