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

	/** Called in the main world pass after Minecraft's sky, before any terrain is drawn. */
	public static void onSkyStage(Runnable hook) {
		WorldRenderEvents.START_MAIN.register(context -> hook.run());
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

	/** A texture the mod made while running (cover pictures). */
	public record Tex(Identifier id) {
	}

	/** Uploads a picture as a texture. Render thread only; the texture owns {@code image} afterwards. */
	public static Tex registerTexture(String path, com.mojang.blaze3d.platform.NativeImage image) {
		Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, path);
		net.minecraft.client.renderer.texture.DynamicTexture texture =
				new net.minecraft.client.renderer.texture.DynamicTexture(() -> "Artlyrics " + path, image);
		net.minecraft.client.Minecraft.getInstance().getTextureManager().register(id, texture);
		return new Tex(id);
	}

	/** Frees a texture made with {@link #registerTexture}. Render thread only. */
	public static void releaseTexture(Tex tex) {
		net.minecraft.client.Minecraft.getInstance().getTextureManager().release(tex.id());
	}

	/** Draws the region (u, v, regionWidth × regionHeight) of a texture into (x, y, width × height). */
	public static void blitTexture(GuiGraphics graphics, Tex tex, int x, int y, int width, int height, float u, float v,
								   int regionWidth, int regionHeight, int textureWidth, int textureHeight, int argb) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, tex.id(), x, y, u, v, width, height, regionWidth, regionHeight,
				textureWidth, textureHeight, argb);
	}

	/** Opens a web page in the default browser. */
	public static void openUrl(String url) {
		net.minecraft.util.Util.getPlatform().openUri(url);
	}

	/** A buffer for flat, see-through colored shapes in the world (sky gradients, beams, petals). */
	public static com.mojang.blaze3d.vertex.VertexConsumer flatBuffer(MultiBufferSource buffers) {
		return buffers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.textBackground());
	}

	/** A buffer for pictures from {@code assets/artlyrics/<path>} drawn in the world, full bright. */
	public static com.mojang.blaze3d.vertex.VertexConsumer textureBuffer(MultiBufferSource buffers, String path) {
		return buffers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.text(Identifier.fromNamespaceAndPath(MOD_ID, path)));
	}
}
