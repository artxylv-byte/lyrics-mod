package com.artweb.client.page;

import com.artweb.client.page.PageModel.Block;
import com.artweb.client.page.PageModel.Kind;
import com.artweb.client.page.PageModel.Span;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads an HTML document into a {@link PageModel}: the readable content, without scripts, styles or layout. */
public final class PageParser {
	private static final Set<String> SKIP = Set.of(
			"head", "script", "style", "noscript", "template", "svg", "canvas", "iframe", "object", "embed",
			"video", "audio", "button", "select", "input", "textarea", "map", "dialog", "math");
	private static final Set<String> BLOCKS = Set.of(
			"p", "div", "section", "article", "main", "header", "footer", "aside", "nav", "figure", "center",
			"address", "details", "summary", "dl", "dt", "dd", "form", "fieldset", "table", "thead", "tbody",
			"tfoot", "tr", "caption", "hgroup", "body", "html", "legend");
	private static final int MAX_BLOCKS = 4000;

	private final List<Block> blocks = new ArrayList<>();
	private List<Span> spans = new ArrayList<>();
	private Kind kind = Kind.P;
	private int depth;
	private String bullet;
	private int style;
	private String href;
	private boolean pre;
	private boolean spaceNeeded;

	private PageParser() {
	}

	public static PageModel parse(Document doc, String url) {
		PageParser p = new PageParser();
		Element body = doc.body();
		p.walk(body != null ? body : doc);
		p.flush();
		String title = doc.title().isBlank() ? Urls.host(url) : doc.title().strip();
		return new PageModel(url, title, p.blocks);
	}

	private void walk(Node node) {
		if (blocks.size() > MAX_BLOCKS) {
			return;
		}
		if (node instanceof TextNode text) {
			addText(pre ? text.getWholeText() : text.text());
			return;
		}
		if (!(node instanceof Element el)) {
			return;
		}
		String tag = el.normalName();
		if (SKIP.contains(tag) || isHidden(el)) {
			return;
		}
		switch (tag) {
			case "br" -> {
				if (pre) {
					addText("\n");
				} else {
					flush();
				}
			}
			case "hr" -> {
				flush();
				blocks.add(new PageModel.Rule());
			}
			case "img" -> image(el);
			case "h1" -> blockWith(el, Kind.H1);
			case "h2" -> blockWith(el, Kind.H2);
			case "h3" -> blockWith(el, Kind.H3);
			case "h4", "h5", "h6" -> blockWith(el, Kind.H4);
			case "figcaption" -> blockWith(el, Kind.CAPTION);
			case "ul", "ol", "menu" -> list(el, tag.equals("ol"));
			case "li" -> {
				flush();
				Kind saved = kind;
				kind = Kind.LI;
				if (bullet == null) {
					bullet = "•";
				}
				children(el);
				flush();
				kind = saved;
			}
			case "blockquote" -> {
				flush();
				Kind saved = kind;
				kind = Kind.QUOTE;
				depth++;
				children(el);
				flush();
				depth--;
				kind = saved;
			}
			case "pre" -> {
				flush();
				Kind saved = kind;
				boolean savedPre = pre;
				kind = Kind.PRE;
				pre = true;
				children(el);
				flush();
				pre = savedPre;
				kind = saved;
			}
			case "td", "th" -> {
				children(el);
				addText("   ");
			}
			case "a" -> {
				String saved = href;
				String target = el.absUrl("href");
				href = target.isEmpty() || target.startsWith("javascript:") ? saved : target;
				children(el);
				href = saved;
			}
			case "b", "strong" -> styled(el, PageModel.BOLD);
			case "i", "em", "cite", "dfn", "var" -> styled(el, PageModel.ITALIC);
			case "code", "kbd", "samp", "tt" -> styled(el, PageModel.CODE);
			case "u", "ins" -> styled(el, PageModel.UNDERLINE);
			default -> {
				if (BLOCKS.contains(tag)) {
					flush();
					children(el);
					flush();
				} else {
					children(el);
				}
			}
		}
	}

	private void children(Element el) {
		for (Node child : el.childNodes()) {
			walk(child);
		}
	}

	private void styled(Element el, int bit) {
		int saved = style;
		style |= bit;
		children(el);
		style = saved;
	}

