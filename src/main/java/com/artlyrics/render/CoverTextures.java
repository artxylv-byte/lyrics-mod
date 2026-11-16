package com.artlyrics.render;

import com.artlyrics.compat.Mc;
import com.artlyrics.cover.CoverImage;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cover pictures as textures. Each cover is resized to exactly the number of screen pixels it's drawn at (so it's
 * sharp at any GUI scale) and all frames of an animated cover go into one sheet. Preparing that happens in the
 * background; only the upload is done on the render thread. A few textures are kept and the rest freed.
 */
public final class CoverTextures {
	public static final CoverTextures INSTANCE = new CoverTextures();

	private static final int MAX_TEXTURES = 4;
	private static final int MAX_SHEET = 2048;

	private record Key(CoverImage image, int side) {
	}

	/** A finished sheet: frames left to right, top to bottom. */
	private static final class Sheet {
		final int side;
		final int columns;
		final int width;
		final int height;
		NativeImage pixels;
		Mc.Tex texture;

		Sheet(int side, int columns, int width, int height, NativeImage pixels) {
			this.side = side;
			this.columns = columns;
			this.width = width;
			this.height = height;
			this.pixels = pixels;
		}
	}

	private final Map<Key, CompletableFuture<Sheet>> sheets = new LinkedHashMap<>(8, 0.75f, true);
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Artlyrics-CoverTextures");
		t.setDaemon(true);
		return t;
	});
	private int serial;

	private CoverTextures() {
	}

	/**
	 * Draws the cover's frame for {@code elapsedMs} into the square (x, y, size) in GUI pixels; {@code pixels} is
	 * how many screen pixels that square covers. Returns false while the texture is still being prepared.
	 */
	public boolean draw(GuiGraphics graphics, CoverImage image, long elapsedMs, int x, int y, int size, int pixels, int argb) {
		int columns = (int) Math.ceil(Math.sqrt(image.frameCount()));
		int side = Math.max(8, Math.min(Math.min(256, MAX_SHEET / columns), (pixels + 7) / 8 * 8));
		Key key = new Key(image, side);
		CompletableFuture<Sheet> future = sheets.get(key);
		if (future == null) {
			future = CompletableFuture.supplyAsync(() -> build(image, side, columns), worker);
			sheets.put(key, future);
			trim();
		}
		if (future.isCompletedExceptionally()) {
			return false;
		}
		Sheet sheet = future.getNow(null);
		if (sheet == null) {
			return false;
		}
		if (sheet.texture == null) {
			sheet.texture = Mc.registerTexture("cover/" + (++serial), sheet.pixels);
			sheet.pixels = null; // owned by the texture now
		}
		int frame = image.frameAt(elapsedMs);
		float u = (frame % sheet.columns) * sheet.side;
		float v = (frame / sheet.columns) * sheet.side;
		Mc.blitTexture(graphics, sheet.texture, x, y, size, size, u, v, sheet.side, sheet.side, sheet.width, sheet.height, argb);
		return true;
	}

	private static Sheet build(CoverImage image, int side, int columns) {
		int[][] frames = image.squareFrames(side);
		int rows = (frames.length + columns - 1) / columns;
		int width = columns * side;
		int height = rows * side;
		NativeImage pixels = new NativeImage(width, height, false);
		for (int f = 0; f < frames.length; f++) {
			int ox = (f % columns) * side;
			int oy = (f / columns) * side;
			int[] px = frames[f];
			for (int py = 0; py < side; py++) {
				for (int pxl = 0; pxl < side; pxl++) {
					pixels.setPixel(ox + pxl, oy + py, px[py * side + pxl]);
				}
			}
		}
		return new Sheet(side, columns, width, height, pixels);
	}

	/** Frees the least recently drawn textures beyond the limit. Render thread. */
	private void trim() {
		Iterator<Map.Entry<Key, CompletableFuture<Sheet>>> it = sheets.entrySet().iterator();
		while (sheets.size() > MAX_TEXTURES && it.hasNext()) {
			CompletableFuture<Sheet> old = it.next().getValue();
			it.remove();
			old.thenAccept(sheet -> net.minecraft.client.Minecraft.getInstance().execute(() -> free(sheet)));
		}
	}

	private static void free(Sheet sheet) {
		if (sheet.texture != null) {
			Mc.releaseTexture(sheet.texture);
			sheet.texture = null;
		} else if (sheet.pixels != null) {
			sheet.pixels.close();
			sheet.pixels = null;
		}
	}
}
