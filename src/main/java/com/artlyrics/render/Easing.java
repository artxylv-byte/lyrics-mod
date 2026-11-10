package com.artlyrics.render;

public final class Easing {
	private Easing() {
	}

	public static float clamp01(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	public static float outCubic(float t) {
		float u = 1 - clamp01(t);
		return 1 - u * u * u;
	}

	/** Smooth start and end. */
	public static float inOutSine(float t) {
		return (float) (0.5 - 0.5 * Math.cos(Math.PI * clamp01(t)));
	}

	/** Exponential smoothing factor for a frame of {@code dtSeconds} at {@code rate} per second. */
	public static float approach(float dtSeconds, float rate) {
		return 1f - (float) Math.exp(-dtSeconds * rate);
	}

	/** Overshoots slightly before settling at 1: a soft "pop". */
	public static float outBack(float t) {
		t = clamp01(t);
		float c1 = 1.70158f;
		float c3 = c1 + 1;
		float u = t - 1;
		return 1 + c3 * u * u * u + c1 * u * u;
	}
}
