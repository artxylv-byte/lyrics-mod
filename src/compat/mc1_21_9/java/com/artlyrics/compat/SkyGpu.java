package com.artlyrics.compat;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Minecraft 1.21.9 and 1.21.10: the GPU side of the animated skies. The world sky is one screen-covering triangle drawn with
 * {@code shaders/core/sky_world}, which works out every pixel from the direction it looks in; menu previews use
 * {@code sky_gui}, drawn like a picture.
 */
public final class SkyGpu {
	private static final String MOD_ID = "artlyrics";
	private static final ResourceLocation WHITE = id("textures/sky/white.png");

	public static final RenderPipeline WORLD = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.FOG_SNIPPET)
			.withLocation(id("pipeline/sky_world"))
			.withVertexShader(id("core/sky_world"))
			.withFragmentShader(id("core/sky_world"))
			.withUniform("ArtlyricsSky", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
			.withBlend(BlendFunction.TRANSLUCENT)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.withCull(false)
			.build();

	public static final RenderPipeline GUI = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(id("pipeline/sky_gui"))
			.withVertexShader(id("core/sky_gui"))
			.withFragmentShader(id("core/sky_gui"))
			.build();

	private static GpuBuffer triangle;
	private static GpuBuffer params;
	private static Boolean worldValid;
	private static Boolean guiValid;

	private SkyGpu() {
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	/** Registers the pipelines so they're compiled with the game's other shaders. */
	public static void register() {
		RenderPipelines.register(WORLD);
		RenderPipelines.register(GUI);
	}

	/** Forgets whether the shaders compiled (after resource packs change). */
	public static void reset() {
		worldValid = null;
		guiValid = null;
	}

	/** Whether the sky shader compiled on this computer. */
	public static boolean worldAvailable() {
		if (worldValid == null) {
			worldValid = RenderSystem.getDevice().precompilePipeline(WORLD).isValid();
		}
		return worldValid;
	}

	public static boolean guiAvailable() {
		if (guiValid == null) {
			guiValid = RenderSystem.getDevice().precompilePipeline(GUI).isValid();
		}
		return guiValid;
	}

	/**
	 * Draws the sky over Minecraft's into the main picture. Call before the terrain is drawn.
	 *
	 * @param seconds  animation time
	 * @param sky      the sky's id
	 * @param strength how much it covers Minecraft's sky (0..1)
	 * @param stars    whether to draw stars
	 * @param daylight how light it is outside (0..1), for blending into the horizon
	 * @param detail   0 (low) .. 2 (high)
	 */
	public static void drawWorld(float seconds, int sky, float strength, boolean stars, float daylight, int detail) {
		if (!worldAvailable()) {
			return;
		}
		ensureBuffers();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer data = Std140Builder.onStack(stack, 32)
					.putVec4(seconds, sky, strength, stars ? 1f : 0f)
					.putVec4(daylight, detail, 0f, 0f)
					.get();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(params.slice(), data);
		}
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f(), 1f);
		RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
				.createRenderPass(() -> "Artlyrics sky", target.getColorTextureView(), OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(WORLD);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setUniform("ArtlyricsSky", params);
			pass.setVertexBuffer(0, triangle);
			pass.draw(0, 3);
		}
	}

	private static void ensureBuffers() {
		if (triangle != null) {
			return;
		}
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(3 * DefaultVertexFormat.POSITION.getVertexSize())) {
			BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
			builder.addVertex(-1f, -1f, 0f);
			builder.addVertex(3f, -1f, 0f);
			builder.addVertex(-1f, 3f, 0f);
			try (MeshData mesh = builder.buildOrThrow()) {
				triangle = RenderSystem.getDevice().createBuffer(() -> "Artlyrics sky triangle", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
			}
		}
		params = RenderSystem.getDevice().createBuffer(() -> "Artlyrics sky", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 32);
	}

	/** One triangle covering the screen (positions in clip space). */
	static GpuBuffer screenTriangle() {
		ensureBuffers();
		return triangle;
	}

	/**
	 * A live preview of a sky in the rectangle (x, y, w, h). Time goes in the vertex color, so it repeats every
	 * 54 minutes.
	 */
	public static void drawPreview(GuiGraphics graphics, int sky, double seconds, int x, int y, int w, int h, float alpha) {
		int ticks = (int) Math.floorMod(Math.round(seconds * 20.0), 65536L);
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
		int color = (a << 24) | ((sky & 0xFF) << 16) | ticks;
		graphics.blit(GUI, WHITE, x, y, 0f, 0f, w, h, w, h, color);
	}
}
