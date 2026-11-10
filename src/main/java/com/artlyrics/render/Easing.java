package com.artlyrics.render;

final class Easing {
	private Easing() {
	}

	static float clamp01(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	static float outCubic(float t) {
		float u = 1 - clamp01(t);
		return 1 - u * u * u;
	}

	/** Overshoots slightly before settling at 1: a soft "pop". */
	static float outBack(float t) {
		t = clamp01(t);
		float c1 = 1.70158f;
		float c3 = c1 + 1;
		float u = t - 1;
		return 1 + c3 * u * u * u + c1 * u * u;
	}
}
