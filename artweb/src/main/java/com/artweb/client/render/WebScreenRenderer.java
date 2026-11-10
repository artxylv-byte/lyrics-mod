package com.artweb.client.render;

import com.artweb.block.WebScreenBlock;
import com.artweb.block.WebScreenBlockEntity;
import com.artweb.client.ImageCache;
import com.artweb.client.PageViews;
import com.artweb.client.ScreenPicker;
import com.artweb.client.page.PageCache;
import com.artweb.client.page.PageLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the web page on a screen block. The picture starts at the block and extends to the right and upwards,
 * {@code width × height} blocks, 64 page pixels per block. Only lines that fit completely are drawn, so text is
 * never cut in half at the edges.
 */
public final class WebScreenRenderer implements BlockEntityRenderer<WebScreenBlockEntity, WebScreenRenderer.State> {
	public static final int BEZEL = 0xFF14131C;
	public static final int BACKGROUND = 0xFF0E0D15;
	private static final int PLACEHOLDER = 0xFF1F1E2C;
	private static final int SCROLLBAR = 0x66FFFFFF;
	private static final int LIGHT = LightTexture.FULL_BRIGHT;

	public static final class State extends BlockEntityRenderState {
		public String url = WebScreenBlockEntity.DEFAULT_URL;
		public int width = 3;
		public int height = 2;
		public int scroll;
		public Direction facing = Direction.NORTH;
	}

	private final Font font;

	public WebScreenRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WebScreenBlockEntity screen, State state, float partialTick, Vec3 camera,
			ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderer.super.extractRenderState(screen, state, partialTick, camera, crumbling);
		state.url = screen.url();
		state.width = screen.width();
		state.height = screen.height();
		state.scroll = screen.scroll();
		BlockState block = screen.getBlockState();
		state.facing = block.hasProperty(WebScreenBlock.FACING) ? block.getValue(WebScreenBlock.FACING) : Direction.NORTH;
		ScreenPicker.seen(screen.getBlockPos(), state.facing, state.width, state.height);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		int pw = state.width * PageViews.PX_PER_BLOCK;
		int ph = state.height * PageViews.PX_PER_BLOCK;
		PageViews.View view = PageViews.get(state.url, pw, font);

		pose.pushPose();
		pose.translate(0.5f, 0.5f, 0.5f);
		pose.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
		pose.translate(-0.5f, -0.5f + state.height, ScreenPicker.FACE_OFFSET);
		pose.scale(1f / PageViews.PX_PER_BLOCK, -1f / PageViews.PX_PER_BLOCK, 1f / PageViews.PX_PER_BLOCK);

		float scroll = 0;
		List<float[]> bars = new ArrayList<>();
		List<int[]> barColors = new ArrayList<>();
		if (view.state() == PageCache.State.READY) {
			PageLayout.Result layout = view.layout();
			scroll = Math.max(0, Math.min(state.scroll, layout.height() - ph));
			float top = scroll;
			float bottom = scroll + ph;
			for (PageLayout.Bar b : layout.bars()) {
				addClipped(bars, barColors, b.x(), b.y() - top, b.w(), b.h(), b.color(), pw, ph);
			}
			drawImages(layout, top, bottom, pw, ph, pose, collector, bars, barColors);
			if (layout.height() > ph) {
				float thumb = Math.max(8, ph * ph / layout.height());
				float y = (ph - thumb) * scroll / (layout.height() - ph);
				addClipped(bars, barColors, pw - 2.5f, y, 1.5f, thumb, SCROLLBAR, pw, ph);
			}
		}

		// Frame, page background and the back of the panel.
		collector.submitCustomGeometry(pose, RenderType.textBackground(), (p, vc) -> {
			float b = 2;
			quad(vc, p, -b, -b, pw + b, 0, 0, BEZEL);
			quad(vc, p, -b, ph, pw + b, ph + b, 0, BEZEL);
			quad(vc, p, -b, 0, 0, ph, 0, BEZEL);
			quad(vc, p, pw, 0, pw + b, ph, 0, BEZEL);
			quad(vc, p, 0, 0, pw, ph, 0, BACKGROUND);
			backQuad(vc, p, -b, -b, pw + b, ph + b, -0.6f, BEZEL);
			for (int i = 0; i < bars.size(); i++) {
				float[] r = bars.get(i);
				quad(vc, p, r[0], r[1], r[0] + r[2], r[1] + r[3], 0.25f, barColors.get(i)[0]);
			}
		});

