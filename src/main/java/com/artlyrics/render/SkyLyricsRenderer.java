package com.artlyrics.render;

import com.artlyrics.Artlyrics;
import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.LyricStyle;
import com.artlyrics.lyrics.LyricLine;
import com.artlyrics.lyrics.LyricWord;
import com.artlyrics.lyrics.Lyrics;
import com.artlyrics.nowplaying.NowPlaying;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws the lyrics as 3D text in the world.
 *
 * <p>Skywriting: each line is written out in the sky in the direction you were looking when it started, at a
 * fixed angle above the horizon, and slowly rises while it's up. Positions are relative to the camera (like the
 * sky itself), so the text never gets closer as you walk, but terrain can still cover it. Every word faces the camera on
 * its own, so lines curve around you.
 *
 * <p>Hover: lines hang a few blocks away just above the crosshair, trail your view smoothly and are drawn on top
 * of everything.
 */
public final class SkyLyricsRenderer {
	private static final long FADE_NANOS = 650_000_000L;
	private static final int MAX_SHOWN = 4;

	/** Angular size of one font pixel, in radians, at size 1.0. */
	private static final float SKY_RADIANS_PER_PX = 0.0042f;
	private static final float HOVER_RADIANS_PER_PX = 0.005f;
	private static final float SKY_MAX_ROW_PX = 200f;
	private static final float HOVER_MAX_ROW_PX = 180f;
	private static final float HOVER_DISTANCE = 4.0f;
	/** Hovering lines sit this many degrees above the crosshair. */
	private static final float HOVER_RAISE_DEG = 9f;

	private final List<ShownLine> shown = new ArrayList<>();
	private float hoverYaw;
	private float hoverElev;
	private boolean hoverInitialized;
	private long lastFrameNanos;
	private boolean hasFrame;

	private static final class ShownLine {
		final Lyrics lyrics;
		final int index;
		final LyricLine line;
		final float anchorYaw;
		final float anchorElev;
		long fadeStartNanos;
		LineLayout layout;
		ArtlyricsConfig.DisplayMode layoutMode;

		ShownLine(Lyrics lyrics, int index, LyricLine line, float anchorYaw, float anchorElev) {
			this.lyrics = lyrics;
			this.index = index;
			this.line = line;
			this.anchorYaw = anchorYaw;
			this.anchorElev = anchorElev;
		}

		boolean fading() {
			return fadeStartNanos != 0;
		}
	}

	public void render(WorldRenderContext context) {
		Artlyrics mod = Artlyrics.get();
		Minecraft mc = Minecraft.getInstance();
		if (mod == null || mc.level == null) {
			shown.clear();
			return;
		}
		ArtlyricsConfig config = mod.config();
		long now = System.nanoTime();
		mod.tracker().frame(now);

		Camera camera = mc.gameRenderer.getMainCamera();
		float dt = hasFrame ? Math.min(0.25f, (now - lastFrameNanos) / 1e9f) : 0f;
		lastFrameNanos = now;
		hasFrame = true;
		followCamera(camera, dt);

		if (!config.enabled) {
			shown.clear();
			return;
		}

		long timeMs = mod.tracker().positionMs() + config.offsetMs;
		updateShownLines(mod, camera, timeMs, now);
		if (shown.isEmpty()) {
			return;
		}

		PoseStack poseStack = context.matrices();
		MultiBufferSource buffers = context.consumers();
		Font font = mc.font;
		double seconds = now / 1e9;
		for (ShownLine line : shown) {
			drawLine(line, config, mod.settingsRevision(), font, poseStack, buffers, timeMs, now, seconds);
		}
	}

	/** Hover anchor: eases towards where the camera looks, so text trails your view a little. */
	private void followCamera(Camera camera, float dt) {
		float targetYaw = camera.yRot();
		float targetElev = -camera.xRot() + HOVER_RAISE_DEG;
		if (!hoverInitialized) {
			hoverYaw = targetYaw;
			hoverElev = targetElev;
			hoverInitialized = true;
			return;
		}
		float k = 1f - (float) Math.exp(-dt * 7.0);
		hoverYaw += Mth.wrapDegrees(targetYaw - hoverYaw) * k;
		hoverElev += (targetElev - hoverElev) * k;
	}

