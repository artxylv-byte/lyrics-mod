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
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws the lyrics as 3D text in the world.
 *
 * <p>Sky mode: each line is placed out in the sky in the direction you were looking when it started, at a
 * fixed angle above the horizon. Positions are relative to the camera (like the sky itself), so the text
 * never gets closer as you walk, but terrain in front of it still hides it. Every word faces the camera on
 * its own, so lines curve around you.
 *
 * <p>Front mode: lines float a few blocks in front of you, follow your view smoothly and are drawn on top
 * of everything.
 */
public final class SkyLyricsRenderer {
	private static final long FADE_NANOS = 650_000_000L;
	private static final int MAX_SHOWN = 4;

	/** Angular size of one font pixel, in radians, at size 1.0. */
	private static final float SKY_RADIANS_PER_PX = 0.0042f;
	private static final float FRONT_RADIANS_PER_PX = 0.005f;
	private static final float SKY_MAX_ROW_PX = 200f;
	private static final float FRONT_MAX_ROW_PX = 180f;
	private static final float FRONT_DISTANCE = 4.0f;
	/** Front-mode lines sit this many degrees above the crosshair. */
	private static final float FRONT_RAISE_DEG = 9f;

	private final List<ShownLine> shown = new ArrayList<>();
	private float frontYaw;
	private float frontElev;
	private boolean frontInitialized;
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

	/** Front-mode anchor: eases towards where the camera looks, so text trails your view a little. */
	private void followCamera(Camera camera, float dt) {
		float targetYaw = camera.yRot();
		float targetElev = -camera.xRot() + FRONT_RAISE_DEG;
		if (!frontInitialized) {
			frontYaw = targetYaw;
			frontElev = targetElev;
			frontInitialized = true;
			return;
		}
		float k = 1f - (float) Math.exp(-dt * 7.0);
		frontYaw += Mth.wrapDegrees(targetYaw - frontYaw) * k;
		frontElev += (targetElev - frontElev) * k;
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
		boolean sky = config.displayMode == ArtlyricsConfig.DisplayMode.SKY;
		if (shownLine.layout == null || shownLine.layout.revision != revision || shownLine.layoutMode != config.displayMode) {
			shownLine.layout = LineLayout.build(shownLine.line, font, config, sky ? SKY_MAX_ROW_PX : FRONT_MAX_ROW_PX, revision);
			shownLine.layoutMode = config.displayMode;
		}

		float fade = shownLine.fading() ? 1f - Easing.clamp01((now - shownLine.fadeStartNanos) / (float) FADE_NANOS) : 1f;
		float drift = 1f - fade;

		Placement p = new Placement();
		p.sky = sky;
		p.yaw = sky ? shownLine.anchorYaw : frontYaw;
		p.elev = (sky ? shownLine.anchorElev : frontElev) + drift * (sky ? 3f : 2f);
		p.distance = sky ? skyDistance() : FRONT_DISTANCE;
		p.radPerPx = (sky ? SKY_RADIANS_PER_PX : FRONT_RADIANS_PER_PX) * config.size;
		p.bendPx = config.curved ? (sky ? 10f : 5f) : 0f;
		p.shadow = config.shadow;

		LyricLine line = shownLine.line;
		List<LyricWord> words = line.words();
		int n = words.size();
		LyricStyle look = config.style;

		if (config.animation == ArtlyricsConfig.Animation.ONE_WORD) {
			int wi = line.wordIndexAt(timeMs);
			if (wi < 0) {
				return;
			}
			LyricWord w = words.get(wi);
			LineLayout.WordBox box = findBox(shownLine.layout, wi);
			if (box == null) {
				return;
			}
			float pop = Easing.clamp01((timeMs - w.startMs()) / 220f);
			float scale = 2.0f * (0.75f + 0.25f * Easing.outBack(pop));
			float alpha = Easing.outCubic(pop) * fade;
			int rgb = LyricStyle.lerpRgb(look.colorAt(n > 1 ? wi / (float) (n - 1) : 0.5f, seconds), look.highlight, 0.35f);
			drawWord(poseStack, buffers, font, box, 0f, 0f, 1f, scale, 0f, ARGB.color(alpha, rgb), p);
			return;
		}

		for (LineLayout.WordBox box : shownLine.layout.words) {
			LyricWord w = words.get(box.index());
			float gradient = n > 1 ? box.index() / (float) (n - 1) : 0.5f;
			int rgb = look.colorAt(gradient, seconds);
			boolean active = w.isActiveAt(timeMs);
			boolean sung = timeMs >= w.startMs();

			float alpha;
			float scale = 1f;
			float rise = 0f;
			if (config.animation == ArtlyricsConfig.Animation.WORD_BY_WORD) {
				if (!sung) {
					continue;
				}
				float in = Easing.clamp01((timeMs - w.startMs()) / 260f);
				alpha = Easing.outCubic(in);
				rise = (1f - Easing.outCubic(in)) * 7f;
				scale = 0.8f + 0.2f * Easing.outBack(in);
			} else { // KARAOKE
				float lineIn = Easing.clamp01((timeMs - line.startMs()) / 300f);
				alpha = lineIn * (sung ? 1f : 0.4f);
				if (!sung) {
					rgb = LyricStyle.lerpRgb(rgb, 0x8A8A9A, 0.45f);
				}
			}
			if (active) {
				float pulse = Easing.clamp01((timeMs - w.startMs()) / 180f);
				rgb = LyricStyle.lerpRgb(rgb, look.highlight, 0.55f);
				scale *= 1f + 0.08f * (1f - pulse) + 0.04f;
			}
			alpha *= fade;
			if (alpha <= 0.02f) {
				continue;
			}
			drawWord(poseStack, buffers, font, box, box.xCenter(), box.y(), box.rowHalfWidth(), scale, rise, ARGB.color(alpha, rgb), p);
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
		boolean shadow;
	}

	private static void drawWord(PoseStack poseStack, MultiBufferSource buffers, Font font, LineLayout.WordBox box,
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
		if (p.sky) {
			font.drawInBatch(box.text(), left, top, argb, p.shadow, matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
		} else {
			// Drawn on top of the world; a manual shadow keeps the draw order (shadow first) under control.
			if (p.shadow) {
				int shadow = ARGB.color(ARGB.alpha(argb), ARGB.scaleRGB(argb, 0.25f) & 0xFFFFFF);
				font.drawInBatch(box.text(), left + 1f, top + 1f, shadow, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
			}
			font.drawInBatch(box.text(), left, top, argb, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
		}
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
		frontInitialized = false;
		hasFrame = false;
	}
}
