package com.artlyrics.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.ResourceLocation;

/** Minecraft 1.21.9 and 1.21.10: the few calls that differ between Minecraft versions. */
public final class Mc {
	private static final String MOD_ID = "artlyrics";

	private Mc() {
	}

	/** Called during the main world pass, after entities; positions are relative to the camera. */
	@FunctionalInterface
	public interface WorldRenderHook {
		void render(PoseStack poseStack, MultiBufferSource buffers);
	}

	private static WorldRenderHook worldHook;

	/**
	 * Fabric API for 1.21.9 has no world render events, so {@code LevelRendererMixin} calls this right after the
	 * main pass submits entities, with that pass's pose stack and buffers.
	 */
	public static void onWorldRender(WorldRenderHook hook) {
		worldHook = hook;
	}

	/** Called by the mixin. */
	public static void fireWorldRender(PoseStack poseStack, MultiBufferSource buffers) {
		if (worldHook != null) {
			worldHook.render(poseStack, buffers);
		}
	}

	public static void addHudElementBeforeChat(String path, HudElement element) {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ResourceLocation.fromNamespaceAndPath(MOD_ID, path), element);
	}

	public static KeyMapping.Category keyCategory(String path) {
		return KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(MOD_ID, path));
	}

	/** One of the mod's fonts ({@code assets/artlyrics/font/<path>.json}). */
	public static FontDescription modFont(String path) {
		return new FontDescription.Resource(ResourceLocation.fromNamespaceAndPath(MOD_ID, path));
	}

	/** One of Minecraft's fonts, e.g. "uniform". */
	public static FontDescription vanillaFont(String path) {
		return new FontDescription.Resource(ResourceLocation.withDefaultNamespace(path));
	}

	/** Draws a GUI sprite from {@code assets/artlyrics/textures/gui/sprites/<path>.png}, tinted. */
	public static void blitSprite(GuiGraphics graphics, String path, int x, int y, int width, int height, int argb) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ResourceLocation.fromNamespaceAndPath(MOD_ID, path), x, y, width, height, argb);
	}

	/** Camera yaw in degrees (Minecraft's yRot). */
	public static float cameraYaw(Camera camera) {
		return camera.getYRot();
	}

	/** Camera pitch in degrees (Minecraft's xRot; negative looks up). */
	public static float cameraPitch(Camera camera) {
		return camera.getXRot();
	}
}
