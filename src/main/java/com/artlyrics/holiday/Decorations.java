package com.artlyrics.holiday;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a theme's little decorations are at a given moment: snow drifting down, hearts and lanterns floating up,
 * stars twinkling, fireworks bursting. Nothing is stored; every position comes from the time and each
 * decoration's own fixed random numbers, so the motion is smooth and costs nothing to keep.
 */
public final class Decorations {
	/** One decoration in a {@code width × height} box (top-left origin). */
	public record Piece(float x, float y, String glyph, int color, float alpha, float scale, float rotation) {
	}

	private Decorations() {
	}

	public static List<Piece> at(HolidayTheme theme, int[] colors, int count, double seconds, float width, float height) {
		List<Piece> out = new ArrayList<>(count);
		String[] glyphs = theme.glyphs();
		if (theme.motion() == HolidayTheme.Motion.BURST) {
			bursts(out, glyphs, colors, count, seconds, width, height);
			return out;
		}
		for (int i = 0; i < count; i++) {
			float r1 = rand(i, 1);
			float r2 = rand(i, 2);
			float r3 = rand(i, 3);
			String glyph = glyphs[(int) (rand(i, 4) * glyphs.length) % glyphs.length];
			int color = colors[(int) (rand(i, 5) * colors.length) % colors.length];
			float scale = 0.6f + 0.6f * r3;
			switch (theme.motion()) {
				case FALL, RISE -> {
					double period = 7 + 6 * r2; // seconds to cross the box
					double phase = (seconds / period + r1) % 1.0;
					float y = (float) (theme.motion() == HolidayTheme.Motion.FALL ? phase : 1 - phase) * (height + 16) - 8;
					float x = r3 * width + (float) Math.sin(seconds * (0.6 + r2) + i) * 6f;
					// Fade in and out at the edges instead of popping.
					float edge = (float) Math.min(phase, 1 - phase);
					float alpha = Math.min(1f, edge * 6f);
					float rotation = (float) Math.sin(seconds * 0.9 + i * 1.7) * 0.6f;
					out.add(new Piece(x, y, glyph, color, alpha, scale, rotation));
				}
				case TWINKLE -> {
					float x = r1 * width + (float) Math.sin(seconds * 0.3 + i) * 3f;
					float y = r2 * height + (float) Math.cos(seconds * 0.25 + i * 1.3) * 2f;
					double wave = 0.5 + 0.5 * Math.sin(seconds * (1.2 + 1.6 * r3) + i * 2.1);
					out.add(new Piece(x, y, glyph, color, (float) (0.15 + 0.85 * wave * wave), scale, 0f));
				}
				default -> {
				}
			}
		}
		return out;
	}

	/** Fireworks: a few bursts at a time, each a ring of sparks flying out and fading. */
	private static void bursts(List<Piece> out, String[] glyphs, int[] colors, int count, double seconds, float width, float height) {
		int perBurst = 7;
		int bursts = Math.max(1, count / perBurst);
		double period = 2.6;
		for (int b = 0; b < bursts; b++) {
			double t = seconds / period + b / (double) bursts;
			long cycle = (long) Math.floor(t);
			float phase = (float) (t - cycle);
			int seed = (int) (cycle * 31 + b * 7);
			float cx = (0.1f + 0.8f * rand(seed, 11)) * width;
			float cy = (0.15f + 0.6f * rand(seed, 12)) * height;
			int color = colors[(int) (rand(seed, 13) * colors.length) % colors.length];
			float radius = (10 + 10 * rand(seed, 14)) * (1 - (1 - phase) * (1 - phase));
			float alpha = phase < 0.1f ? phase * 10f : 1f - (phase - 0.1f) / 0.9f;
			for (int j = 0; j < perBurst; j++) {
				double angle = Math.PI * 2 * j / perBurst + rand(seed, j + 20) * 0.5;
				float x = cx + (float) Math.cos(angle) * radius;
				float y = cy + (float) Math.sin(angle) * radius + phase * phase * 4f; // sparks sink a little
				String glyph = glyphs[j % glyphs.length];
				out.add(new Piece(x, y, glyph, color, Math.max(0f, alpha), 0.7f - 0.3f * phase, 0f));
			}
		}
	}

	/** A repeatable random number in [0, 1) for decoration {@code i}. */
	static float rand(int i, int salt) {
		long h = i * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 40) / (float) (1L << 24);
	}
}
