package com.artlyrics.ui;

import com.artlyrics.config.ArtlyricsConfig.TextFont;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Canvas} that draws into an image with Java2D, for previewing the UI without the game. Text uses
 * Minecraft's own bitmap glyph sheets (from the Minecraft jar on the test classpath) with the game's spacing
 * rules, and the mod's bundled TrueType fonts at the sizes from their font definitions.
 */
final class JavaCanvas implements Canvas {
	private final int width;
	private final int height;
	private final BufferedImage image;
	private final Graphics2D g;
	private final Deque<AffineTransform> stack = new ArrayDeque<>();
	private float alpha = 1f;

	private static final Map<Integer, Glyph> GLYPHS = new HashMap<>();
	private static final Map<Integer, Float> SPACES = new HashMap<>();
	private static final Map<TextFont, Font> TTF = new EnumMap<>(TextFont.class);
	private static final Map<String, BufferedImage> ICONS = new HashMap<>();
	private static final Font FALLBACK = new Font(Font.SANS_SERIF, Font.PLAIN, 8);

	/** One bitmap glyph: its pixels (cell-sized), scale from cell to font pixels, and vertical offset. */
	private record Glyph(BufferedImage sheet, int sx, int sy, int cellW, int cellH, float scale, int ascent, int advance) {
	}

	JavaCanvas(int width, int height, int pixelScale) {
		this.width = width;
		this.height = height;
		this.image = new BufferedImage(width * pixelScale, height * pixelScale, BufferedImage.TYPE_INT_ARGB);
		this.g = image.createGraphics();
		g.scale(pixelScale, pixelScale);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		loadFonts();
	}

	/** Paints a sunset sky with a dark horizon, standing in for the world behind the UI. */
	void paintBackdrop() {
		g.setPaint(new GradientPaint(0, 0, new Color(0xFF8A3C), 0, height * 0.7f, new Color(0xFFC46A)));
		g.fillRect(0, 0, width, height);
		g.setPaint(new Color(0x3E6B2E));
		g.fillRect(0, (int) (height * 0.72), width, height);
	}

	void save(Path path) throws IOException {
		Files.createDirectories(path.getParent());
		ImageIO.write(image, "png", path.toFile());
	}

	private Color color(int argb) {
		int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
		return new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, Math.max(0, Math.min(255, a)));
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public int height() {
		return height;
	}

	@Override
	public void fill(int x0, int y0, int x1, int y1, int argb) {
		if (x1 <= x0 || y1 <= y0) {
			return;
		}
		g.setPaint(color(argb));
		g.fillRect(x0, y0, x1 - x0, y1 - y0);
	}

	@Override
	public void gradientV(int x0, int y0, int x1, int y1, int topArgb, int bottomArgb) {
		g.setPaint(new GradientPaint(0, y0, color(topArgb), 0, y1, color(bottomArgb)));
		g.fillRect(x0, y0, x1 - x0, y1 - y0);
	}

	@Override
	public void text(String text, TextFont font, int x, int y, int argb, boolean shadow) {
		if (shadow) {
			int dark = (argb & 0xFF000000) | ((((argb >> 16) & 0xFF) / 4) << 16) | ((((argb >> 8) & 0xFF) / 4) << 8) | ((argb & 0xFF) / 4);
			drawText(text, font, x + 1, y + 1, dark);
		}
		drawText(text, font, x, y, argb);
	}

	private void drawText(String text, TextFont font, float x, float y, int argb) {
		Font ttf = font == null ? null : TTF.get(font);
		Color c = color(argb);
		if (ttf != null) {
			g.setFont(ttf);
			g.setPaint(c);
			g.drawString(text, x, y + 7);
			return;
		}
		float cx = x;
		for (int cp : text.codePoints().toArray()) {
			cx += drawBitmapChar(cp, cx, y, c);
		}
	}

	/** Draws one character in Minecraft's bitmap font and returns its advance. */
	private float drawBitmapChar(int cp, float x, float y, Color c) {
		Float space = SPACES.get(cp);
		if (space != null) {
			return space;
		}
		Glyph glyph = GLYPHS.get(cp);
		if (glyph == null) {
			g.setFont(FALLBACK);
			g.setPaint(c);
			String s = new String(Character.toChars(cp));
			g.drawString(s, x, y + 7);
			return g.getFontMetrics(FALLBACK).stringWidth(s) + 1;
		}
		g.setPaint(c);
		float top = y + 7 - glyph.ascent;
		for (int py = 0; py < glyph.cellH; py++) {
			for (int px = 0; px < glyph.cellW; px++) {
				int pixel = glyph.sheet.getRGB(glyph.sx + px, glyph.sy + py);
				if ((pixel >>> 24) > 0) {
					g.fill(new java.awt.geom.Rectangle2D.Float(x + px * glyph.scale, top + py * glyph.scale, glyph.scale, glyph.scale));
				}
			}
		}
		return glyph.advance;
	}

