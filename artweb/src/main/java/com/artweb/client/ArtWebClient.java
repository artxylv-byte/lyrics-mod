package com.artweb.client;

import com.artweb.ArtWeb;
import com.artweb.block.WebScreenBlock;
import com.artweb.block.WebScreenBlockEntity;
import com.artweb.client.gui.BrowserScreen;
import com.artweb.client.render.WebScreenRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** Client side: draws the pages on screen blocks and opens the browser window on right-click (block or picture). */
public final class ArtWebClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(ArtWeb.WEB_SCREEN_ENTITY, WebScreenRenderer::new);
		ClientTickEvents.START_CLIENT_TICK.register(ScreenPicker::tick);
		WebScreenBlock.clientOpener = pos -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level != null && mc.level.getBlockEntity(pos) instanceof WebScreenBlockEntity screen) {
				mc.setScreen(new BrowserScreen(pos, screen));
			}
		};
	}
}