	private void updateShownLines(Artlyrics mod, Camera camera, long timeMs, long now) {
		Lyrics lyrics = mod.lyrics().lyrics();
		NowPlaying track = mod.tracker().track();

		int index = -1;
		LyricLine line = null;
		if (lyrics != null && track != null) {
			index = lyrics.lineIndexAt(timeMs);
			if (index >= 0) {
				line = lyrics.lines().get(index);
				if (line.isBlank() || timeMs >= line.visibleUntil()) {
					line = null;
				}
			}
		}

		boolean alreadyShown = false;
		for (ShownLine s : shown) {
			if (s.fading()) {
				continue;
			}
			if (line != null && s.lyrics == lyrics && s.index == index) {
				alreadyShown = true;
			} else {
				s.fadeStartNanos = now;
			}
		}

		if (line != null && !alreadyShown) {
			ArtlyricsConfig config = mod.config();
			// Small, repeatable variation so consecutive lines don't stack exactly on top of each other.
			float jitterYaw = ((index * 37) % 17 - 8) * 0.9f;
			float jitterElev = ((index * 53) % 9 - 4) * 0.7f;
			shown.add(new ShownLine(lyrics, index, line, camera.yRot() + jitterYaw, config.skyHeight + jitterElev));
		}

		Iterator<ShownLine> it = shown.iterator();
		while (it.hasNext()) {
			ShownLine s = it.next();
			if (s.fading() && now - s.fadeStartNanos > FADE_NANOS) {
				it.remove();
			}
		}
		while (shown.size() > MAX_SHOWN) {
			shown.remove(0);
		}
	}

	private void drawLine(ShownLine shownLine, ArtlyricsConfig config, int revision, Font font, PoseStack poseStack,
						  MultiBufferSource buffers, long timeMs, long now, double seconds) {
		boolean sky = config.displayMode == ArtlyricsConfig.DisplayMode.SKYWRITING;
		if (shownLine.layout == null || shownLine.layout.revision != revision || shownLine.layoutMode != config.displayMode) {
			shownLine.layout = LineLayout.build(shownLine.line, font, config, sky ? SKY_MAX_ROW_PX : HOVER_MAX_ROW_PX, revision);
			shownLine.layoutMode = config.displayMode;
		}
		LyricLine line = shownLine.line;

		float fade = shownLine.fading() ? 1f - Easing.clamp01((now - shownLine.fadeStartNanos) / (float) FADE_NANOS) : 1f;
		float leaving = 1f - fade;
		// Skywriting rises slowly while it's up, like smoke trails drifting off; leaving lines lift away faster.
		float drift = sky ? Math.min(2.5f, Math.max(0f, (timeMs - line.startMs()) / 1000f) * 0.25f) : 0f;

		Placement p = new Placement();
		p.sky = sky;
		p.yaw = sky ? shownLine.anchorYaw : hoverYaw;
		p.elev = (sky ? shownLine.anchorElev : hoverElev) + drift + leaving * (sky ? 3f : 2f);
		p.distance = sky ? skyDistance() : HOVER_DISTANCE;
		p.radPerPx = (sky ? SKY_RADIANS_PER_PX : HOVER_RADIANS_PER_PX) * config.size;
		p.bendPx = config.arc ? (sky ? 10f : 5f) : 0f;
		p.effect = config.textEffect;

		List<LyricWord> words = line.words();
		int n = words.size();
		LyricStyle look = config.style;
		LineLayout layout = shownLine.layout;

		if (config.animation == ArtlyricsConfig.Animation.SPOTLIGHT) {
			int wi = line.wordIndexAt(timeMs);
			LineLayout.WordBox box = wi < 0 ? null : findBox(layout, wi);
			if (box == null) {
				return;
			}
			float in = Easing.clamp01((timeMs - words.get(wi).startMs()) / 240f);
			float scale = 2.2f * (0.85f + 0.15f * Easing.outCubic(in));
			float alpha = Easing.outCubic(in) * fade;
			int rgb = LyricStyle.lerpRgb(look.colorAt(n > 1 ? wi / (float) (n - 1) : 0.5f, seconds), look.accent, 0.3f);
			drawWord(poseStack, buffers, font, box, box.text(), 0f, 0f, 1f, scale, 0f, ARGB.color(alpha, rgb), p);
			return;
		}

		for (LineLayout.WordBox box : layout.words) {
			LyricWord w = words.get(box.index());
			int base = look.colorAt(n > 1 ? box.index() / (float) (n - 1) : 0.5f, seconds);
			boolean sung = timeMs >= w.startMs();
			boolean active = w.isActiveAt(timeMs);
			long since = timeMs - w.startMs();

			Component text = box.text();
			int rgb = base;
			float alpha = 1f;
			float scale = 1f;
			float rise = 0f;
			switch (config.animation) {
				case BLOOM -> {
					if (!sung) {
						continue;
					}
					// Opens up from small and bright, then settles into the palette color.
					float in = Easing.clamp01(since / 320f);
					alpha = Easing.outCubic(in);
					scale = 0.55f + 0.45f * Easing.outBack(in);
					rgb = LyricStyle.lerpRgb(look.accent, base, Easing.clamp01(since / 500f));
				}
				case TYPEWRITER -> {
					if (!sung) {
						continue;
					}
					int letters = box.raw().codePointCount(0, box.raw().length());
					long perLetter = Math.max(25, Math.min(90, (w.endMs() - w.startMs()) / Math.max(1, letters)));
					int visible = (int) Math.min(letters, 1 + since / perLetter);
					if (visible < letters) {
						int cut = box.raw().offsetByCodePoints(0, visible);
						text = Component.literal(box.raw().substring(0, cut)).withStyle(layout.style);
					}
					if (active) {
						rgb = LyricStyle.lerpRgb(base, look.accent, 0.4f);
					}
				}
				default -> { // SWEEP
					alpha = Easing.clamp01((timeMs - line.startMs()) / 300f) * (sung ? 1f : 0.35f);
					if (!sung) {
						rgb = LyricStyle.lerpRgb(base, 0x7C7F8C, 0.5f);
					} else if (active) {
						rgb = LyricStyle.lerpRgb(base, look.accent, 0.5f);
						rise = -1.5f * (1f - Easing.clamp01(since / 200f));
					}
				}
			}
			alpha *= fade;
			if (alpha <= 0.02f) {
				continue;
			}
			drawWord(poseStack, buffers, font, box, text, box.xCenter(), box.y(), box.rowHalfWidth(), scale, rise, ARGB.color(alpha, rgb), p);
		}
	}