		pose.translate(0, 0, 0.5f);
		if (view.state() == PageCache.State.READY) {
			drawText(view.layout(), scroll, ph, pose, collector);
		} else {
			drawMessage(view, pw, ph, pose, collector);
		}
		pose.popPose();
	}

	private void drawText(PageLayout.Result layout, float top, int ph, PoseStack pose, SubmitNodeCollector collector) {
		float bottom = top + ph;
		for (PageLayout.Run r : layout.runs()) {
			if (r.y() + r.height() > bottom + 0.01f) {
				break; // runs are in page order, so nothing further down fits either
			}
			if (r.y() < top - 0.01f) {
				continue;
			}
			FormattedCharSequence text = PageViews.text(r);
			float y = r.y() - top + 0.5f * r.scale();
			if (r.scale() == 1f) {
				collector.submitText(pose, r.x(), y, text, false, Font.DisplayMode.POLYGON_OFFSET, LIGHT, r.color(), 0, 0);
			} else {
				pose.pushPose();
				pose.translate(r.x(), y, 0);
				pose.scale(r.scale(), r.scale(), 1);
				collector.submitText(pose, 0, 0, text, false, Font.DisplayMode.POLYGON_OFFSET, LIGHT, r.color(), 0, 0);
				pose.popPose();
			}
		}
	}

	private void drawImages(PageLayout.Result layout, float top, float bottom, int pw, int ph, PoseStack pose,
			SubmitNodeCollector collector, List<float[]> bars, List<int[]> barColors) {
		for (PageLayout.ImageBox box : layout.images()) {
			if (box.y() + box.h() <= top - ph || box.y() >= bottom + ph) {
				continue; // well outside the screen: don't even download it
			}
			ImageCache.Img img = ImageCache.get(box.src());
			if (box.y() + box.h() <= top || box.y() >= bottom) {
				continue; // just outside: downloaded ahead of time, not drawn
			}
			float y0 = Math.max(box.y(), top);
			float y1 = Math.min(box.y() + box.h(), bottom);
			float x1 = Math.min(box.x() + box.w(), pw);
			if (img.state() != ImageCache.State.READY) {
				addClipped(bars, barColors, box.x(), box.y() - top, box.w(), box.h(), PLACEHOLDER, pw, ph);
				continue;
			}
			ResourceLocation texture = img.texture();
			float v0 = (y0 - box.y()) / box.h();
			float v1 = (y1 - box.y()) / box.h();
			float u1 = (x1 - box.x()) / box.w();
			float sx = box.x();
			float sy0 = y0 - top;
			float sy1 = y1 - top;
			collector.submitCustomGeometry(pose, RenderType.text(texture), (p, vc) -> {
				vc.addVertex(p, sx, sy0, 0.25f).setColor(-1).setUv(0, v0).setLight(LIGHT);
				vc.addVertex(p, sx, sy1, 0.25f).setColor(-1).setUv(0, v1).setLight(LIGHT);
				vc.addVertex(p, x1, sy1, 0.25f).setColor(-1).setUv(u1, v1).setLight(LIGHT);
				vc.addVertex(p, x1, sy0, 0.25f).setColor(-1).setUv(u1, v0).setLight(LIGHT);
			});
		}
	}

	private void drawMessage(PageViews.View view, int pw, int ph, PoseStack pose, SubmitNodeCollector collector) {
		String message = view.message() == null ? "" : view.message();
		int color = view.state() == PageCache.State.ERROR ? 0xFFFF9C9C : 0xFFB8B5C8;
		List<FormattedCharSequence> lines = font.split(Component.literal(message), Math.max(20, pw - 12));
		if (lines.size() > Math.max(1, (ph - 8) / 10)) {
			lines = lines.subList(0, Math.max(1, (ph - 8) / 10));
		}
		float y = (ph - lines.size() * 10f) / 2f;
		for (FormattedCharSequence line : lines) {
			float x = (pw - font.width(line)) / 2f;
			collector.submitText(pose, x, y, line, false, Font.DisplayMode.POLYGON_OFFSET, LIGHT, color, 0, 0);
			y += 10;
		}
	}

	/** Adds a rectangle given in screen coordinates, cut to the screen's edges. */
	private static void addClipped(List<float[]> bars, List<int[]> colors, float x, float y, float w, float h, int color, int pw, int ph) {
		float x0 = Math.max(0, x);
		float y0 = Math.max(0, y);
		float x1 = Math.min(pw, x + w);
		float y1 = Math.min(ph, y + h);
		if (x1 > x0 && y1 > y0) {
			bars.add(new float[] {x0, y0, x1 - x0, y1 - y0});
			colors.add(new int[] {color});
		}
	}

	/** A rectangle facing the viewer (same corner order as the font's letters). */
	private static void quad(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z, int color) {
		vc.addVertex(p, x0, y0, z).setColor(color).setLight(LIGHT);
		vc.addVertex(p, x0, y1, z).setColor(color).setLight(LIGHT);
		vc.addVertex(p, x1, y1, z).setColor(color).setLight(LIGHT);
		vc.addVertex(p, x1, y0, z).setColor(color).setLight(LIGHT);
	}

	/** A rectangle facing away, so the panel looks solid from behind. */
	private static void backQuad(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z, int color) {
		vc.addVertex(p, x1, y0, z).setColor(color).setLight(LIGHT);
		vc.addVertex(p, x1, y1, z).setColor(color).setLight(LIGHT);
		vc.addVertex(p, x0, y1, z).setColor(color).setLight(LIGHT);
		vc.addVertex(p, x0, y0, z).setColor(color).setLight(LIGHT);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true; // the page is bigger than the block
	}

	@Override
	public int getViewDistance() {
		return 96;
	}
}
