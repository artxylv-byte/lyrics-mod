package com.artlyrics.ui;

import com.artlyrics.config.ArtlyricsConfig.MenuTheme;
import com.artlyrics.config.LyricStyle;
import com.artlyrics.config.Palette;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeTest {
	@AfterEach
	void back() {
		Theme.apply(MenuTheme.DARK);
	}

	@Test
	void lightThemesUseDarkTextAndReadableColors() {
		Theme.apply(MenuTheme.LIGHT);
		assertTrue(Theme.LIGHT);
		assertTrue(Palette.toHsv(Theme.TEXT & 0xFFFFFF)[2] < 0.3f, "dark text");
		// A pale lyric color gets dark enough to read on white.
		int pale = Theme.readable(0xFFFFE6F0);
		assertTrue(Palette.toHsv(pale & 0xFFFFFF)[2] <= 0.56f);
		Theme.apply(MenuTheme.DARK);
		assertEquals(0xFFFFE6F0, Theme.readable(0xFFFFE6F0));
		assertTrue(Palette.toHsv(Theme.TEXT & 0xFFFFFF)[2] > 0.9f);
	}

	@Test
	void everyThemeSetsItsColors() {
		for (MenuTheme t : MenuTheme.values()) {
			Theme.apply(t);
			assertNotEquals(Theme.TEXT, Theme.PANEL, t.toString());
		}
	}

	@Test
	void rainbowFlows() {
		Palette p = LyricStyle.RAINBOW.palette();
		assertNotEquals(p.colorAt(0f, 0), p.colorAt(0.5f, 0));
		assertNotEquals(p.colorAt(0f, 0), p.colorAt(0f, 4));
	}
}
