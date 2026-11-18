package com.artlyrics.sky;

import java.util.Locale;

/**
 * The animated skies Artlyrics can paint over Minecraft's. Each one is drawn by the GPU, pixel by pixel, in
 * {@code assets/artlyrics/shaders/include/sky.glsl}; the order here is the id the shader uses, so new skies go
 * at the end of their group and the shader's list must match. The colors, star counts and pictures are only for
 * computers whose graphics card can't run the shader (and for offline previews).
 */
public enum SkyTheme {
	NONE(0, 0, 0, 0, null),
	MILKY_WAY(0x03050F, 0x0A1124, 0x151E33, 340, null),
	RINGED_PLANET(0x0B0A24, 0x1E1A4A, 0x3A3270, 260, "planet"),
	SPIRAL_GALAXY(0x07061A, 0x1A1040, 0x3A2266, 320, "galaxy"),
	AURORA(0x04101C, 0x0B2433, 0x16404A, 240, null),
	ECLIPSE(0x05060C, 0x14161F, 0x2A2C38, 220, "corona"),
	BLACK_HOLE(0x020204, 0x07070D, 0x101018, 300, null),
	NEBULA(0x090616, 0x1A0E30, 0x34204E, 280, "nebula"),
	TWIN_MOONS(0x061418, 0x10303A, 0x22525A, 220, "moon"),
	MOON_HALO(0x0A0F1C, 0x18213A, 0x2B3550, 120, "moon"),
	METEOR_SHOWER(0x060A1C, 0x101C3A, 0x22305A, 300, null),
	COMET(0x060A1C, 0x0E1638, 0x2A2A50, 260, null),
	LIGHT_PILLARS(0x0C0618, 0x22103A, 0x40205E, 200, null),
	STAR_RIFT(0x08040F, 0x1A0C30, 0x2E1648, 260, null),
	SKY_LANTERNS(0x050814, 0x1A1530, 0x5A2A30, 90, null),
	SAKURA_DUSK(0x8E7CC8, 0xD9A0D8, 0xFFC6D9, 40, null),
	GOLDEN_HOUR(0x3A62B0, 0xA894A0, 0xFFA052, 0, null),
	SYNTH_SUNSET(0x1A0B3A, 0x5A1C78, 0xE0479E, 120, "synth_sun");

	/** Dome colors from straight up to near the horizon. */
	public final int zenith;
	public final int middle;
	public final int horizon;
	public final int stars;
	public final String texture;

	SkyTheme(int zenith, int middle, int horizon, int stars, String texture) {
		this.zenith = zenith;
		this.middle = middle;
		this.horizon = horizon;
		this.stars = stars;
		this.texture = texture;
	}

	public String translationKey() {
		return "artlyrics.sky." + name().toLowerCase(Locale.ROOT);
	}

	/** Asset path of the sky's picture, or null. */
	public String texturePath() {
		return texture == null ? null : "textures/sky/" + texture + ".png";
	}

	/** Dome color at {@code t} (0 = horizon, 1 = straight up). */
	public int colorAt(float t) {
		return t < 0.5f ? lerp(horizon, middle, t * 2f) : lerp(middle, zenith, (t - 0.5f) * 2f);
	}

	static int lerp(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	/**
	 * How much of the sky to show for the time of day: 1 at night, fading out through dusk and dawn
	 * ({@code dayTime} is Minecraft's 0–24000 day clock, 0 = sunrise).
	 */
	public static float nightAmount(long dayTime) {
		float t = Math.floorMod(dayTime, 24000L);
		if (t >= 13800 && t <= 22200) {
			return 1f;
		}
		if (t > 12000 && t < 13800) {
			return smooth((t - 12000) / 1800f);
		}
		if (t > 22200) {
			return smooth(1f - (t - 22200) / 1800f);
		}
		return 0f;
	}

	private static float smooth(float x) {
		x = Math.max(0, Math.min(1, x));
		return x * x * (3 - 2 * x);
	}
}
