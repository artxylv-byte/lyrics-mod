package com.artweb.client;

import com.artweb.ArtWeb;
import com.artweb.client.page.Web;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pictures from web pages, downloaded in the background and turned into textures. Keeps a limited number and
 * frees the oldest. PNG, JPEG, GIF and BMP work; formats Java can't read (like WebP) are skipped.
 */
public final class ImageCache {
	public static final int MAX_SIDE = 512;
	private static final int MAX_IMAGES = 40;
	private static final int MAX_BYTES = 8 * 1024 * 1024;
	private static final String ACCEPT = "image/png,image/jpeg,image/gif,image/bmp;q=0.9,image/*;q=0.5";

	/** Goes up by one whenever a picture finishes loading, so pages know to lay themselves out again. */
	private static final AtomicInteger VERSION = new AtomicInteger();
	private static final AtomicInteger IDS = new AtomicInteger();
	/** Texture names that were freed and can be used again (render thread only), so render types aren't piled up. */
	private static final java.util.ArrayDeque<ResourceLocation> FREE_IDS = new java.util.ArrayDeque<>();

	public enum State {
		LOADING, READY, FAILED
	}

	public static final class Img {
		private volatile State state = State.LOADING;
		private volatile ResourceLocation texture;
		private volatile int width;
		private volatile int height;
		private volatile boolean dropped;

		public State state() {
			return state;
		}

		public ResourceLocation texture() {
			return texture;
		}

		public int width() {
			return width;
		}

		public int height() {
			return height;
		}
	}

	private static final Map<String, Img> IMAGES = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Img> eldest) {
			if (size() > MAX_IMAGES) {
				drop(eldest.getValue());
				return true;
			}
			return false;
		}
	};
	private static final ExecutorService LOADER = Executors.newFixedThreadPool(4, r -> {
		Thread t = new Thread(r, "ArtWeb image loader");
		t.setDaemon(true);
		return t;
	});

	private ImageCache() {
	}

	public static int version() {
		return VERSION.get();
	}

	/** The picture at this address, starting to download it if needed. */
	public static synchronized Img get(String src) {
		Img img = IMAGES.get(src);
		if (img == null) {
			img = new Img();
			IMAGES.put(src, img);
			Img target = img;
			LOADER.execute(() -> load(src, target));
		}
		return img;
	}

	/** Width and height of a picture that has loaded, or null (doesn't start a download). */
	public static synchronized int[] size(String src) {
		Img img = IMAGES.get(src);
		return img != null && img.state == State.READY ? new int[] {img.width, img.height} : null;
	}

	private static void load(String src, Img img) {
		NativeImage n = null;
		try {
			Web.Response r = Web.get(src, ACCEPT, MAX_BYTES);
			if (r.truncated() || r.status() >= 400) {
				throw new java.io.IOException("not available");
			}
			BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(r.body()));
			if (decoded == null) {
				throw new java.io.IOException("unsupported picture format");
			}
			int w = decoded.getWidth();
			int h = decoded.getHeight();
			float s = Math.min(1f, (float) MAX_SIDE / Math.max(w, h));
			int tw = Math.max(1, Math.round(w * s));
			int th = Math.max(1, Math.round(h * s));
			BufferedImage argb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = argb.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(decoded, 0, 0, tw, th, null);
			g.dispose();
			int[] px = argb.getRGB(0, 0, tw, th, null, 0, tw);
			n = new NativeImage(tw, th, false);
			for (int y = 0; y < th; y++) {
				for (int x = 0; x < tw; x++) {
					n.setPixel(x, y, px[y * tw + x]);
				}
			}
			NativeImage pixels = n;
			n = null;
			// Natural size of the picture (the layout scales it), texture size can be smaller.
			int naturalW = w;
			int naturalH = h;
			Minecraft.getInstance().execute(() -> upload(img, pixels, naturalW, naturalH));
		} catch (Exception e) {
			if (n != null) {
				n.close();
			}
			img.state = State.FAILED;
			if (!(e instanceof java.io.IOException)) {
				ArtWeb.LOGGER.debug("[ArtWeb] Couldn't load picture {}", src, e);
			}
		}
	}

	/** Render thread: makes the texture. */
	private static void upload(Img img, NativeImage pixels, int w, int h) {
		if (img.dropped) {
			pixels.close();
			return;
		}
		ResourceLocation id = FREE_IDS.isEmpty() ? ArtWeb.id("web_image/" + IDS.incrementAndGet()) : FREE_IDS.poll();
		DynamicTexture texture = new DynamicTexture(() -> "ArtWeb picture", pixels);
		texture.setFilter(true, false);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		img.texture = id;
		img.width = w;
		img.height = h;
		img.state = State.READY;
		VERSION.incrementAndGet();
	}

	private static void drop(Img img) {
		img.dropped = true;
		ResourceLocation id = img.texture;
		if (id != null) {
			Minecraft.getInstance().execute(() -> {
				Minecraft.getInstance().getTextureManager().release(id);
				FREE_IDS.add(id);
			});
		}
	}
}
