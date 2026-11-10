package com.artweb.client.page;

import java.util.List;

/**
 * A web page reduced to what a simple viewer can show: paragraphs (with headings, list items, quotes, code),
 * images and dividing lines. Text keeps bold/italic/code styling and links.
 */
public record PageModel(String url, String title, List<Block> blocks) {
	public static final int BOLD = 1;
	public static final int ITALIC = 2;
	public static final int CODE = 4;
	public static final int UNDERLINE = 8;

	public PageModel {
		blocks = List.copyOf(blocks);
	}

	public sealed interface Block permits Para, Image, Rule {
	}

	public enum Kind {
		P, H1, H2, H3, H4, LI, QUOTE, PRE, CAPTION
	}

	/** A paragraph-like block. {@code depth} is the list/quote nesting; {@code bullet} is "•", "3." or null. */
	public record Para(Kind kind, int depth, String bullet, List<Span> spans) implements Block {
		public Para {
			spans = List.copyOf(spans);
		}

		public String text() {
			StringBuilder sb = new StringBuilder();
			for (Span s : spans) {
				sb.append(s.text());
			}
			return sb.toString();
		}
	}

	/** {@code width}/{@code height} are from the page's attributes, 0 if not given. */
	public record Image(String src, String alt, int width, int height) implements Block {
	}

	public record Rule() implements Block {
	}

	/** A piece of text with one style; {@code href} is the link target or null. */
	public record Span(String text, int style, String href) {
	}
}
