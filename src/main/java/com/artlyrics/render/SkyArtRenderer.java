package com.artlyrics.render;

import com.artlyrics.Artlyrics;
import com.artlyrics.compat.Mc;
import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.sky.SkyTheme;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Paints the chosen animated sky: a color dome that fades into Minecraft's own sky near the horizon (so distant
 * land and fog still match), twinkling stars, and the sky's objects, which turn with the world like real ones.
 * Everything is drawn camera-relative, a little further away than the lyrics, so the lyrics stay in front.
 */
public final class SkyArtRenderer {
	private static final float DEG = (float) (Math.PI / 180.0);
	private static final int LIGHT = LightTexture.FULL_BRIGHT;
	private static final String GLOW = "textures/sky/glow.png";

	private float shown;
	private long lastNanos;

	public void render(PoseStack poseStack, MultiBufferSource buffers) {
		Artlyrics mod = Artlyrics.get();
		Minecraft mc = Minecraft.getInstance();
		if (mod == null || mc.level == null) {
			return;
		}
		ArtlyricsConfig config = mod.config();
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min(0.25f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		SkyTheme sky = config.sky;
		float want = 0f;
		if (sky != SkyTheme.NONE && mc.level.dimension() == Level.OVERWORLD) {
			want = config.skyNightOnly ? SkyTheme.nightAmount(mc.level.getDayTime()) : 1f;
			want *= 1f - 0.75f * mc.level.getRainLevel(1f);
		}
		// Fade the whole sky in and out over about half a second instead of switching it.
		shown += (want - shown) * Easing.approach(dt, 4f);
		if (shown < 0.01f || sky == SkyTheme.NONE) {
			return;
		}
		float vis = shown * config.skyIntensity;
		double t = now / 1e9 * config.skySpeed;
		float dist = distance(mc);
		Matrix4f base = poseStack.last().pose();

		dome(Mc.flatBuffer(buffers), base, sky, dist, vis);
		if (config.skyStars && sky.stars > 0) {
			stars(Mc.textureBuffer(buffers, GLOW), base, sky.stars, dist * 0.98f, vis, t);
		}
		switch (sky) {
			case RINGED_PLANET -> {
				picture(buffers, sky, base, 160f, 38f + (float) Math.sin(t * 0.05) * 0.6f, dist * 0.96f, 24f, 24f, (float) Math.sin(t * 0.03) * 0.08f, vis, 0xFFFFFF);
				picture(buffers, "textures/sky/moon.png", base, 182f, 46f, dist * 0.96f, 3.2f, 3.2f, 0f, vis * 0.9f, 0xE8E2D4);
			}
			case SPIRAL_GALAXY -> {
				picture(buffers, "textures/sky/nebula.png", base, 196f, 42f, dist * 0.97f, 60f, 46f, 0.3f, vis * 0.28f, 0xC8B8FF);
				picture(buffers, sky, base, 200f, 44f, dist * 0.96f, 36f, 36f, (float) (t * 0.02), vis, 0xFFFFFF);
			}
			case NEBULA -> {
				picture(buffers, sky, base, 140f, 34f, dist * 0.97f, 54f, 44f, (float) (t * 0.004), vis * 0.9f, 0xFFFFFF);
				picture(buffers, sky, base, 230f, 52f, dist * 0.97f, 44f, 36f, 2f + (float) (t * -0.003), vis * 0.7f, 0xB8D8FF);
				picture(buffers, sky, base, 40f, 28f, dist * 0.97f, 40f, 30f, 4f, vis * 0.6f, 0xFFC8E0);
			}
			case ECLIPSE -> {
				float pulse = 0.85f + 0.15f * (float) Math.sin(t * 0.8);
				picture(buffers, sky, base, 180f, 40f, dist * 0.96f, 20f * pulse, 20f * pulse, (float) (t * 0.01), vis, 0xFFFFFF);
			}
			case TWIN_MOONS -> {
				picture(buffers, sky, base, 120f, 36f, dist * 0.96f, 11f, 11f, 0f, vis, 0xD8E8FF);
				picture(buffers, sky, base, 138f, 27f + (float) Math.sin(t * 0.04) * 0.8f, dist * 0.95f, 5.5f, 5.5f, 1f, vis, 0xFFD0E0);
				picture(buffers, GLOW, base, 120f, 36f, dist * 0.97f, 30f, 30f, 0f, vis * 0.25f, 0xA8D0FF);
			}
			case SYNTH_SUNSET -> {
				band(Mc.flatBuffer(buffers), base, dist * 0.99f, 0f, 22f, 0xFF4FA0, vis * 0.55f);
				picture(buffers, sky, base, 180f, 13f, dist * 0.96f, 28f, 28f, 0f, vis, 0xFFFFFF);
				picture(buffers, GLOW, base, 180f, 13f, dist * 0.97f, 60f, 40f, 0f, vis * 0.35f, 0xFF6FB0);
			}
			case AURORA -> aurora(Mc.flatBuffer(buffers), base, dist * 0.97f, vis, t);
			case LIGHT_PILLARS -> pillars(Mc.flatBuffer(buffers), base, dist * 0.97f, vis, t);
			case METEOR_SHOWER -> meteors(Mc.flatBuffer(buffers), base, dist * 0.96f, vis, t);
			case SAKURA_DUSK -> {
				band(Mc.flatBuffer(buffers), base, dist * 0.99f, 0f, 20f, 0xFFD6E6, vis * 0.45f);
				petals(Mc.flatBuffer(buffers), base, dist * 0.9f, vis, t);
			}
			default -> {
			}
		}
	}

	/** Further than the lyrics, but well inside the fog so it isn't faded out. */
	private static float distance(Minecraft mc) {
		float blocks = mc.options.getEffectiveRenderDistance() * 16f;
		return Math.max(26f, Math.min(115f, blocks * 0.7f));
	}

	private static Vector3f dir(float yaw, float elev, float dist) {
		return SkyGeometry.direction(yaw, elev).mul(dist);
	}

	/** The color dome: clear at the horizon, full color from about 14° up. */
	private static void dome(VertexConsumer vc, Matrix4f m, SkyTheme sky, float dist, float vis) {
		int rings = 24;
		int segments = 36;
		for (int r = 0; r < rings; r++) {
			float e0 = 90f * r / rings;
			float e1 = 90f * (r + 1) / rings;
			int c0 = ARGB.color(vis * domeAlpha(e0), sky.colorAt(e0 / 90f));
			int c1 = ARGB.color(vis * domeAlpha(e1), sky.colorAt(e1 / 90f));
			for (int s = 0; s < segments; s++) {
				float y0 = 360f * s / segments;
				float y1 = 360f * (s + 1) / segments;
				// Corner order matters: this way round the faces point at the camera (inside the dome).
				vertex(vc, m, dir(y1, e0, dist), c0);
				vertex(vc, m, dir(y1, e1, dist), c1);
				vertex(vc, m, dir(y0, e1, dist), c1);
				vertex(vc, m, dir(y0, e0, dist), c0);
			}
		}
	}

	private static float domeAlpha(float elev) {
		float x = Math.max(0f, Math.min(1f, elev / 14f));
		return x * x * (3 - 2 * x) * 0.97f;
	}

	/** A soft glow along the horizon (sunsets): it fades out smoothly at both edges, brightest a third of the way up. */
	private static void band(VertexConsumer vc, Matrix4f m, float dist, float e0, float e1, int rgb, float alpha) {
		int segments = 36;
		int steps = 10;
		for (int k = 0; k < steps; k++) {
			float u0 = k / (float) steps;
			float u1 = (k + 1) / (float) steps;
			float el0 = e0 + (e1 - e0) * u0;
			float el1 = e0 + (e1 - e0) * u1;
			int c0 = ARGB.color(alpha * bump(u0), rgb);
			int c1 = ARGB.color(alpha * bump(u1), rgb);
			for (int s = 0; s < segments; s++) {
				float y0 = 360f * s / segments;
				float y1 = 360f * (s + 1) / segments;
				vertex(vc, m, dir(y1, el0, dist), c0);
				vertex(vc, m, dir(y1, el1, dist), c1);
				vertex(vc, m, dir(y0, el1, dist), c1);
				vertex(vc, m, dir(y0, el0, dist), c0);
			}
		}
	}

	/** 0 at both ends with no hard edge, 1 around a third of the way. */
	private static float bump(float u) {
		float x = (float) Math.sin(Math.PI * Math.pow(u, 0.6));
		return x * x;
	}

	private static void stars(VertexConsumer vc, Matrix4f base, int count, float dist, float vis, double t) {
		for (int i = 0; i < count; i++) {
			float yaw = rand(i, 1) * 360f;
			float elev = 12f + (float) Math.pow(rand(i, 2), 0.8) * 76f;
			float size = 0.35f + rand(i, 3) * 0.55f;
			double wave = 0.5 + 0.5 * Math.sin(t * (0.8 + rand(i, 4) * 2.2) + i);
			float alpha = vis * (0.35f + 0.65f * (float) wave) * Math.min(1f, (elev - 12f) / 10f);
			int rgb = rand(i, 5) < 0.15f ? 0xBFD4FF : rand(i, 5) > 0.92f ? 0xFFE6C8 : 0xFFFFFF;
			quad(vc, SkyGeometry.wordMatrix(base, yaw, elev, dist, 0f, dist * DEG), size, size, ARGB.color(alpha, rgb), true);
		}
	}

	private static void picture(MultiBufferSource buffers, SkyTheme sky, Matrix4f base, float yaw, float elev, float dist,
								float w, float h, float roll, float alpha, int tint) {
		picture(buffers, sky.texturePath(), base, yaw, elev, dist, w, h, roll, alpha, tint);
	}

	/** A picture facing the camera; {@code w} and {@code h} are in degrees of sky. */
	private static void picture(MultiBufferSource buffers, String texture, Matrix4f base, float yaw, float elev, float dist,
								float w, float h, float roll, float alpha, int tint) {
		quad(Mc.textureBuffer(buffers, texture), SkyGeometry.wordMatrix(base, yaw, elev, dist, roll, dist * DEG), w, h,
				ARGB.color(Math.max(0f, Math.min(1f, alpha)), tint), true);
	}

	/** Ribbons of light rippling across the north. */
	private static void aurora(VertexConsumer vc, Matrix4f m, float dist, float vis, double t) {
		int[][] colors = {{0x5CFFB0, 0x8A6BFF}, {0x3FE0D0, 0x5C7BFF}, {0x9CFF8A, 0xC86BFF}};
		for (int k = 0; k < 3; k++) {
			float start = 120f + k * 40f;
			int steps = 96;
			for (int s = 0; s < steps; s++) {
				float a0 = start + 150f * s / steps;
				float a1 = start + 150f * (s + 1) / steps;
				float edge0 = edgeFade(s, steps);
				float edge1 = edgeFade(s + 1, steps);
				float b0 = 16f + k * 5f + 5f * (float) Math.sin(a0 * DEG * 3 + t * 0.35 + k);
				float b1 = 16f + k * 5f + 5f * (float) Math.sin(a1 * DEG * 3 + t * 0.35 + k);
				float h0 = 12f + 5f * (float) Math.sin(a0 * DEG * 5 + t * 0.6 + k * 2);
				float h1 = 12f + 5f * (float) Math.sin(a1 * DEG * 5 + t * 0.6 + k * 2);
				float shimmer0 = 0.6f + 0.4f * (float) Math.sin(a0 * DEG * 9 + t * 1.3 + k);
				float shimmer1 = 0.6f + 0.4f * (float) Math.sin(a1 * DEG * 9 + t * 1.3 + k);
				int bottom0 = ARGB.color(vis * 0.55f * edge0 * shimmer0, colors[k][0]);
				int bottom1 = ARGB.color(vis * 0.55f * edge1 * shimmer1, colors[k][0]);
				int top0 = ARGB.color(0f, colors[k][1]);
				int top1 = ARGB.color(0f, colors[k][1]);
				vertex(vc, m, dir(a1, b1, dist), bottom1);
				vertex(vc, m, dir(a1, b1 + h1, dist), top1);
				vertex(vc, m, dir(a0, b0 + h0, dist), top0);
				vertex(vc, m, dir(a0, b0, dist), bottom0);
			}
		}
	}

	private static float edgeFade(int s, int steps) {
		float x = Math.min(s, steps - s) / (steps * 0.2f);
		return Math.min(1f, x);
	}

	/** Tall beams of light rising from the horizon, slowly brightening and dimming. */
	private static void pillars(VertexConsumer vc, Matrix4f m, float dist, float vis, double t) {
		for (int i = 0; i < 11; i++) {
			float yaw = rand(i, 7) * 360f;
			float width = 0.7f + rand(i, 8) * 1.2f;
			float height = 35f + rand(i, 9) * 30f;
			float pulse = 0.4f + 0.6f * (float) Math.pow(0.5 + 0.5 * Math.sin(t * (0.4 + rand(i, 10)) + i * 2.3), 2);
			int rgb = rand(i, 11) > 0.5f ? 0xF4EEFF : 0xB98CFF;
			int bottom = ARGB.color(vis * 0.7f * pulse, rgb);
			int top = ARGB.color(0f, rgb);
			vertex(vc, m, dir(yaw + width, 0f, dist), bottom);
			vertex(vc, m, dir(yaw + width * 0.3f, height, dist), top);
			vertex(vc, m, dir(yaw - width * 0.3f, height, dist), top);
			vertex(vc, m, dir(yaw - width, 0f, dist), bottom);
		}
	}

	/** Shooting stars: a few at a time, each streaking across and fading. */
	private static void meteors(VertexConsumer vc, Matrix4f base, float dist, float vis, double t) {
		int lanes = 5;
		double period = 3.2;
		for (int l = 0; l < lanes; l++) {
			double phaseT = t / period + l / (double) lanes;
			long cycle = (long) Math.floor(phaseT);
			float p = (float) (phaseT - cycle);
			if (p > 0.35f) {
				continue; // resting between meteors
			}
			float q = p / 0.35f;
			int seed = (int) (cycle * 13 + l * 101);
			float yaw = rand(seed, 1) * 360f;
			float elev = 30f + rand(seed, 2) * 40f;
			float angle = -0.5f - rand(seed, 3) * 0.6f;
			float travel = 18f * q;
			float alpha = vis * (q < 0.15f ? q / 0.15f : 1f - (q - 0.15f) / 0.85f);
			Matrix4f m = SkyGeometry.wordMatrix(base, yaw + travel * (float) Math.cos(angle), elev + travel * (float) Math.sin(angle),
					dist, angle, dist * DEG);
			float len = 7f;
			int head = ARGB.color(alpha, 0xFFFFFF);
			int tail = ARGB.color(0f, 0xBFD4FF);
			vc.addVertex(m, -len, -0.12f, 0f).setColor(tail).setLight(LIGHT);
			vc.addVertex(m, -len, 0.12f, 0f).setColor(tail).setLight(LIGHT);
			vc.addVertex(m, 0f, 0.18f, 0f).setColor(head).setLight(LIGHT);
			vc.addVertex(m, 0f, -0.18f, 0f).setColor(head).setLight(LIGHT);
		}
	}

	/** Cherry blossom petals drifting down across the sky. */
	private static void petals(VertexConsumer vc, Matrix4f base, float dist, float vis, double t) {
		for (int i = 0; i < 46; i++) {
			double period = 14 + rand(i, 1) * 10;
			float p = (float) ((t / period + rand(i, 2)) % 1.0);
			float elev = 75f - p * 72f;
			float yaw = rand(i, 3) * 360f + (float) Math.sin(t * 0.5 + i) * 3f;
			float fade = Math.min(1f, Math.min(p, 1f - p) * 8f);
			int rgb = rand(i, 4) > 0.5f ? 0xFFB7D5 : 0xFFE0EC;
			Matrix4f m = SkyGeometry.wordMatrix(base, yaw, elev, dist, (float) (t * (0.6 + rand(i, 5)) + i), dist * DEG);
			quad(vc, m, 1.5f, 0.9f, ARGB.color(vis * fade * 0.9f, rgb), false);
		}
	}

	private static void quad(VertexConsumer vc, Matrix4f m, float w, float h, int argb, boolean textured) {
		float x0 = -w / 2f;
		float x1 = w / 2f;
		float y0 = -h / 2f;
		float y1 = h / 2f;
		if (textured) {
			vc.addVertex(m, x0, y0, 0f).setColor(argb).setUv(0f, 0f).setLight(LIGHT);
			vc.addVertex(m, x0, y1, 0f).setColor(argb).setUv(0f, 1f).setLight(LIGHT);
			vc.addVertex(m, x1, y1, 0f).setColor(argb).setUv(1f, 1f).setLight(LIGHT);
			vc.addVertex(m, x1, y0, 0f).setColor(argb).setUv(1f, 0f).setLight(LIGHT);
		} else {
			vc.addVertex(m, x0, y0, 0f).setColor(argb).setLight(LIGHT);
			vc.addVertex(m, x0, y1, 0f).setColor(argb).setLight(LIGHT);
			vc.addVertex(m, x1, y1, 0f).setColor(argb).setLight(LIGHT);
			vc.addVertex(m, x1, y0, 0f).setColor(argb).setLight(LIGHT);
		}
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, Vector3f p, int argb) {
		vc.addVertex(m, p.x, p.y, p.z).setColor(argb).setLight(LIGHT);
	}

	/** Repeatable random number in [0, 1). */
	static float rand(int i, int salt) {
		long h = i * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 40) / (float) (1L << 24);
	}
}
