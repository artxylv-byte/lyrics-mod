package com.artlyrics.cover;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Turns picture files into {@link CoverImage}s: PNG, JPEG, BMP and GIF, including animated GIFs (every frame put
 * together the way browsers show them, with each frame's own delay). Big pictures are shrunk, since covers are
 * shown small.
 */
public final class CoverDecoder {
	/** Largest side kept for still covers and for animated ones (which have many frames to store). */
	static final int MAX_SIDE = 224;
	static final int MAX_SIDE_ANIMATED = 160;
	static final int MAX_FRAMES = 120;
	/** Frames that ask for (almost) no delay are shown this long, like browsers do. */
	private static final int DEFAULT_DELAY_MS = 100;

	private CoverDecoder() {
	}

	/** Reads a picture, or returns null if it isn't one Java can read (for example WebP or a video). */
	public static @Nullable CoverImage decode(byte[] data, String source) {
		if (data == null || data.length < 8) {
			return null;
		}
		try {
			if (isGif(data)) {
				CoverImage gif = decodeGif(data, source);
				if (gif != null) {
					return gif;
				}
			}
			BufferedImage still = ImageIO.read(new ByteArrayInputStream(data));
			if (still == null) {
				return null;
			}
			BufferedImage small = shrink(still, MAX_SIDE);
			return new CoverImage(small.getWidth(), small.getHeight(),
					List.of(small.getRGB(0, 0, small.getWidth(), small.getHeight(), null, 0, small.getWidth())),
					new int[] {1000}, source);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private static boolean isGif(byte[] d) {
		return d[0] == 'G' && d[1] == 'I' && d[2] == 'F';
	}

	private static @Nullable CoverImage decodeGif(byte[] data, String source) throws IOException {
		Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
		if (!readers.hasNext()) {
			return null;
		}
		ImageReader reader = readers.next();
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
			reader.setInput(in, false);
			int[] screen = logicalScreen(reader);
			List<BufferedImage> composed = new ArrayList<>();
			List<Integer> delays = new ArrayList<>();
			BufferedImage canvas = null;
			for (int i = 0; i < MAX_FRAMES; i++) {
				BufferedImage frame;
				IIOMetadata meta;
				try {
					frame = reader.read(i);
					meta = reader.getImageMetadata(i);
				} catch (IndexOutOfBoundsException e) {
					break; // no more frames
				} catch (IOException | RuntimeException e) {
					if (composed.isEmpty()) {
						throw e;
					}
					break; // a damaged frame: keep what played so far
				}
				Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
				int left = intAttr(child(root, "ImageDescriptor"), "imageLeftPosition", 0);
				int top = intAttr(child(root, "ImageDescriptor"), "imageTopPosition", 0);
				Node control = child(root, "GraphicControlExtension");
				String disposal = control == null ? "none" : attr(control, "disposalMethod", "none");
				int delay = (control == null ? 0 : intAttr(control, "delayTime", 0)) * 10;

				if (canvas == null) {
					int w = screen != null ? screen[0] : frame.getWidth() + left;
					int h = screen != null ? screen[1] : frame.getHeight() + top;
					canvas = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
				}
				BufferedImage before = disposal.equals("restoreToPrevious") ? copy(canvas) : null;
				Graphics2D g = canvas.createGraphics();
				g.drawImage(frame, left, top, null);
				g.dispose();

				composed.add(copy(canvas));
				delays.add(delay <= 10 ? DEFAULT_DELAY_MS : delay);

				if (disposal.equals("restoreToBackgroundColor")) {
					Graphics2D clear = canvas.createGraphics();
					clear.setComposite(AlphaComposite.Clear);
					clear.fillRect(left, top, frame.getWidth(), frame.getHeight());
					clear.dispose();
				} else if (before != null) {
					canvas = before;
				}
			}
			if (composed.isEmpty()) {
				return null;
			}
			int maxSide = composed.size() > 1 ? MAX_SIDE_ANIMATED : MAX_SIDE;
			List<int[]> frames = new ArrayList<>(composed.size());
			int w = 0;
			int h = 0;
			for (BufferedImage img : composed) {
				BufferedImage small = shrink(img, maxSide);
				w = small.getWidth();
				h = small.getHeight();
				frames.add(small.getRGB(0, 0, w, h, null, 0, w));
			}
			int[] delayArray = delays.stream().mapToInt(Integer::intValue).toArray();
			return new CoverImage(w, h, frames, delayArray, source);
		} finally {
			reader.dispose();
		}
	}

	/** The GIF's full picture size, or null if it doesn't say. */
	private static int[] logicalScreen(ImageReader reader) {
		try {
			IIOMetadata stream = reader.getStreamMetadata();
			if (stream == null) {
				return null;
			}
			Node desc = child(stream.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
			int w = intAttr(desc, "logicalScreenWidth", 0);
			int h = intAttr(desc, "logicalScreenHeight", 0);
			return w > 0 && h > 0 ? new int[] {w, h} : null;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** Shrinks a picture so its longer side is at most {@code maxSide} (never enlarges). */
	static BufferedImage shrink(BufferedImage image, int maxSide) {
		int w = image.getWidth();
		int h = image.getHeight();
		BufferedImage argb = image;
		if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
			argb = copy(image);
		}
		if (Math.max(w, h) <= maxSide) {
			return argb;
		}
		float s = (float) maxSide / Math.max(w, h);
		int tw = Math.max(1, Math.round(w * s));
		int th = Math.max(1, Math.round(h * s));
		BufferedImage out = argb;
		int cw = w;
		int ch = h;
		while (cw / 2 >= tw && ch / 2 >= th) {
			cw /= 2;
			ch /= 2;
			out = scaled(out, cw, ch);
		}
		return scaled(out, tw, th);
	}

	private static BufferedImage scaled(BufferedImage src, int w, int h) {
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	private static BufferedImage copy(BufferedImage src) {
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return out;
	}

	private static @Nullable Node child(@Nullable Node parent, String name) {
		if (parent == null) {
			return null;
		}
		for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeName().equals(name)) {
				return n;
			}
		}
		return null;
	}

	private static String attr(@Nullable Node node, String name, String fallback) {
		if (node == null) {
			return fallback;
		}
		NamedNodeMap attrs = node.getAttributes();
		Node a = attrs == null ? null : attrs.getNamedItem(name);
		return a == null ? fallback : a.getNodeValue();
	}

	private static int intAttr(@Nullable Node node, String name, int fallback) {
		try {
			return Integer.parseInt(attr(node, name, Integer.toString(fallback)));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