	@Override
	public int textWidth(String text, TextFont font) {
		Font ttf = font == null ? null : TTF.get(font);
		if (ttf != null) {
			FontMetrics m = g.getFontMetrics(ttf);
			return Math.round((float) ttf.getStringBounds(text, m.getFontRenderContext()).getWidth());
		}
		float w = 0;
		for (int cp : text.codePoints().toArray()) {
			Float space = SPACES.get(cp);
			Glyph glyph = GLYPHS.get(cp);
			if (space != null) {
				w += space;
			} else if (glyph != null) {
				w += glyph.advance;
			} else {
				w += g.getFontMetrics(FALLBACK).stringWidth(new String(Character.toChars(cp))) + 1;
			}
		}
		return Math.round(w);
	}

	@Override
	public void icon(String name, int x, int y, int argb) {
		BufferedImage icon = ICONS.computeIfAbsent(name, n -> readImage("/assets/artlyrics/textures/gui/sprites/icon/" + n + ".png"));
		if (icon == null) {
			return;
		}
		Color tint = color(argb);
		for (int py = 0; py < icon.getHeight(); py++) {
			for (int px = 0; px < icon.getWidth(); px++) {
				int a = icon.getRGB(px, py) >>> 24;
				if (a > 0) {
					g.setPaint(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), tint.getAlpha() * a / 255));
					g.fillRect(x + px, y + py, 1, 1);
				}
			}
		}
	}

	@Override
	public void push() {
		stack.push(g.getTransform());
	}

	@Override
	public void translate(float x, float y) {
		g.translate(x, y);
	}

	@Override
	public void scale(float s) {
		g.scale(s, s);
	}

	@Override
	public void pop() {
		g.setTransform(stack.pop());
	}

	@Override
	public void clip(int x0, int y0, int x1, int y1) {
		g.setClip(x0, y0, x1 - x0, y1 - y0);
	}

	@Override
	public void unclip() {
		g.setClip(null);
	}

	@Override
	public float alpha() {
		return alpha;
	}

	@Override
	public void setAlpha(float alpha) {
		this.alpha = Math.max(0f, Math.min(1f, alpha));
		g.setComposite(AlphaComposite.SrcOver);
	}

	// --------------------------------------------------------------------------------------------------------

	private static synchronized void loadFonts() {
		if (!GLYPHS.isEmpty()) {
			return;
		}
		JsonObject include = readJson("/assets/minecraft/font/include/default.json");
		for (JsonElement el : include.getAsJsonArray("providers")) {
			JsonObject p = el.getAsJsonObject();
			if (!"bitmap".equals(p.get("type").getAsString())) {
				continue;
			}
			String file = p.get("file").getAsString().replace("minecraft:", "");
			BufferedImage sheet = readImage("/assets/minecraft/textures/" + file);
			if (sheet == null) {
				continue;
			}
			JsonArray rows = p.getAsJsonArray("chars");
			int fontHeight = p.has("height") ? p.get("height").getAsInt() : 8;
			int ascent = p.get("ascent").getAsInt();
			int rowCount = rows.size();
			int colCount = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
			int cellW = sheet.getWidth() / colCount;
			int cellH = sheet.getHeight() / rowCount;
			float scale = fontHeight / (float) cellH;
			for (int r = 0; r < rowCount; r++) {
				int[] cps = rows.get(r).getAsString().codePoints().toArray();
				for (int col = 0; col < cps.length; col++) {
					int cp = cps[col];
					if (cp == 0 || GLYPHS.containsKey(cp)) {
						continue;
					}
					int width = 0;
					for (int px = cellW - 1; px >= 0 && width == 0; px--) {
						for (int py = 0; py < cellH; py++) {
							if ((sheet.getRGB(col * cellW + px, r * cellH + py) >>> 24) > 0) {
								width = px + 1;
								break;
							}
						}
					}
					int advance = (int) (0.5f + width * scale) + 1;
					GLYPHS.put(cp, new Glyph(sheet, col * cellW, r * cellH, cellW, cellH, scale, ascent, advance));
				}
			}
		}
		JsonObject space = readJson("/assets/minecraft/font/include/space.json");
		for (JsonElement el : space.getAsJsonArray("providers")) {
			JsonObject advances = el.getAsJsonObject().getAsJsonObject("advances");
			for (Map.Entry<String, JsonElement> e : advances.entrySet()) {
				SPACES.put(e.getKey().codePointAt(0), e.getValue().getAsFloat());
			}
		}
		for (TextFont font : TextFont.values()) {
			if (font.bundled == null) {
				continue;
			}
			try (InputStream in = JavaCanvas.class.getResourceAsStream("/assets/artlyrics/font/" + font.bundled + ".ttf")) {
				float size = readJson("/assets/artlyrics/font/" + font.bundled + "_ui.json")
						.getAsJsonArray("providers").get(0).getAsJsonObject().get("size").getAsFloat();
				TTF.put(font, Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(size));
			} catch (Exception e) {
				throw new IllegalStateException("Couldn't load font " + font, e);
			}
		}
	}

	private static JsonObject readJson(String path) {
		try (InputStream in = JavaCanvas.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Missing resource " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static BufferedImage readImage(String path) {
		try (InputStream in = JavaCanvas.class.getResourceAsStream(path)) {
			return in == null ? null : ImageIO.read(in);
		} catch (IOException e) {
			return null;
		}
	}
}
