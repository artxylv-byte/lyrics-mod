package com.artlyrics.screen;

import com.artlyrics.Artlyrics;
import com.artlyrics.ui.GuiCanvas;
import com.artlyrics.ui.SettingsUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Artlyrics settings. Opened with the keybind (default K) or from Mod Menu. The UI itself is {@link SettingsUi}. */
public final class ArtlyricsScreen extends Screen {
	private final @Nullable Screen parent;
	private final SettingsUi ui;

	public ArtlyricsScreen(@Nullable Screen parent) {
		super(Component.translatable("artlyrics.screen.title"));
		this.parent = parent;
		this.ui = new SettingsUi(Artlyrics.get().uiHost(this::onClose));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		ui.render(new GuiCanvas(graphics, font), mouseX, mouseY, System.nanoTime());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return ui.mouseClicked(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		ui.mouseDragged(event.x(), event.y());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		ui.mouseReleased();
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return ui.mouseScrolled(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	/** Switches to the settings page with this index (used by the screenshot test). */
	public void showPage(int index) {
		SettingsUi.Page[] pages = SettingsUi.Page.values();
		ui.setPage(pages[Math.floorMod(index, pages.length)]);
	}

	@Override
	public boolean isPauseScreen() {
		// Keep the world (and the demo song) moving behind the settings so changes can be watched live.
		return false;
	}

	@Override
	public void onClose() {
		Artlyrics.get().config().save();
		minecraft.setScreen(parent);
	}
}
