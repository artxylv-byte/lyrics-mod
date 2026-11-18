package com.artlyrics.ui;

import com.artlyrics.cover.CoverImage;
import net.minecraft.client.Minecraft;
import com.artlyrics.compat.Mc;
import com.artlyrics.config.ArtlyricsConfig.TextFont;
import com.artlyrics.render.Fonts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** {@link Canvas} backed by Minecraft's {@link GuiGraphics}. */
public final class GuiCanvas implements Canvas {
	private final GuiGraphics graphics;
	private final Font font;
	private float alpha = 1f;
	/** The scale applied by {@link #scale} calls in effect, to know how many screen pixels a cover covers. */
	private float scale = 1f;
	private final java.util.ArrayDeque<Float> scales = new java.util.ArrayDeque<>();

	public GuiCanvas(GuiGraphics graphics, Font font) {
		this.graphics = graphics;
		this.font = font;
	}

	private int mul(int argb) {
		if (alpha >= 0.999f) {
			return argb;
		}
		int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static Component component(String text, TextFont font) {
		Component c = Component.literal(text);
		return font == null ? c : c.copy().withStyle(Fonts.uiStyle(font));
	}

	@Override
	public int width() {
		return graphics.guiWidth();
	}

	@Override
	public int height() {
		return graphics.guiHeight();
	}

	@Override
	public void fill(int x0, int y0, int x1, int y1, int argb) {
		if (x1 > x0 && y1 > y0) {
			graphics.fill(x0, y0, x1, y1, mul(argb));
		}
	}

	@Override
	public void gradientV(int x0, int y0, int x1, int y1, int topArgb, int bottomArgb) {
		graphics.fillGradient(x0, y0, x1, y1, mul(topArgb), mul(bottomArgb));
	}

	@Override
	public void text(String text, TextFont font, int x, int y, int argb, boolean shadow) {
		int color = mul(argb);
		if ((color >>> 24) < 4) {
			return;
		}
		graphics.drawString(this.font, component(text, font), x, y, color, shadow);
	}

	@Override
	public int textWidth(String text, TextFont font) {
		return this.font.width(component(text, font));
	}

	@Override
	public void icon(String name, int x, int y, int argb) {
		Mc.blitSprite(graphics, "icon/" + name, x, y, 10, 10, mul(argb));
	}

	@Override
	public void push() {
		graphics.pose().pushMatrix();
		scales.push(scale);
	}

	@Override
	public void translate(float x, float y) {
		graphics.pose().translate(x, y);
	}

	@Override
	public void scale(float s) {
		graphics.pose().scale(s, s);
		scale *= s;
	}

	@Override
	public void pop() {
		graphics.pose().popMatrix();
		scale = scales.isEmpty() ? 1f : scales.pop();
	}

	@Override
	public boolean cover(CoverImage image, long elapsedMs, int x, int y, int size) {
		int argb = mul(0xFFFFFFFF);
		if ((argb >>> 24) < 4) {
			return true;
		}
		int pixels = Math.round(size * scale * Minecraft.getInstance().getWindow().getGuiScale());
		return com.artlyrics.render.CoverTextures.INSTANCE.draw(graphics, image, elapsedMs, x, y, size, pixels, argb);
	}

	@Override
	public void clip(int x0, int y0, int x1, int y1) {
		graphics.enableScissor(x0, y0, x1, y1);
	}

	@Override
	public void unclip() {
		graphics.disableScissor();
	}

	@Override
	public float alpha() {
		return alpha;
	}

	@Override
	public void setAlpha(float alpha) {
		this.alpha = Math.max(0f, Math.min(1f, alpha));
	}
}
