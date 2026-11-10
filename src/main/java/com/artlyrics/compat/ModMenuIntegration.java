package com.artlyrics.compat;

import com.artlyrics.screen.ArtlyricsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Adds a "Configure" button for Artlyrics in Mod Menu (only loaded when Mod Menu is installed). */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ArtlyricsScreen::new;
	}
}
