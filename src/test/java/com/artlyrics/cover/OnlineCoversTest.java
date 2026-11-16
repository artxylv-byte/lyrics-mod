package com.artlyrics.cover;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OnlineCoversTest {
	private static final String ANSWER = """
			{"resultCount":3,"results":[
			 {"artistName":"Other Band","trackName":"Paper Lanterns","artworkUrl100":"https://example.com/other/100x100bb.jpg"},
			 {"artistName":"Moon Harbor","trackName":"Paper Lanterns (Live)","artworkUrl100":"https://example.com/live/100x100bb.jpg"},
			 {"artistName":"Moon Harbor & Guest","trackName":"Paper Lanterns","artworkUrl100":"https://example.com/studio/100x100bb.jpg"}]}
			""";

	@Test
	void picksTheSameSongBySameArtistInAHigherSize() {
		assertEquals("https://example.com/studio/300x300bb.jpg", OnlineCovers.pickArtwork(ANSWER, "Paper Lanterns", "Moon Harbor"));
		assertNull(OnlineCovers.pickArtwork(ANSWER, "Paper Lanterns", "Nobody"));
		assertNull(OnlineCovers.pickArtwork("{\"resultCount\":0,\"results\":[]}", "x", "y"));
	}
}
