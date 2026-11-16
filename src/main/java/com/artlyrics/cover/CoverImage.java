package com.artlyrics.cover;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * A song's cover picture: one frame, or several for an animated cover (GIF), each with how long it shows.
 * Pixels are ARGB, {@code width × height} per frame.
 */
public final class CoverImage {
	private final int width;
	private final int height;
	private final List<int[]> frames;
	private final int[] delaysMs;
	private final int totalMs;
	private final String source;

	public CoverImage(int width, int height, List<int[]> frames, int[] delaysMs, String source) {
		if (frames.isEmpty() || frames.size() != delaysMs.length) {
			throw new IllegalArgumentException("frames and delays don't match");
		}
		this.width = width;
		this.height = height;
		this.frames = List.copyOf(frames);
		this.delaysMs = delaysMs.clone();
		int total = 0;
		for (int d : this.delaysMs) {
			total += d;
		}
		this.totalMs = Math.max(1, total);
		this.source = source;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public int frameCount() {
		return frames.size();
	}

	public boolean animated() {
		return frames.size() > 1;
	}

	public int[] frame(int index) {
		return frames.get(index);
	}

	public int delayMs(int index) {
		return delaysMs[index];
	}

	/** Where the picture came from, e.g. "Spotify" or "covers folder". */
	public String source() {
		return source;
	}

	/** The frame to show {@code elapsedMs} after the animation started (it loops). */
	public int frameAt(long elapsedMs) {
		if (frames.size() == 1) {
			return 0;
		}
		long t = Math.floorMod(elapsedMs, (long) totalMs);
		for (int i = 0; i < delaysMs.length; i++) {
			t -= delaysMs[i];
			if (t < 0) {
				return i;
			}
		}
		return delaysMs.length - 1;
	}

	/**
	 * Every frame resized to a {@code side × side} square (cropping the middle of non-square pictures), smoothly,
	 * so it can be drawn pixel for pixel at that size and stay sharp.
	 */
	public int[][] squareFrames(int side) {
		int[][] out = new int[frames.size()][];
		int crop = Math.min(width, height);
		int cx = (width - crop) / 2;
		int cy = (height - crop) / 2;
		for (int f = 0; f < frames.size(); f++) {
			BufferedImage src = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			src.setRGB(0, 0, width, height, frames.get(f), 0, width);
			BufferedImage square = src.getSubimage(cx, cy, crop, crop);
			out[f] = resize(square, side).getRGB(0, 0, side, side, null, 0, side);
		}
		return out;
	}

	/** Smooth resize: halves repeatedly before the last step, which keeps downscaled pictures from looking grainy. */
	static BufferedImage resize(BufferedImage image, int side) {
		BufferedImage current = image;
		int w = image.getWidth();
		int h = image.getHeight();
		while (w / 2 >= side && h / 2 >= side) {
			w /= 2;
			h /= 2;
			current = draw(current, w, h);
		}
		return draw(current, side, side);
	}

	private static BufferedImage draw(BufferedImage src, int w, int h) {
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return out;
	}
}
