package com.artlyrics.cover;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverDecoderTest {
	private static byte[] resource(String name) throws IOException {
		try (InputStream in = CoverDecoderTest.class.getResourceAsStream(name)) {
			return in.readAllBytes();
		}
	}

	@Test
	void animatedGifKeepsEveryFrameAndItsDelay() throws IOException {
		CoverImage img = CoverDecoder.decode(resource("/covers/animated.gif"), "test");
		assertNotNull(img);
		assertTrue(img.animated());
		assertEquals(3, img.frameCount());
		assertEquals(20, img.width());
		assertEquals(50, img.delayMs(0));
		assertEquals(200, img.delayMs(1));
		assertEquals(300, img.delayMs(2));
		// Frame 2 only changes a small square; the rest must still show frame 1 underneath.
		int[] second = img.frame(1);
		assertEquals(0xFFFF0000, second[0]);
		assertEquals(0xFF0000FF, second[7 * 20 + 7]);
		assertEquals(0xFF00FF00, img.frame(2)[0]);
		// Timing: 0-49 ms frame 0, 50-249 frame 1, 250-549 frame 2, then it loops.
		assertEquals(0, img.frameAt(10));
		assertEquals(1, img.frameAt(60));
		assertEquals(2, img.frameAt(300));
		assertEquals(0, img.frameAt(560));
	}

	@Test
	void stillPicturesAndSquareFrames() throws IOException {
		CoverImage img = CoverDecoder.decode(resource("/covers/wide.png"), "test");
		assertNotNull(img);
		assertFalse(img.animated());
		assertEquals(0, img.frameAt(123_456));
		int[][] square = img.squareFrames(8);
		assertEquals(64, square[0].length);
		assertEquals(0xFF0A141E, square[0][0]);
	}

	@Test
	void unreadableDataGivesNothing() {
		assertNull(CoverDecoder.decode(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}, "webp"));
		assertNull(CoverDecoder.decode(new byte[3], "tiny"));
	}

	@Test
	void bigPicturesAreShrunk() {
		java.awt.image.BufferedImage big = new java.awt.image.BufferedImage(1000, 500, java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.awt.image.BufferedImage small = CoverDecoder.shrink(big, CoverDecoder.MAX_SIDE);
		assertEquals(CoverDecoder.MAX_SIDE, small.getWidth());
		assertEquals(CoverDecoder.MAX_SIDE / 2, small.getHeight(), 1);
	}

	@Test
	void builtInDemoCoverIsAnimated() throws IOException {
		CoverImage img = CoverDecoder.decode(resource("/artlyrics/demo-cover.gif"), "demo");
		assertNotNull(img);
		assertTrue(img.frameCount() > 10);
	}
}
