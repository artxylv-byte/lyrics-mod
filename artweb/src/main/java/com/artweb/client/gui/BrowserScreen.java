package com.artweb.client.gui;

import com.artweb.ArtWeb;
import com.artweb.block.WebScreenBlockEntity;
import com.artweb.client.ImageCache;
import com.artweb.client.PageViews;
import com.artweb.client.page.PageCache;
import com.artweb.client.page.PageLayout;
import com.artweb.client.page.Urls;
import com.artweb.client.render.WebScreenRenderer;
import com.artweb.net.UpdateScreenPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The browser window for one screen block: an address bar, back/forward/reload/home, the page exactly as the
 * block shows it (scroll with the mouse wheel, click links), and the screen's size. Every change is sent to the
 * server so everyone sees the same page on the block.
 */
public final class BrowserScreen extends Screen {
	private static final int BAR = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 6;
	private static final long SEND_DELAY_MS = 250;
	private static final ResourceLocation RELOAD_ICON = ArtWeb.id("textures/gui/reload.png");

	/** Back/forward lists per screen block, kept while the game runs. */
	private static final Map<BlockPos, History> HISTORY = new HashMap<>();

	private static final class History {
		final Deque<String> back = new ArrayDeque<>();
		final Deque<String> forward = new ArrayDeque<>();
	}

	private final BlockPos pos;
	private final History history;
	private String url;
	private int blocksW;
	private int blocksH;
	private float scroll;
	private boolean dirty;
	private long changedAt;

	private EditBox address;
	private Button backButton;
	private Button forwardButton;
	private Button reloadButton;
	private Button sizeLabel;

	// Where the page is drawn, worked out each frame.
	private float pageX;
	private float pageY;
	private float pageScale = 1;

	public BrowserScreen(BlockPos pos, WebScreenBlockEntity screen) {
		super(Component.translatable("artweb.browser.title"));
		this.pos = pos.immutable();
		this.history = HISTORY.computeIfAbsent(this.pos, p -> new History());
		this.url = screen.url();
		this.blocksW = screen.width();
		this.blocksH = screen.height();
		this.scroll = screen.scroll();
	}

	private int pageW() {
		return blocksW * PageViews.PX_PER_BLOCK;
	}

	private int pageH() {
		return blocksH * PageViews.PX_PER_BLOCK;
	}