	private void blockWith(Element el, Kind k) {
		flush();
		Kind saved = kind;
		kind = k;
		children(el);
		flush();
		kind = saved;
	}

	private void list(Element el, boolean ordered) {
		flush();
		depth++;
		int n = 1;
		try {
			n = el.hasAttr("start") ? Integer.parseInt(el.attr("start").strip()) : 1;
		} catch (NumberFormatException ignored) {
			// keep 1
		}
		for (Node child : el.childNodes()) {
			if (child instanceof Element li && li.normalName().equals("li")) {
				bullet = ordered ? (n++) + "." : "•";
			}
			walk(child);
		}
		bullet = null;
		flush();
		depth--;
	}

	private void image(Element el) {
		String src = el.absUrl("src");
		if (src.isEmpty() || src.startsWith("data:")) {
			src = el.absUrl("data-src");
		}
		if (src.isEmpty() && el.hasAttr("srcset")) {
			String first = el.attr("srcset").strip().split("[\\s,]+")[0];
			src = resolve(el, first);
		}
		int w = intAttr(el, "width");
		int h = intAttr(el, "height");
		if (src.isEmpty() || !Urls.isWeb(src) || (w > 0 && w <= 2) || (h > 0 && h <= 2)
				|| src.toLowerCase(Locale.ROOT).contains(".svg")) {
			String alt = el.attr("alt").strip();
			if (!alt.isEmpty() && alt.length() < 80) {
				addText(" [" + alt + "] ");
			}
			return;
		}
		flush();
		blocks.add(new PageModel.Image(src, el.attr("alt").strip(), w, h));
	}

	private static String resolve(Element el, String relative) {
		try {
			return java.net.URI.create(el.baseUri()).resolve(relative).toString();
		} catch (IllegalArgumentException e) {
			return "";
		}
	}

	private static int intAttr(Element el, String name) {
		String v = el.attr(name).strip().replace("px", "");
		try {
			return v.isEmpty() ? 0 : (int) Double.parseDouble(v);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static boolean isHidden(Element el) {
		if (el.hasAttr("hidden") || "true".equals(el.attr("aria-hidden"))) {
			return true;
		}
		String css = el.attr("style").replace(" ", "").toLowerCase(Locale.ROOT);
		if (css.contains("display:none") || css.contains("visibility:hidden")) {
			return true;
		}
		String cls = el.className();
		return cls.contains("mw-editsection") || cls.contains("sr-only") || cls.contains("visually-hidden");
	}

	private void addText(String raw) {
		if (raw.isEmpty()) {
			return;
		}
		String text;
		if (pre) {
			text = raw;
		} else {
			// Collapse runs of whitespace, carrying a pending space across element boundaries.
			boolean leading = Character.isWhitespace(raw.charAt(0));
			boolean trailing = Character.isWhitespace(raw.charAt(raw.length() - 1));
			String core = raw.strip().replaceAll("\\s+", " ");
			if (core.isEmpty()) {
				spaceNeeded = spaceNeeded || !spans.isEmpty();
				return;
			}
			text = core;
			if (!spans.isEmpty()) {
				if (leading) {
					text = " " + text;
				} else if (spaceNeeded) {
					// The space came after the previous piece, so it belongs to it (not to a link that follows).
					Span last = spans.get(spans.size() - 1);
					spans.set(spans.size() - 1, new Span(last.text() + " ", last.style(), last.href()));
				}
			}
			spaceNeeded = trailing;
		}
		Span last = spans.isEmpty() ? null : spans.get(spans.size() - 1);
		if (last != null && last.style() == style && java.util.Objects.equals(last.href(), href)) {
			spans.set(spans.size() - 1, new Span(last.text() + text, style, href));
		} else {
			spans.add(new Span(text, style, href));
		}
	}

	private void flush() {
		spaceNeeded = false;
		if (spans.isEmpty()) {
			return;
		}
		boolean hasText = spans.stream().anyMatch(s -> !s.text().isBlank());
		if (hasText) {
			blocks.add(new PageModel.Para(kind, depth, kind == Kind.LI ? bullet : null, spans));
			if (kind == Kind.LI) {
				bullet = null; // only the first paragraph of a list item gets the bullet
			}
		}
		spans = new ArrayList<>();
	}
}
