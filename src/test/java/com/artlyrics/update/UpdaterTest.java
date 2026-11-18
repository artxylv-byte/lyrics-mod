package com.artlyrics.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdaterTest {
	@TempDir
	Path dir;

	private static final String LISTING = """
			[{"name":"ArtWeb-1.0.0-mc1.21.9.jar","size":5,"sha":"a","download_url":"https://raw.githubusercontent.com/x/ArtWeb-1.0.0-mc1.21.9.jar"},
			 {"name":"Artlyrics-1.10.0-mc1.21.11.jar","size":7,"sha":"b","download_url":"https://raw.githubusercontent.com/x/Artlyrics-1.10.0-mc1.21.11.jar"},
			 {"name":"Artlyrics-1.9.0-mc1.21.9.jar","size":7,"sha":"c","download_url":"https://raw.githubusercontent.com/x/Artlyrics-1.9.0-mc1.21.9.jar"},
			 {"name":"Artlyrics-1.4.0-mc1.21.11.jar","size":7,"sha":"d","download_url":"https://raw.githubusercontent.com/x/Artlyrics-1.4.0-mc1.21.11.jar"},
			 {"name":"README.md","size":1,"sha":"e","download_url":"https://raw.githubusercontent.com/x/README.md"}]
			""";

	@Test
	void findsOnlyThisMinecraftVersionsJars() {
		List<Updater.Release> r = Updater.parseListing(LISTING, "mc1.21.11");
		assertEquals(2, r.size());
		assertEquals("1.10.0", r.get(0).version());
		assertEquals(7, r.get(0).size());
		assertEquals(1, Updater.parseListing(LISTING, "mc1.21.9").size());
		assertTrue(Updater.parseListing("{\"message\":\"Not Found\"}", "mc1.21.11").isEmpty());
	}

	@Test
	void comparesVersionsByNumber() {
		assertTrue(Updater.compare("1.10.0", "1.9.2") > 0);
		assertTrue(Updater.compare("1.4.0", "1.4") == 0);
		assertTrue(Updater.compare("1.4.0", "1.4.1") < 0);
		assertTrue(Updater.compare("2.0", "1.99.99") > 0);
	}

	private Path jar(String name, String json) throws IOException {
		Path p = dir.resolve(name);
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(p))) {
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			zip.write(json.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return p;
	}

	@Test
	void onlyAcceptsTheRightJar() throws IOException {
		Updater.verifyJar(jar("good.jar", "{\"id\":\"artlyrics\",\"version\":\"1.5.0+mc1.21.11\"}"), "1.5.0");
		assertThrows(IOException.class, () -> Updater.verifyJar(jar("other.jar", "{\"id\":\"artweb\",\"version\":\"1.5.0+mc1.21.11\"}"), "1.5.0"));
		assertThrows(IOException.class, () -> Updater.verifyJar(jar("old.jar", "{\"id\":\"artlyrics\",\"version\":\"1.4.0+mc1.21.11\"}"), "1.5.0"));
		Path notZip = dir.resolve("broken.jar");
		Files.writeString(notZip, "<html>error</html>");
		assertThrows(IOException.class, () -> Updater.verifyJar(notZip, "1.5.0"));
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	void helperSwapsTheJarsOnceTheGameHasClosed() throws Exception {
		Path old = dir.resolve("Artlyrics-1.4.0-mc1.21.11.jar");
		Path pending = dir.resolve("Artlyrics-1.5.0-mc1.21.11.jar.download");
		Path dest = dir.resolve("Artlyrics-1.5.0-mc1.21.11.jar");
		Files.writeString(old, "old");
		Files.writeString(pending, "new");
		// Stand-in for Minecraft: a process that runs for a moment.
		Process game = new ProcessBuilder("sleep", "1").start();
		Process helper = new ProcessBuilder("/bin/sh", "-c", Updater.unixSwapScript(), Long.toString(game.pid()),
				old.toString(), pending.toString(), dest.toString()).start();
		Thread.sleep(300);
		assertTrue(Files.exists(old), "nothing changes while the game is still running");
		assertTrue(helper.waitFor(15, java.util.concurrent.TimeUnit.SECONDS));
		assertFalse(Files.exists(old));
		assertFalse(Files.exists(pending));
		assertEquals("new", Files.readString(dest));
	}

	@Test
	void forgetsDownloadsThatAreAlreadyInstalled() throws IOException {
		Path installed = dir.resolve("Artlyrics-1.4.0-mc1.21.11.jar");
		Files.writeString(installed, "x");
		Path stale = dir.resolve("Artlyrics-1.3.0-mc1.21.11.jar.download");
		Path part = dir.resolve("Artlyrics-1.6.0-mc1.21.11.jar.part");
		Files.writeString(stale, "x");
		Files.writeString(part, "x");
		Updater u = new Updater("1.4.0+mc1.21.11", installed, "o/r", List.of("main"), "test");
		u.resumePending();
		assertFalse(Files.exists(stale));
		assertFalse(Files.exists(part));
		assertEquals(Updater.State.IDLE, u.state());
	}

	/** Asks the real GitHub repository. Run with {@code ./gradlew test -Dartlyrics.liveTests=true}. */
	@Test
	@EnabledIfSystemProperty(named = "artlyrics.liveTests", matches = "true")
	void findsNewerVersionsOnGitHub() throws Exception {
		Updater u = new Updater("0.0.1+mc1.21.11", null, "artxylv-byte/lyrics-mod",
				List.of("main", "claude/minecraft-sky-lyric-mods-p1mjm2"), "Artlyrics-tests/1.0");
		u.check(true); // can't install from here (no jar), so it only finds it
		for (int i = 0; i < 100 && u.busy(); i++) {
			Thread.sleep(100);
		}
		assertEquals(Updater.State.AVAILABLE, u.state(), u.message());
		assertNotNull(u.latest());
		assertTrue(Updater.compare(u.latest().version(), "1.4.0") >= 0);
		System.out.println("github: newest " + u.latest().fileName());
	}
}
