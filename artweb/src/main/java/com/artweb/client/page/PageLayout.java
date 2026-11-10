package com.artweb.client.page;

import com.artweb.client.page.PageModel.Block;
import com.artweb.client.page.PageModel.Kind;
import com.artweb.client.page.PageModel.Para;
import com.artweb.client.page.PageModel.Span;

import java.util.ArrayList;
import java.util.List;

/**
 * Places a {@link PageModel} on a page of a fixed width: wraps text into lines, sizes images and lays out bullets,
 * quote bars and dividers. Positions are in page pixels with y going down. Knows nothing about Minecraft, so the
 * same layout is used on the screen block and in the browser window, and in unit tests.
 */
public final class PageLayout {
	/** Line height of normal text, in page pixels (the font is 9 pixels tall). */
	public static final float LINE = 10f;
	public static final float PAD = 4f;
	public static final float INDENT = 10f;

	public static final int TEXT = 0xFFDCDAE6;
	public static final int HEADING = 0xFFFFFFFF;
	public static final int LINK = 0xFF7FB7FF;
	public static final int CODE = 0xFFFFC88A;
	public static final int MUTED = 0xFFA6A3B4;
	public static final int QUOTE_BAR = 0xFF5A5770;
	public static final int RULE = 0xFF45435A;
	public static final int CODE_BG = 0xFF1E1D2A;

	/** Text width in page pixels at scale 1, for the given style bits. */
	public interface Measure {
		float width(String text, int style);
	}

	/** Size of an image once known (from the page or after loading), or null. */
	public interface ImageSizes {
		int[] size(String src);
	}

	/** A piece of text drawn on one line. {@code y} is the top of the line; {@code scale} multiplies the font. */
	public record Run(float x, float y, float scale, String text, int style, int color, String href) {
		public float height() {
			return LINE * scale;
		}
	}

	/** An image placed at (x, y) with size w × h. */
	public record ImageBox(float x, float y, float w, float h, String src, String alt) {
	}

	/** A filled rectangle: quote bars, dividers and code backgrounds. */
	public record Bar(float x, float y, float w, float h, int color) {
	}

