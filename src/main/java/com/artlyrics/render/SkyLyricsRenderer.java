package com.artlyrics.render;

import com.artlyrics.Artlyrics;
import com.artlyrics.compat.Mc;
import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.Palette;
import com.artlyrics.lyrics.LyricLine;
import com.artlyrics.lyrics.LyricWord;
import com.artlyrics.lyrics.Lyrics;
import com.artlyrics.nowplaying.NowPlaying;
import com.mojang.blaze3d.vertex.PoseStack;
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
 * set angle above the horizon, and slowly rises while it's up. Positions are relative to the camera (like the
 * sky itself), so the text never gets closer as you walk, but terrain can still cover it. Every word faces the
 * camera on its own, so lines curve around you.
 *
 * <p>Hover: lines hang a few blocks away above the crosshair, trail your view smoothly and are drawn on top
 * of everything.
 */
public final class SkyLyricsRenderer {
	private static final long EXIT_NANOS = 750_000_000L;
	private static final int MAX_SHOWN = 4;
	/**
	 * Words start appearing this long before they're sung, so the fade-in is already visible on the beat instead of
	 * trailing behind the voice.
	 */
	private static final float LEAD_MS = 120f;
	/** Skywriting rises at most this many degrees while a line is shown. */
	private static final float MAX_DRIFT_DEG = 4f;
	private static final float PREVIEW_SCALE = 0.8f;

	/** Angular size of one font pixel, in radians, at size 1.0. */
	private static final float SKY_RADIANS_PER_PX = 0.006f;
	private static final float HOVER_RADIANS_PER_PX = 0.0068f;

	private static final int[][] RING_1 = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
	private static final float[][] RING_2 = {{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-1.4f, -1.4f}, {1.4f, -1.4f}, {-1.4f, 1.4f}, {1.4f, 1.4f}};

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
		long exitStartNanos;
		LineLayout layout;
		ArtlyricsConfig.DisplayMode layoutMode;
		/** The next line, shown small underneath when the preview is on. */
		LineLayout previewLayout;
		int previewIndex = -1;

		ShownLine(Lyrics lyrics, int index, LyricLine line, float anchorYaw, float anchorElev) {
			this.lyrics = lyrics;
			this.index = index;
			this.line = line;
			this.anchorYaw = anchorYaw;
			this.anchorElev = anchorElev;
		}

