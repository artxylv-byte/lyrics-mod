package com.artlyrics.compat;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
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
 * Minecraft 1.21.9 and 1.21.10: skywriting drawn into the sky. The words are drawn as usual, but into a layer of their own;
 * then {@code shaders/core/sky_lyrics} lays that layer into the sky with light, soft edges and haze, before the
 * land and clouds, so those pass in front of the words.
 */
public final class SkyTextGpu {
	public static final RenderPipeline COMPOSITE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.FOG_SNIPPET)
			.withLocation(ResourceLocation.fromNamespaceAndPath("artlyrics", "pipeline/sky_lyrics"))
			.withVertexShader(ResourceLocation.fromNamespaceAndPath("artlyrics", "core/sky_lyrics"))
			.withFragmentShader(ResourceLocation.fromNamespaceAndPath("artlyrics", "core/sky_lyrics"))
			.withSampler("Sampler0")
			.withUniform("ArtlyricsLyrics", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
			.withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.withCull(false)
			.build();

	private static TextureTarget layer;
	private static GpuBuffer params;
	private static MultiBufferSource.BufferSource buffers;
	private static Boolean valid;
	/** Fog settings with no fog, so far-off words keep their colors (the sky shader adds its own haze). */
	private static GpuBuffer noFog;
	private static GpuBufferSlice savedFog;

	private SkyTextGpu() {
	}

	public static void register() {
		RenderPipelines.register(COMPOSITE);
	}

	/** Whether the shaders compiled on this computer. */
	public static boolean available() {
		if (valid == null) {
			valid = SkyGpu.worldAvailable() && RenderSystem.getDevice().precompilePipeline(COMPOSITE).isValid();
		}
		return valid;
	}

	/** Starts drawing into the lyrics layer: what's drawn into the returned buffers until {@link #end} goes there. */
	public static MultiBufferSource.BufferSource begin() {
		RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
		if (layer == null) {
			layer = new TextureTarget("Artlyrics sky lyrics", main.width, main.height, true);
			layer.setFilterMode(FilterMode.LINEAR);
		} else if (layer.width != main.width || layer.height != main.height) {
			layer.resize(main.width, main.height);
			layer.setFilterMode(FilterMode.LINEAR);
		}
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(layer.getColorTexture(), 0, layer.getDepthTexture(), 1.0);
		RenderSystem.outputColorTextureOverride = layer.getColorTextureView();
		RenderSystem.outputDepthTextureOverride = layer.getDepthTextureView();
		if (noFog == null) {
			noFog = RenderSystem.getDevice().createBuffer(() -> "Artlyrics no fog", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 48);
			try (MemoryStack stack = MemoryStack.stackPush()) {
				ByteBuffer data = Std140Builder.onStack(stack, 48)
						.putVec4(0f, 0f, 0f, 0f)
						.putFloat(1e7f).putFloat(2e7f).putFloat(1e7f).putFloat(2e7f).putFloat(2e7f).putFloat(2e7f)
						.get();
				RenderSystem.getDevice().createCommandEncoder().writeToBuffer(noFog.slice(), data);
			}
		}
		savedFog = RenderSystem.getShaderFog();
		RenderSystem.setShaderFog(noFog.slice());
		if (buffers == null) {
			buffers = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 20));
		}
		return buffers;
	}

	public static void end() {
		try {
			buffers.endBatch();
		} finally {
			RenderSystem.outputColorTextureOverride = null;
			RenderSystem.outputDepthTextureOverride = null;
			if (savedFog != null) {
				RenderSystem.setShaderFog(savedFog);
			}
		}
	}

	/**
	 * Lays the lyrics layer into the sky.
	 *
	 * @param look             0 starlight, 1 smoke, 2 neon, 3 clean
	 * @param glow             how much light around the letters
	 * @param daylight         0 at night .. 1 in full day
	 * @param radiansPerFontPx how big the letters are
	 * @param shadow           how much soft shadow to cast under the letters (0..1)
	 */
	public static void composite(float seconds, int look, float glow, float daylight, float radiansPerFontPx, float shadow) {
		if (layer == null) {
			return;
		}
		if (params == null) {
			params = RenderSystem.getDevice().createBuffer(() -> "Artlyrics sky lyrics", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 32);
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer data = Std140Builder.onStack(stack, 32)
					.putVec4(seconds, look, glow, daylight)
					.putVec4(radiansPerFontPx, shadow, 0f, 0f)
					.get();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(params.slice(), data);
		}
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f(), 1f);
		RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
				.createRenderPass(() -> "Artlyrics sky lyrics", main.getColorTextureView(), OptionalInt.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(COMPOSITE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setUniform("ArtlyricsLyrics", params);
			pass.bindSampler("Sampler0", layer.getColorTextureView());
			pass.setVertexBuffer(0, SkyGpu.screenTriangle());
			pass.draw(0, 3);
		}
	}
}
