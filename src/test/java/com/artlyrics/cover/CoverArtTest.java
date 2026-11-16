package com.artlyrics.cover;

import com.artlyrics.nowplaying.NowPlaying;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverArtTest {
	@TempDir
	Path dir;

	private void copy(String resource, Path to) throws Exception {
		try (InputStream in = CoverArtTest.class.getResourceAsStream(resource)) {
			Files.write(to, in.readAllBytes());
		}
	}

	private static NowPlaying song(String title, String artist, String album, String art) {
		return new NowPlaying("Spotify", title, artist, album, 200_000, 0, true, 0, art);
	}

	@Test
	void folderPicturesWinAndMatchSongOrAlbum() throws Exception {
		Path covers = dir.resolve("covers");
		Files.createDirectories(covers);
		copy("/covers/animated.gif", covers.resolve("Moon Harbor - Paper Lanterns.gif"));
		copy("/covers/wide.png", covers.resolve("Night Album.png"));
		CoverArt art = new CoverArt(covers, "test", () -> new CoverArt.Settings(true, false));

		CoverImage bySong = art.find(song("Paper Lanterns (Remastered)", "Moon Harbor", "", ""), new CoverArt.Settings(true, false));
		assertNotNull(bySong);
		assertTrue(bySong.animated());
		assertEquals("covers folder", bySong.source());

		CoverImage byAlbum = art.find(song("Another Song", "Moon Harbor", "Night Album", ""), new CoverArt.Settings(true, false));
		assertNotNull(byAlbum);
		assertEquals(1, byAlbum.frameCount());

		assertNull(art.find(song("Unknown", "Nobody", "", ""), new CoverArt.Settings(true, false)));
	}

	@Test
	void picturesTheMusicPlayerShares() throws Exception {
		Path file = dir.resolve("cover-1.img");
		copy("/covers/wide.png", file);
		CoverArt art = new CoverArt(dir.resolve("none"), "test", () -> new CoverArt.Settings(true, false));
		CoverImage img = art.find(song("Song", "Artist", "", file.toUri().toString()), new CoverArt.Settings(true, false));
		assertNotNull(img);
		assertEquals("Spotify", img.source());
		// A missing file or an address type we don't read gives nothing (and no error).
		assertNull(art.find(song("Song", "Artist", "", dir.resolve("gone.png").toUri().toString()), new CoverArt.Settings(true, false)));
		assertNull(art.find(song("Song", "Artist", "", "ftp://example.com/x.png"), new CoverArt.Settings(true, false)));
	}

	@Test
	void updatesInTheBackgroundAndClearsOnSongChange() throws Exception {
		Path file = dir.resolve("cover-2.img");
		copy("/covers/animated.gif", file);
		CoverArt art = new CoverArt(dir.resolve("none"), "test", () -> new CoverArt.Settings(true, false));
		NowPlaying np = song("Song", "Artist", "", file.toUri().toString());
		art.update(np, 0);
		art.update(np, 1_000_000_000L); // after the short wait, the lookup starts
		for (int i = 0; i < 100 && art.current() == null; i++) {
			Thread.sleep(20);
		}
		assertNotNull(art.current());
		art.update(song("Other", "Artist", "", ""), 2_000_000_000L);
		assertNull(art.current());
		art.update(null, 3_000_000_000L);
		assertNull(art.current());
		art.close();
	}
}