		boolean leaving() {
			return exitStartNanos != 0;
		}
	}

	/** Everything needed to draw the words of one line this frame. */
	private static final class Frame {
		boolean sky;
		float yaw;
		float elev;
		float distance;
		float radPerPx;
		float bendPx;
		ArtlyricsConfig.TextEffect effect;
		float glow;
		PoseStack poseStack;
		MultiBufferSource buffers;
		Font font;
	}

	/** Draws the lyrics; called from the main world pass with camera-relative positions. */
	public void render(PoseStack poseStack, MultiBufferSource buffers) {
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
		followCamera(camera, dt, config);

		if (!config.enabled) {
			shown.clear();
			return;
		}

		long timeMs = mod.lyricsTimeMs();
		updateShownLines(mod, config, camera, timeMs, now);
		if (shown.isEmpty()) {
			return;
		}

		Frame f = new Frame();
		f.poseStack = poseStack;
		f.buffers = buffers;
		f.font = mc.font;
		double seconds = now / 1e9;
		ShownLine newest = null;
		for (ShownLine line : shown) {
			if (!line.leaving()) {
				newest = line;
			}
		}
		for (ShownLine line : shown) {
			drawLine(line, config, mod.settingsRevision(), f, timeMs, now, seconds, line == newest);
		}
	}

	/** Hover anchor: eases towards where the camera looks, so text trails your view a little. */
	private void followCamera(Camera camera, float dt, ArtlyricsConfig config) {
		float targetYaw = Mc.cameraYaw(camera);
		float targetElev = -Mc.cameraPitch(camera) + config.hoverRaise;
		if (!hoverInitialized) {
			hoverYaw = targetYaw;
			hoverElev = targetElev;
			hoverInitialized = true;
			return;
		}
		float k = 1f - (float) Math.exp(-dt * config.followSpeed);
		hoverYaw += Mth.wrapDegrees(targetYaw - hoverYaw) * k;
		hoverElev += (targetElev - hoverElev) * k;
	}

	private void updateShownLines(Artlyrics mod, ArtlyricsConfig config, Camera camera, long timeMs, long now) {
		Lyrics lyrics = mod.lyrics().lyrics();
		NowPlaying track = mod.tracker().track();
		long lingerMs = Math.round(config.lingerSeconds * 1000);

		int index = -1;
		LyricLine line = null;
		if (lyrics != null && track != null) {
			index = lyrics.lineIndexAt(timeMs);
			if (index >= 0) {
				line = lyrics.lines().get(index);
				if (line.isBlank() || timeMs >= lyrics.visibleEnd(index, lingerMs)) {
					line = null;
				}
			}
		}

		boolean alreadyShown = false;
		for (ShownLine s : shown) {
			if (s.leaving()) {
				continue;
			}
			if (line != null && s.lyrics == lyrics && s.index == index) {
				alreadyShown = true;
			} else {
				s.exitStartNanos = now;
			}
		}

		if (line != null && !alreadyShown) {
			float jitterYaw = 0f;
			float jitterElev = 0f;
			if (config.scatter) {
				// Small, repeatable variation so consecutive lines don't stack exactly on top of each other.
				jitterYaw = ((index * 37) % 17 - 8) * 0.9f;
				jitterElev = ((index * 53) % 9 - 4) * 0.7f;
			}
			shown.add(new ShownLine(lyrics, index, line, Mc.cameraYaw(camera) + jitterYaw, config.skyHeight + jitterElev));
		}

		long exitNanos = (long) (EXIT_NANOS / config.animationSpeed);
		Iterator<ShownLine> it = shown.iterator();
		while (it.hasNext()) {
			ShownLine s = it.next();
			if (s.leaving() && now - s.exitStartNanos > exitNanos) {
				it.remove();
			}
		}
		while (shown.size() > MAX_SHOWN) {
			shown.remove(0);
		}
	}

	/** How far skywriting has risen after {@code ms}: starts at {@code speed} degrees per second and eases to a stop. */
	static float driftDegrees(long ms, float speed) {
		if (speed <= 0f || ms <= 0) {
			return 0f;
		}
		return MAX_DRIFT_DEG * (1f - (float) Math.exp(-(ms / 1000f) * speed / MAX_DRIFT_DEG));
	}

	private void drawLine(ShownLine shownLine, ArtlyricsConfig config, int revision, Frame f, long timeMs, long now, double seconds,
						  boolean newest) {
		boolean sky = config.displayMode == ArtlyricsConfig.DisplayMode.SKYWRITING;
		if (shownLine.layout == null || shownLine.layout.revision != revision || shownLine.layoutMode != config.displayMode) {
			float maxRow = sky ? config.lineWidth : config.lineWidth * 0.9f;
			shownLine.layout = LineLayout.build(shownLine.line, f.font, config, maxRow, revision);
			shownLine.layoutMode = config.displayMode;
		}
		LyricLine line = shownLine.line;
		float speed = config.animationSpeed;

		// Leaving: 1 while shown, easing down to 0 as the line exits.
		float exit = 1f;
		if (shownLine.leaving()) {
			float t = Easing.clamp01((now - shownLine.exitStartNanos) * speed / (float) EXIT_NANOS);
			exit = 1f - Easing.inOutSine(t);
		}
		float gone = 1f - exit;
		float lineScale = config.exitStyle == ArtlyricsConfig.ExitStyle.SHRINK ? 0.6f + 0.4f * exit : 1f;
		// Skywriting rises slowly while it's up, like a trail drifting off, and eases to a stop.
		float drift = sky ? driftDegrees(timeMs - line.startMs(), config.driftSpeed) : 0f;
		// Angular size per font pixel: the same however far away the text is placed.
		f.radPerPx = (sky ? SKY_RADIANS_PER_PX : HOVER_RADIANS_PER_PX) * config.size;
		// A leaving line glides up by its own height, so it never sits under the line that replaces it.
		float heightDeg = (shownLine.layout.height() + 4f) * f.radPerPx * Mth.RAD_TO_DEG;
		float lift;
		if (config.exitStyle == ArtlyricsConfig.ExitStyle.FLOAT) {
			lift = gone * Math.max(heightDeg, sky ? 3.5f : 2f);
		} else {
			lift = sky ? 0f : gone * heightDeg; // hover lines share one spot, so they always make room
		}

		f.sky = sky;
		f.yaw = sky ? shownLine.anchorYaw : hoverYaw;
		f.elev = (sky ? shownLine.anchorElev : hoverElev) + drift + lift;
		f.distance = sky ? skyDistance(config) : config.hoverDistance;
		f.bendPx = config.arc ? (sky ? 16f : 8f) * config.arcAmount : 0f;
		f.effect = config.textEffect;
		f.glow = config.glowStrength;

		List<LyricWord> words = line.words();
		int n = words.size();
		Palette palette = config.palette();
		LineLayout layout = shownLine.layout;
		float baseAlpha = exit * config.opacity;

		if (newest && config.nextLinePreview) {
			drawPreview(shownLine, config, revision, f, timeMs, baseAlpha, palette, seconds);
		}

		if (config.animation == ArtlyricsConfig.Animation.SPOTLIGHT) {
			int wi = line.wordIndexAt(timeMs);
			LineLayout.WordBox box = wi < 0 ? null : findBox(layout, wi);
			if (box == null) {
				return;
			}
			float in = Easing.clamp01((timeMs - words.get(wi).startMs()) * speed / 260f);
			float scale = 2.2f * (0.85f + 0.15f * Easing.outCubic(in)) * lineScale;
			int rgb = Palette.lerpRgb(palette.colorAt(n > 1 ? wi / (float) (n - 1) : 0.5f, seconds), palette.accent(), 0.3f);
			drawWord(f, box, box.text(), 0f, 0f, 1f, scale, 0f, Easing.outCubic(in) * baseAlpha, rgb);
			return;
		}

		boolean whole = config.wholeLine;
		for (LineLayout.WordBox box : layout.words) {
			LyricWord w = words.get(box.index());
			int base = palette.colorAt(n > 1 ? box.index() / (float) (n - 1) : 0.5f, seconds);
			// With "whole line" every word counts as starting with the line.
			long wordStart = whole ? line.startMs() : w.startMs();
			long wordEnd = whole ? Math.max(line.startMs() + 1, line.endMs()) : w.endMs();
			boolean sung = timeMs + LEAD_MS >= wordStart;
			boolean active = !whole && w.isActiveAt(timeMs);
			float since = (timeMs + LEAD_MS - wordStart) * speed;

			switch (config.animation) {
				case FADE -> {
					if (!sung) {
						continue;
					}
					// A long, gentle fade with a tiny settle from below.
					float in = Easing.clamp01(since / 550f);
					int rgb = active ? Palette.lerpRgb(base, palette.accent(), 0.25f * (1f - Easing.clamp01(since / 900f))) : base;
					drawWord(f, box, box.text(), box.xCenter(), box.y(), box.rowHalfWidth(), lineScale,
							(1f - Easing.outCubic(in)) * 2.5f, Easing.inOutSine(in) * baseAlpha, rgb);
				}
				case RISE -> {
					if (!sung) {
						continue;
					}
					drawRisingLetters(f, box, since, w, lineScale, baseAlpha, base, palette, active);
				}
				case BLOOM -> {
					if (!sung) {
						continue;
					}
					float in = Easing.clamp01(since / 380f);
					int rgb = Palette.lerpRgb(palette.accent(), base, Easing.clamp01(since / 600f));
					drawWord(f, box, box.text(), box.xCenter(), box.y(), box.rowHalfWidth(),
							(0.6f + 0.4f * Easing.outBack(in)) * lineScale, 0f, Easing.outCubic(in) * baseAlpha, rgb);
				}
				case TYPEWRITER -> {
					if (!sung) {
						continue;
					}
					int letters = box.letters().length;
					float perLetter = Math.max(25f, Math.min(90f, (wordEnd - wordStart) / (float) Math.max(1, letters)));
					int visible = (int) Math.min(letters, 1 + since / perLetter);
					Component text = box.text();
					if (visible < letters) {
						int cut = box.raw().offsetByCodePoints(0, visible);
						text = Component.literal(box.raw().substring(0, cut)).withStyle(layout.style);
					}
					int rgb = active ? Palette.lerpRgb(base, palette.accent(), 0.4f) : base;
					drawWord(f, box, text, box.xCenter(), box.y(), box.rowHalfWidth(), lineScale, 0f, baseAlpha, rgb);
				}
				default -> { // SWEEP: a karaoke fill that runs smoothly through each word as it's sung
					float lineIn = Easing.inOutSine(Easing.clamp01((timeMs + LEAD_MS - line.startMs()) * speed / 400f));
					float fill = Easing.clamp01((timeMs - wordStart) / (float) Math.max(1, wordEnd - wordStart));
					int unsungRgb = Palette.lerpRgb(base, 0x7C7F8C, 0.5f);
					// Just-sung words keep a little of the highlight and let it fade out.
					float afterglow = 1f - Easing.clamp01((timeMs - wordEnd) / 450f);
					int sungRgb = Palette.lerpRgb(base, palette.accent(), 0.5f * afterglow);
					if (fill <= 0f) {
						drawWord(f, box, box.text(), box.xCenter(), box.y(), box.rowHalfWidth(), lineScale, 0f, lineIn * 0.35f * baseAlpha, unsungRgb);
					} else if (fill >= 1f) {
						drawWord(f, box, box.text(), box.xCenter(), box.y(), box.rowHalfWidth(), lineScale, 0f, lineIn * baseAlpha, sungRgb);
					} else {
						drawSweepingWord(f, box, fill, lineScale, lineIn * baseAlpha, unsungRgb, sungRgb);
					}
				}
			}
		}
	}

	/** Rise: letters float up into place one after another. */
	private void drawRisingLetters(Frame f, LineLayout.WordBox box, float since, LyricWord w, float lineScale, float baseAlpha,
								   int base, Palette palette, boolean active) {
		Component[] letters = box.letters();
		float stagger = Math.max(18f, Math.min(55f, (w.endMs() - w.startMs()) / (float) Math.max(1, letters.length)));
		float letterTime = 380f;
		int rgb = active ? Palette.lerpRgb(base, palette.accent(), 0.3f) : base;
		if (since >= stagger * (letters.length - 1) + letterTime) {
			drawWord(f, box, box.text(), box.xCenter(), box.y(), box.rowHalfWidth(), lineScale, 0f, baseAlpha, rgb);
			return;
		}
		for (int i = 0; i < letters.length; i++) {
			float in = Easing.clamp01((since - i * stagger) / letterTime);
			if (in <= 0f) {
				break;
			}
			drawGlyphs(f, box, letters[i], box.letterX()[i], box.xCenter(), box.y(), box.rowHalfWidth(), lineScale,
					(1f - Easing.outCubic(in)) * 6f, Easing.inOutSine(in) * baseAlpha, rgb);
		}
	}

	/**
	 * A word partway through being sung: letters behind the fill point are lit, the one under it is part lit, and
	 * the rest wait in the dim color, so the highlight glides instead of jumping from word to word.
	 */
	private void drawSweepingWord(Frame f, LineLayout.WordBox box, float fill, float lineScale, float alpha, int unsungRgb, int sungRgb) {
		Component[] letters = box.letters();
		float[] xs = box.letterX();
		float edge = fill * box.width();
		for (int i = 0; i < letters.length; i++) {
			float x0 = xs[i];
			float x1 = i + 1 < xs.length ? xs[i + 1] : box.width();
			float lit = Easing.clamp01((edge - x0) / Math.max(1f, x1 - x0));
			float a = alpha * (0.35f + 0.65f * lit);
			int rgb = Palette.lerpRgb(unsungRgb, sungRgb, lit);
			float rise = -1.2f * lit * (1f - Easing.clamp01((edge - x1) / 24f));
			drawGlyphs(f, box, letters[i], x0, box.xCenter(), box.y(), box.rowHalfWidth(), lineScale, rise, a, rgb);
		}
	}

	/** The next line, small and faint under the current one. */
	private void drawPreview(ShownLine current, ArtlyricsConfig config, int revision, Frame f, long timeMs, float baseAlpha,
							 Palette palette, double seconds) {
		Lyrics lyrics = current.lyrics;
		int next = -1;
		for (int i = current.index + 1; i < lyrics.lines().size(); i++) {
			if (!lyrics.lines().get(i).isBlank()) {
				next = i;
				break;
			}
		}
		if (next < 0) {
			return;
		}
		if (current.previewLayout == null || current.previewIndex != next || current.previewLayout.revision != revision) {
			float maxRow = (f.sky ? config.lineWidth : config.lineWidth * 0.9f) / PREVIEW_SCALE;
			current.previewLayout = LineLayout.build(lyrics.lines().get(next), f.font, config, maxRow, revision);
			current.previewIndex = next;
		}
		LineLayout preview = current.previewLayout;
		float fadeIn = Easing.inOutSine(Easing.clamp01((timeMs + LEAD_MS - current.line.startMs()) / 500f));
		float alpha = baseAlpha * config.previewOpacity * fadeIn;
		if (alpha <= 0.01f) {
			return;
		}
		float offset = current.layout.height() / 2f + 5f + preview.height() * PREVIEW_SCALE / 2f;
		int rgb = Palette.lerpRgb(palette.colorAt(0.5f, seconds), 0xB8BCC8, 0.45f);
		for (LineLayout.WordBox box : preview.words) {
			drawWord(f, box, box.text(), box.xCenter() * PREVIEW_SCALE, offset + box.y() * PREVIEW_SCALE,
					box.rowHalfWidth() * PREVIEW_SCALE, PREVIEW_SCALE, 0f, alpha, rgb);
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

	private void drawWord(Frame f, LineLayout.WordBox box, Component text, float xCenterPx, float yPx, float rowHalfWidth,
						  float scale, float risePx, float alpha, int rgb) {
		drawGlyphs(f, box, text, 0f, xCenterPx, yPx, rowHalfWidth, scale, risePx, alpha, rgb);
	}

	/**
	 * Draws {@code text} at a word's place, {@code dx} font pixels from the word's left edge. The word is placed by
	 * its full width, so partial words and single letters line up with the finished word.
	 */
	private void drawGlyphs(Frame f, LineLayout.WordBox box, Component text, float dx, float xCenterPx, float yPx,
							float rowHalfWidth, float scale, float risePx, float alpha, int rgb) {
		if (alpha <= 0.01f) {
			return;
		}
		// The whole line lies on one flat plane facing the camera at the line's anchor, like a banner, so rows read
		// straight. Arc: words in the middle of a row sit a little higher than the ends and tilt with the curve.
		float u = Mth.clamp(xCenterPx / rowHalfWidth, -1f, 1f);
		float arcPx = f.bendPx * (1f - u * u);
		float slope = rowHalfWidth > 1f ? -2f * u * f.bendPx / rowHalfWidth : 0f;
		Matrix4f matrix = SkyGeometry.wordMatrix(f.poseStack.last().pose(), f.yaw, f.elev, f.distance, 0f, f.distance * f.radPerPx)
				.translate(xCenterPx, yPx - arcPx, 0f);
		if (slope != 0f) {
			// Font Y points down, so a counter-clockwise tilt on screen is a negative rotation here.
			matrix.rotateZ(-(float) Math.atan(slope) * 0.8f);
		}
		matrix.scale(scale, scale, 1f);

		float x = -box.width() / 2f + dx;
		float y = -4.5f + risePx / scale;
		int argb = ARGB.color(alpha, rgb);
		int light = LightTexture.FULL_BRIGHT;
		Font.DisplayMode mode = f.sky ? Font.DisplayMode.POLYGON_OFFSET : Font.DisplayMode.SEE_THROUGH;
		Font font = f.font;
		// Glow, shadow and outline copies sit a hair behind the letters, so however Minecraft sorts translucent
		// text, the letters always end up on top.
		Matrix4f behind = new Matrix4f(matrix).translate(0f, 0f, -0.6f);

		switch (f.effect) {
			case GLOW -> {
				// A soft halo: faint copies in a lighter tint around the letters.
				if (f.glow > 0.01f) {
					int halo = Palette.lerpRgb(rgb, 0xFFFFFF, 0.3f);
					int inner = ARGB.color(alpha * f.glow * 0.16f, halo);
					int outer = ARGB.color(alpha * f.glow * 0.09f, halo);
					for (float[] o : RING_2) {
						font.drawInBatch(text, x + o[0], y + o[1], outer, false, behind, f.buffers, mode, 0, light);
					}
					for (int[] o : RING_1) {
						font.drawInBatch(text, x + o[0], y + o[1], inner, false, behind, f.buffers, mode, 0, light);
					}
				}
				font.drawInBatch(text, x, y, argb, false, matrix, f.buffers, mode, 0, light);
			}
			case OUTLINE -> {
				int outline = ARGB.color(alpha, Palette.lerpRgb(rgb, 0x000000, 0.78f));
				if (f.sky) {
					font.drawInBatch8xOutline(text.getVisualOrderText(), x, y, argb, outline, matrix, f.buffers, light);
				} else {
					for (int[] o : RING_1) {
						font.drawInBatch(text, x + o[0] * 0.75f, y + o[1] * 0.75f, outline, false, behind, f.buffers, mode, 0, light);
					}
					font.drawInBatch(text, x, y, argb, false, matrix, f.buffers, mode, 0, light);
				}
			}
			case SHADOW -> {
				if (f.sky) {
					font.drawInBatch(text, x, y, argb, true, matrix, f.buffers, mode, 0, light);
				} else {
					font.drawInBatch(text, x + 1f, y + 1f, ARGB.color(alpha * 0.8f, ARGB.scaleRGB(argb, 0.25f) & 0xFFFFFF), false, behind, f.buffers, mode, 0, light);
					font.drawInBatch(text, x, y, argb, false, matrix, f.buffers, mode, 0, light);
				}
			}
			case NONE -> font.drawInBatch(text, x, y, argb, false, matrix, f.buffers, mode, 0, light);
		}
	}

	private static float skyDistance(ArtlyricsConfig config) {
		if (config.skyDistance > 0) {
			return config.skyDistance;
		}
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