	/** A clickable area. */
	public record Link(float x, float y, float w, float h, String href) {
		public boolean contains(float px, float py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	public record Result(float width, float height, List<Run> runs, List<ImageBox> images, List<Bar> bars, List<Link> links) {
		public String linkAt(float x, float y) {
			for (Link l : links) {
				if (l.contains(x, y)) {
					return l.href();
				}
			}
			return null;
		}
	}

	private final float width;
	private final Measure measure;
	private final ImageSizes sizes;
	private final List<Run> runs = new ArrayList<>();
	private final List<ImageBox> images = new ArrayList<>();
	private final List<Bar> bars = new ArrayList<>();
	private final List<Link> links = new ArrayList<>();
	private float y = PAD;
	private float pendingGap;

	private PageLayout(float width, Measure measure, ImageSizes sizes) {
		this.width = width;
		this.measure = measure;
		this.sizes = sizes;
	}

	public static Result layout(PageModel page, float width, Measure measure, ImageSizes sizes) {
		PageLayout l = new PageLayout(width, measure, sizes);
		for (Block b : page.blocks()) {
			l.block(b);
		}
		return new Result(width, l.y + PAD, List.copyOf(l.runs), List.copyOf(l.images), List.copyOf(l.bars), List.copyOf(l.links));
	}

	/** Text size of a kind of paragraph. Headings shrink on narrow pages so a title doesn't fill a small screen. */
	public static float scaleOf(Kind kind, float pageWidth) {
		float full = switch (kind) {
			case H1 -> 2.0f;
			case H2 -> 1.5f;
			case H3 -> 1.25f;
			case H4 -> 1.1f;
			default -> 1f;
		};
		float room = Math.max(0.25f, Math.min(1f, (pageWidth - 64) / 256f));
		return 1f + (full - 1f) * room;
	}

	private void block(Block b) {
		switch (b) {
			case Para p -> para(p);
			case PageModel.Image img -> image(img);
			case PageModel.Rule r -> {
				gap(4);
				bars.add(new Bar(PAD, y, width - 2 * PAD, 1, RULE));
				y += 1;
				pendingGap = 4;
			}
		}
	}

	private void gap(float g) {
		y += Math.max(g, pendingGap);
		pendingGap = 0;
		if (runs.isEmpty() && images.isEmpty() && bars.isEmpty()) {
			y = PAD; // no gap above the first thing on the page
		}
	}

	private void image(PageModel.Image img) {
		float max = width - 2 * PAD;
		int[] known = sizes == null ? null : sizes.size(img.src());
		float w;
		float h;
		if (known != null && known[0] > 0 && known[1] > 0) {
			w = known[0];
			h = known[1];
			if (img.width() > 0 && img.height() > 0) {
				// The page may ask for a different size than the file has.
				w = img.width();
				h = img.height();
			} else if (img.width() > 0) {
				h = h * img.width() / w;
				w = img.width();
			}
		} else if (img.width() > 0 && img.height() > 0) {
			w = img.width();
			h = img.height();
		} else {
			w = Math.min(max, 120);
			h = w * 9 / 16; // placeholder until the picture arrives
		}
		if (w > max) {
			h = h * max / w;
			w = max;
		}
		gap(3);
		images.add(new ImageBox(PAD, y, w, h, img.src(), img.alt()));
		y += h;
		pendingGap = 3;
	}

	private void para(Para p) {
		float scale = scaleOf(p.kind(), width);
		float lineH = LINE * scale;
		boolean heading = p.kind() == Kind.H1 || p.kind() == Kind.H2 || p.kind() == Kind.H3 || p.kind() == Kind.H4;
		gap(heading ? 6 : p.kind() == Kind.LI ? 1 : 4);

		float left = PAD + p.depth() * INDENT + (p.kind() == Kind.QUOTE ? 4 : 0);
		float right = width - PAD;
		if (right - left < 24) {
			left = Math.max(PAD, right - 24); // deeply nested: keep a usable column
		}
		float top = y;
		int baseColor = switch (p.kind()) {
			case H1, H2, H3, H4 -> HEADING;
			case CAPTION, QUOTE -> MUTED;
			case PRE -> CODE;
			default -> TEXT;
		};
		int extraStyle = heading ? PageModel.BOLD : p.kind() == Kind.CAPTION ? PageModel.ITALIC : 0;

		if (p.bullet() != null) {
			float bw = measure.width(p.bullet(), 0) * scale;
			runs.add(new Run(Math.max(0, left - bw - 3), y, scale, p.bullet(), 0, MUTED, null));
		}

		Line line = new Line(left, right, scale, lineH);
		if (p.kind() == Kind.PRE) {
			for (Span s : p.spans()) {
				String[] parts = s.text().split("\n", -1);
				for (int i = 0; i < parts.length; i++) {
					if (i > 0) {
						line.newLine();
					}
					line.chars(parts[i].replace("\t", "    "), s.style() | extraStyle, color(s, baseColor), s.href());
				}
			}
		} else {
			for (Span s : p.spans()) {
				line.words(s.text(), s.style() | extraStyle, color(s, baseColor), s.href());
			}
		}
		line.finish();

		if (p.kind() == Kind.PRE) {
			bars.add(new Bar(left - 2, top - 2, right - left + 4, y - top + 4, CODE_BG));
		}
		if (p.kind() == Kind.QUOTE) {
			bars.add(new Bar(left - 6, top, 2, y - top, QUOTE_BAR));
		}
		pendingGap = heading ? 3 : p.kind() == Kind.LI ? 1 : 4;
	}

	private static int color(Span s, int base) {
		if (s.href() != null) {
			return LINK;
		}
		if ((s.style() & PageModel.CODE) != 0) {
			return CODE;
		}
		return base;
	}

	/** Fills one line at a time, breaking between words (or inside a word that is wider than the column). */
	private final class Line {
		private final float left;
		private final float right;
		private final float scale;
		private final float lineH;
		private final List<Run> pending = new ArrayList<>();
		private float x;
		private boolean space;

		Line(float left, float right, float scale, float lineH) {
			this.left = left;
			this.right = right;
			this.scale = scale;
			this.lineH = lineH;
			this.x = left;
		}

		void words(String text, int style, int color, String href) {
			int i = 0;
			int n = text.length();
			while (i < n) {
				if (text.charAt(i) == ' ') {
					space = x > left;
					i++;
					continue;
				}
				int end = text.indexOf(' ', i);
				if (end < 0) {
					end = n;
				}
				word(text.substring(i, end), style, color, href);
				i = end;
			}
		}

		private void word(String word, int style, int color, String href) {
			float spaceW = space ? measure.width(" ", style) * scale : 0;
			float w = measure.width(word, style) * scale;
			if (x > left && x + spaceW + w > right) {
				newLine();
				spaceW = 0;
			}
			if (w > right - left) {
				chars(word, style, color, href); // a very long word or address: break it anywhere
				space = false;
				return;
			}
			add((spaceW > 0 ? " " : "") + word, spaceW + w, style, color, href);
			space = false;
		}

		/** Adds text, breaking at any character when the line is full. */
		void chars(String text, int style, int color, String href) {
			int start = 0;
			while (start < text.length()) {
				int end = start;
				float w = 0;
				while (end < text.length()) {
					float cw = measure.width(text.substring(end, end + 1), style) * scale;
					if (x + w + cw > right && (x > left || end > start)) {
						break;
					}
					w += cw;
					end++;
				}
				if (end > start) {
					add(text.substring(start, end), w, style, color, href);
				}
				if (end < text.length()) {
					newLine();
				}
				start = end;
			}
		}

		private void add(String text, float w, int style, int color, String href) {
			Run last = pending.isEmpty() ? null : pending.get(pending.size() - 1);
			if (last != null && last.style() == style && last.color() == color && java.util.Objects.equals(last.href(), href)) {
				pending.set(pending.size() - 1, new Run(last.x(), y, scale, last.text() + text, style, color, href));
			} else {
				pending.add(new Run(x, y, scale, text, style, color, href));
			}
			x += w;
		}

		void newLine() {
			flushLine();
			y += lineH;
			x = left;
			space = false;
		}

		void finish() {
			if (!pending.isEmpty() || x > left) {
				flushLine();
				y += lineH;
			}
		}

		private void flushLine() {
			for (Run r : pending) {
				String text = r.text();
				float x0 = r.x();
				if (text.startsWith(" ") && r.href() != null) {
					// Don't underline or click the space before a link.
					float sw = measure.width(" ", r.style()) * scale;
					x0 += sw;
					text = text.substring(1);
					r = new Run(x0, r.y(), r.scale(), text, r.style(), r.color(), r.href());
				}
				if (text.isEmpty()) {
					continue;
				}
				runs.add(r);
				if (r.href() != null) {
					links.add(new Link(x0, r.y(), measure.width(text, r.style()) * scale, lineH, r.href()));
				}
			}
			pending.clear();
		}
	}
}
