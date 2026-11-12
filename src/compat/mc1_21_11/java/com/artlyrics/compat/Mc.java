package com.artlyrics.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Minecraft 1.21.11: the few calls that differ between Minecraft versions. */
public final class Mc {
	private static final String MOD_ID = "artlyrics";

	private Mc() {
	}

	/** Called during the main world pass, after entities; positions are relative to the camera. */
	@FunctionalInterface
	public interface WorldRenderHook {
		void render(PoseStack poseStack, MultiBufferSource buffers);
	}

	public static void onWorldRender(WorldRenderHook hook) {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> hook.render(context.matrices(), context.consumers()));
	}

	public static void addHudElementBeforeChat(String path, HudElement element) {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, path), element);
	}

	public static KeyMapping.Category keyCategory(String path) {
		return KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, path));
	}

	/** One of the mod's fonts ({@code assets/artlyrics/font/<path>.json}). */
	public static FontDescription modFont(String path) {
		return new FontDescription.Resource(Identifier.fromNamespaceAndPath(MOD_ID, path));
	}

	/** One of Minecraft's fonts, e.g. "uniform". */
	public static FontDescription vanillaFont(String path) {
		return new FontDescription.Resource(Identifier.withDefaultNamespace(path));
	}

	/** Draws a GUI sprite from {@code assets/artlyrics/textures/gui/sprites/<path>.png}, tinted. */
	public static void blitSprite(GuiGraphics graphics, String path, int x, int y, int width, int height, int argb) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath(MOD_ID, path), x, y, width, height, argb);
	}

	/** Camera yaw in degrees (Minecraft's yRot). */
	public static float cameraYaw(Camera camera) {
		return camera.yRot();
	}

	/** Camera pitch in degrees (Minecraft's xRot; negative looks up). */
	public static float cameraPitch(Camera camera) {
		return camera.xRot();
	}

	/** Opens a folder in the computer's file manager. */
	public static void openFolder(java.nio.file.Path folder) {
		net.minecraft.util.Util.getPlatform().openPath(folder);
	}
}