	private static LineLayout.WordBox findBox(LineLayout layout, int index) {
		for (LineLayout.WordBox b : layout.words) {
			if (b.index() == index) {
				return b;
			}
		}
		return null;
	}

	/** Where and how big the current line is drawn. */
	private static final class Placement {
		boolean sky;
		float yaw;
		float elev;
		float distance;
		float radPerPx;
		float bendPx;
		ArtlyricsConfig.TextEffect effect;
	}

	private static final int[][] OUTLINE_OFFSETS = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

	/**
	 * Draws {@code text} at a word's place. The word is positioned by its full width ({@code box}), so partially
	 * typed words grow to the right instead of shifting around.
	 */
	private static void drawWord(PoseStack poseStack, MultiBufferSource buffers, Font font, LineLayout.WordBox box, Component text,
								 float xCenterPx, float yPx, float rowHalfWidth, float scale, float risePx, int argb, Placement p) {
		// Arc: words in the middle of a row sit a little higher than the ends.
		float u = Mth.clamp(xCenterPx / rowHalfWidth, -1f, 1f);
		float arcPx = p.bendPx * (1f - u * u);
		float slope = rowHalfWidth > 1f ? -2f * u * p.bendPx / rowHalfWidth : 0f;

		float yawDeg = p.yaw + (float) Math.toDegrees(xCenterPx * p.radPerPx);
		float elevDeg = p.elev + (float) Math.toDegrees((arcPx - yPx) * p.radPerPx);
		float roll = slope != 0f ? (float) Math.atan(slope) * 0.6f : 0f;
		Matrix4f matrix = SkyGeometry.wordMatrix(poseStack.last().pose(), yawDeg, elevDeg, p.distance, roll,
				p.distance * p.radPerPx * scale);

		float left = -box.width() / 2f;
		float top = -4.5f + risePx / scale;
		int alpha = ARGB.alpha(argb);
		int light = LightTexture.FULL_BRIGHT;
		int outline = ARGB.color(alpha, LyricStyle.lerpRgb(argb & 0xFFFFFF, 0x000000, 0.78f));

		if (p.sky) {
			switch (p.effect) {
				case OUTLINE -> font.drawInBatch8xOutline(text.getVisualOrderText(), left, top, argb, outline, matrix, buffers, light);
				case SHADOW -> font.drawInBatch(text, left, top, argb, true, matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
				case NONE -> font.drawInBatch(text, left, top, argb, false, matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
			}
			return;
		}

		// Hover text is drawn on top of the world, so effects are drawn by hand first, then the text over them.
		Font.DisplayMode mode = Font.DisplayMode.SEE_THROUGH;
		switch (p.effect) {
			case SHADOW -> font.drawInBatch(text, left + 1f, top + 1f, ARGB.color(alpha, ARGB.scaleRGB(argb, 0.25f) & 0xFFFFFF),
					false, matrix, buffers, mode, 0, light);
			case OUTLINE -> {
				for (int[] o : OUTLINE_OFFSETS) {
					font.drawInBatch(text, left + o[0], top + o[1], outline, false, matrix, buffers, mode, 0, light);
				}
			}
			case NONE -> {
			}
		}
		font.drawInBatch(text, left, top, argb, false, matrix, buffers, mode, 0, light);
	}

	private static float skyDistance() {
		Minecraft mc = Minecraft.getInstance();
		float renderDistanceBlocks = mc.options.getEffectiveRenderDistance() * 16f;
		// Far enough to feel like part of the sky, close enough to stay out of the distance fog.
		return Mth.clamp(renderDistanceBlocks * 0.55f, 20f, 90f);
	}

	/** Clears what's on screen, e.g. after switching worlds. */
	public void reset() {
		shown.clear();
		hoverInitialized = false;
		hasFrame = false;
	}
}