	@Override
	protected void init() {
		int x = MARGIN;
		backButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> back())
				.bounds(x, MARGIN, BAR, BAR).tooltip(Tooltip.create(Component.translatable("artweb.browser.back"))).build());
		x += BAR + GAP;
		forwardButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> forward())
				.bounds(x, MARGIN, BAR, BAR).tooltip(Tooltip.create(Component.translatable("artweb.browser.forward"))).build());
		x += BAR + GAP;
		reloadButton = addRenderableWidget(Button.builder(Component.empty(), b -> reload())
				.bounds(x, MARGIN, BAR, BAR).tooltip(Tooltip.create(Component.translatable("artweb.browser.reload"))).build());
		x += BAR + GAP;
		addRenderableWidget(Button.builder(Component.literal("⌂"), b -> navigate(WebScreenBlockEntity.DEFAULT_URL, true))
				.bounds(x, MARGIN, BAR, BAR).tooltip(Tooltip.create(Component.translatable("artweb.browser.home"))).build());
		x += BAR + GAP + 2;

		int goW = 34;
		address = new EditBox(font, x, MARGIN, width - MARGIN - goW - GAP - x, BAR, Component.translatable("artweb.browser.address"));
		address.setMaxLength(WebScreenBlockEntity.MAX_URL_LENGTH);
		address.setHint(Component.translatable("artweb.browser.hint"));
		address.setValue(url);
		address.moveCursorToStart(false);
		addRenderableWidget(address);
		addRenderableWidget(Button.builder(Component.translatable("artweb.browser.go"), b -> go())
				.bounds(width - MARGIN - goW, MARGIN, goW, BAR).build());

		int y = height - MARGIN - BAR;
		int bx = MARGIN;
		addRenderableWidget(Button.builder(Component.literal("-"), b -> resize(-1, 0))
				.bounds(bx, y, BAR, BAR).tooltip(Tooltip.create(Component.translatable("artweb.browser.narrower"))).build());
		bx += BAR + GAP;
		addRenderableWidget(Button.builder(Component.literal("+"), b -> resize(1, 0))
				.bounds(bx, y, BAR, BAR).tooltip(Tooltip.create(Component.translatable("artweb.browser.wider"))).build());
		bx += BAR + GAP;
		sizeLabel = addRenderableWidget(Button.builder(sizeText(), b -> { })
				.bounds(bx, y, 92, BAR).build());
		sizeLabel.active = false;
		bx += 92 + GAP;
		addRenderableWidget(Button.builder(Component.literal("-"), b -> resize(0, -1))
				.bounds(bx, y, BAR, BAR).tooltip(Tooltip.create(Component.translatable("artweb.browser.shorter"))).build());
		bx += BAR + GAP;
		addRenderableWidget(Button.builder(Component.literal("+"), b -> resize(0, 1))
				.bounds(bx, y, BAR, BAR).tooltip(Tooltip.create(Component.translatable("artweb.browser.taller"))).build());

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(width - MARGIN - 70, y, 70, BAR).build());
		updateButtons();
	}

	private Component sizeText() {
		return Component.translatable("artweb.browser.size", blocksW, blocksH);
	}

	private void updateButtons() {
		if (backButton != null) {
			backButton.active = !history.back.isEmpty();
			forwardButton.active = !history.forward.isEmpty();
			sizeLabel.setMessage(sizeText());
		}
	}

	// ---- navigation ----

	private void go() {
		String target = Urls.fromInput(address.getValue());
		if (target != null) {
			navigate(target, true);
		}
		address.setFocused(false);
		setFocused(null);
	}

	private void navigate(String target, boolean remember) {
		if (target.length() > WebScreenBlockEntity.MAX_URL_LENGTH) {
			return;
		}
		if (remember && !target.equals(url)) {
			history.back.push(url);
			history.forward.clear();
		}
		url = target;
		scroll = 0;
		address.setValue(url);
		address.moveCursorToStart(false);
		PageCache.get(url);
		sendNow();
		updateButtons();
	}

	private void back() {
		if (!history.back.isEmpty()) {
			history.forward.push(url);
			navigate(history.back.pop(), false);
		}
	}

	private void forward() {
		if (!history.forward.isEmpty()) {
			history.back.push(url);
			navigate(history.forward.pop(), false);
		}
	}

	private void reload() {
		PageCache.reload(url);
	}

	private void resize(int dw, int dh) {
		blocksW = Math.max(1, Math.min(WebScreenBlockEntity.MAX_WIDTH, blocksW + dw));
		blocksH = Math.max(1, Math.min(WebScreenBlockEntity.MAX_HEIGHT, blocksH + dh));
		clampScroll();
		sendNow();
		updateButtons();
	}

	// ---- sending changes ----

	private void sendNow() {
		dirty = false;
		ClientPlayNetworking.send(new UpdateScreenPayload(pos, url, blocksW, blocksH, Math.round(scroll)));
	}

	private void markScrolled() {
		dirty = true;
		changedAt = System.currentTimeMillis();
	}

	@Override
	public void tick() {
		if (dirty && System.currentTimeMillis() - changedAt > SEND_DELAY_MS) {
			sendNow();
		}
		if (minecraft != null && minecraft.level != null && !(minecraft.level.getBlockEntity(pos) instanceof WebScreenBlockEntity)) {
			onClose(); // the screen block was broken
		}
	}

	@Override
	public void removed() {
		if (dirty) {
			sendNow();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- scrolling and links ----

	private PageViews.View view() {
		return PageViews.get(url, pageW(), font);
	}

	private float maxScroll() {
		PageViews.View v = view();
		return v.layout() == null ? 0 : Math.max(0, v.layout().height() - pageH());
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	private void scrollBy(float amount) {
		float before = scroll;
		scroll += amount;
		clampScroll();
		if (scroll != before) {
			markScrolled();
		}
	}

	private boolean overPage(double mx, double my) {
		return mx >= pageX && my >= pageY && mx < pageX + pageW() * pageScale && my < pageY + pageH() * pageScale;
	}

	private String linkAt(double mx, double my) {
		PageViews.View v = view();
		if (v.layout() == null || !overPage(mx, my)) {
			return null;
		}
		float px = (float) ((mx - pageX) / pageScale);
		float py = (float) ((my - pageY) / pageScale) + scroll;
		return v.layout().linkAt(px, py);
	}

	/** Where a point of the page (in page pixels, from the top of the whole page) is on the window right now. */
	public double[] pageToScreen(float px, float py) {
		return new double[] {pageX + px * pageScale, pageY + (py - scroll) * pageScale};
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (overPage(mx, my)) {
			scrollBy((float) (-scrollY * PageLayout.LINE * 3));
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		String link = linkAt(event.x(), event.y());
		if (link != null && event.button() == 0) {
			String target = Urls.unwrapRedirect(link);
			if (Urls.isWeb(target) && !samePage(target, url)) {
				navigate(target, true);
			}
			return true;
		}
		if (overPage(event.x(), event.y())) {
			setFocused(null);
			address.setFocused(false);
			return true;
		}
		return false;
	}

	private static boolean samePage(String a, String b) {
		return stripFragment(a).equals(stripFragment(b));
	}

	private static String stripFragment(String u) {
		int hash = u.indexOf('#');
		return hash >= 0 ? u.substring(0, hash) : u;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (address.isFocused() && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
			go();
			return true;
		}
		if (!address.isFocused()) {
			float page = pageH() - 2 * PageLayout.LINE;
			switch (key) {
				case InputConstants.KEY_DOWN -> scrollBy(PageLayout.LINE * 2);
				case InputConstants.KEY_UP -> scrollBy(-PageLayout.LINE * 2);
				case InputConstants.KEY_PAGEDOWN, InputConstants.KEY_SPACE -> scrollBy(page);
				case InputConstants.KEY_PAGEUP -> scrollBy(-page);
				case InputConstants.KEY_HOME -> scrollBy(-scroll);
				case InputConstants.KEY_END -> scrollBy(maxScroll());
				default -> {
					return super.keyPressed(event);
				}
			}
			return true;
		}
		return super.keyPressed(event);
	}

	// ---- drawing ----

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		int pw = pageW();
		int ph = pageH();
		int areaTop = MARGIN + BAR + 6;
		int areaBottom = height - MARGIN - BAR - 18;
		float areaW = width - 2 * MARGIN;
		float areaH = Math.max(20, areaBottom - areaTop);
		pageScale = Math.min(areaW / pw, areaH / ph);
		pageX = (width - pw * pageScale) / 2f;
		pageY = areaTop + (areaH - ph * pageScale) / 2f;

		int x0 = Math.round(pageX);
		int y0 = Math.round(pageY);
		int x1 = Math.round(pageX + pw * pageScale);
		int y1 = Math.round(pageY + ph * pageScale);
		g.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, WebScreenRenderer.BEZEL);
		g.fill(x0, y0, x1, y1, WebScreenRenderer.BACKGROUND);

		PageViews.View view = view();
		clampScroll();
		g.enableScissor(x0, y0, x1, y1);
		g.pose().pushMatrix();
		g.pose().translate(pageX, pageY);
		g.pose().scale(pageScale, pageScale);
		if (view.state() == PageCache.State.READY) {
			drawPage(g, view.layout(), pw, ph);
		} else {
			drawMessage(g, view, pw, ph);
		}
		g.pose().popMatrix();
		g.disableScissor();

		// Status line: the link under the mouse, or the page title.
		String link = linkAt(mouseX, mouseY);
		String status = link != null ? Urls.unwrapRedirect(link) : view.title();
		if (status != null) {
			String shown = font.plainSubstrByWidth(status, width - 2 * MARGIN);
			g.drawString(font, shown, MARGIN, height - MARGIN - BAR - 13, link != null ? PageLayout.LINK : 0xFFB8B5C8, false);
		}
		super.render(g, mouseX, mouseY, delta);
		g.blit(RenderPipelines.GUI_TEXTURED, RELOAD_ICON, reloadButton.getX() + 2, reloadButton.getY() + 2, 0f, 0f, 16, 16, 16, 16);
	}

	private void drawPage(GuiGraphics g, PageLayout.Result layout, int pw, int ph) {
		float top = scroll;
		float bottom = scroll + ph;
		for (PageLayout.Bar b : layout.bars()) {
			if (b.y() + b.h() > top && b.y() < bottom) {
				fill(g, b.x(), b.y() - top, b.x() + b.w(), b.y() + b.h() - top, b.color());
			}
		}
		for (PageLayout.ImageBox box : layout.images()) {
			if (box.y() + box.h() <= top - ph || box.y() >= bottom + ph) {
				continue;
			}
			ImageCache.Img img = ImageCache.get(box.src());
			if (box.y() + box.h() <= top || box.y() >= bottom) {
				continue;
			}
			float y = box.y() - top;
			if (img.state() == ImageCache.State.READY) {
				g.pose().pushMatrix();
				g.pose().translate(box.x(), y);
				g.pose().scale(box.w() / 256f, box.h() / 256f);
				g.blit(RenderPipelines.GUI_TEXTURED, img.texture(), 0, 0, 0f, 0f, 256, 256, 256, 256, 256, 256);
				g.pose().popMatrix();
			} else {
				fill(g, box.x(), y, box.x() + box.w(), y + box.h(), 0xFF1F1E2C);
				if (img.state() == ImageCache.State.FAILED && !box.alt().isEmpty()) {
					g.pose().pushMatrix();
					g.pose().translate(box.x() + 3, y + 3);
					g.drawString(font, font.plainSubstrByWidth(box.alt(), (int) box.w() - 6), 0, 0, PageLayout.MUTED, false);
					g.pose().popMatrix();
				}
			}
		}
		for (PageLayout.Run r : layout.runs()) {
			if (r.y() >= bottom) {
				break;
			}
			if (r.y() + r.height() <= top) {
				continue;
			}
			g.pose().pushMatrix();
			g.pose().translate(r.x(), r.y() - top + 0.5f * r.scale());
			if (r.scale() != 1f) {
				g.pose().scale(r.scale(), r.scale());
			}
			g.drawString(font, PageViews.text(r), 0, 0, r.color(), false);
			g.pose().popMatrix();
		}
		if (layout.height() > ph) {
			float thumb = Math.max(8, ph * ph / layout.height());
			float y = (ph - thumb) * scroll / (layout.height() - ph);
			fill(g, pw - 2.5f, y, pw - 1f, y + thumb, 0x66FFFFFF);
		}
	}

	private void drawMessage(GuiGraphics g, PageViews.View view, int pw, int ph) {
		String message = view.message() == null ? "" : view.message();
		int color = view.state() == PageCache.State.ERROR ? 0xFFFF9C9C : 0xFFB8B5C8;
		List<FormattedCharSequence> lines = font.split(Component.literal(message), Math.max(20, pw - 12));
		int y = Math.round((ph - lines.size() * 10f) / 2f);
		for (FormattedCharSequence line : lines) {
			g.drawString(font, line, (pw - font.width(line)) / 2, y, color, false);
			y += 10;
		}
	}

	/** Fills a rectangle given in page pixels (which may be fractions; the pose scales them). */
	private static void fill(GuiGraphics g, float x0, float y0, float x1, float y1, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x0, y0);
		g.pose().scale((x1 - x0) / 64f, (y1 - y0) / 64f);
		g.fill(0, 0, 64, 64, color);
		g.pose().popMatrix();
	}
}
