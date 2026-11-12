package com.artlyrics.lyrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalLyricsTest {
	@TempDir
	Path dir;

	@Test
	void findsFilesByNameOrTags() throws Exception {
		Files.writeString(dir.resolve("Moon Harbor - Paper Lanterns.lrc"), "[00:10.00]First\n[00:14.00]Second\n", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("my-file.lrc"), "[ar:Prāta Vētra]\n[ti:Kāds cits]\n[00:05.00]Rinda\n", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("Quiet Song.txt"), "Only words here\nAnd a second line\n", StandardCharsets.UTF_8);
		LocalLyrics local = new LocalLyrics(dir);

		LyricsResult byName = local.lookup(new TrackQuery("Paper Lanterns (Remastered)", "Moon Harbor", "", 120_000));
		assertEquals(LyricsResult.Status.FOUND, byName.status());
		assertEquals(10_000, byName.lyrics().lines().get(0).startMs());

		LyricsResult byTags = local.lookup(new TrackQuery("Kads cits", "Prata Vetra", "", 0));
		assertEquals(LyricsResult.Status.FOUND, byTags.status());
		assertEquals("Rinda", byTags.lyrics().lines().get(0).text());

		LyricsResult plain = local.lookup(new TrackQuery("Quiet Song", "Anyone", "", 90_000));
		assertEquals(LyricsResult.Status.FOUND, plain.status());
		assertTrue(plain.lyrics().estimated());

		assertEquals(LyricsResult.Status.NOT_FOUND, local.lookup(new TrackQuery("Paper Lanterns", "Other Band", "", 0)).status());
	}

	@Test
	void missingFolderIsFine() throws Exception {
		LocalLyrics local = new LocalLyrics(dir.resolve("nope"));
		assertEquals(LyricsResult.Status.NOT_FOUND, local.lookup(new TrackQuery("A", "B", "", 0)).status());
		local.createFolder();
		assertTrue(Files.exists(dir.resolve("nope/README.txt")));
	}
}
