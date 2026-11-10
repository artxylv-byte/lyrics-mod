package com.artlyrics.render;

import com.artlyrics.Artlyrics;
import com.artlyrics.ui.GuiCanvas;
import com.artlyrics.ui.SongCardView;
import com.artlyrics.ui.UiHost;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Draws the song card ({@link SongCardView}) on the HUD. */
public final class SongCardHud implements HudElement {
	private final SongCardView view = new SongCardView();
	private UiHost host;

	@Override
	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Artlyrics mod = Artlyrics.get();
		Minecraft mc = Minecraft.getInstance();
		if (mod == null || mc.options.hideGui) {
			return;
		}
		if (host == null) {
			host = mod.uiHost(() -> { });
		}
		long now = System.nanoTime();
		mod.tracker().frame(now);
		view.drawHud(new GuiCanvas(graphics, mc.font), host, now);
	}
}
