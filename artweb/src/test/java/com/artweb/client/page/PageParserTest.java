package com.artweb.client.page;

import com.artweb.client.page.PageModel.Kind;
import com.artweb.client.page.PageModel.Para;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageParserTest {
	private static PageModel parse(String html) {
		return PageParser.parse(Jsoup.parse(html, "https://example.com/dir/page.html"), "https://example.com/dir/page.html");
	}

	private static List<Para> paras(PageModel m) {
		return m.blocks().stream().filter(b -> b instanceof Para).map(b -> (Para) b).toList();
	}

	@Test
	void readsHeadingsParagraphsAndLinks() {
		PageModel m = parse("""
				<html><head><title> Castle guide </title><style>p{color:red}</style><script>var x = 1;</script></head>
				<body><nav hidden>menu</nav><h1>Castles</h1>
				<p>Build   a <b>big</b> wall and <a href="../walls.html">read more</a>.</p>
				<p style="display: none">secret</p></body></html>""");
		assertEquals("Castle guide", m.title());
		List<Para> p = paras(m);
		assertEquals(2, p.size());
		assertEquals(Kind.H1, p.get(0).kind());
		assertEquals("Castles", p.get(0).text());
		assertEquals("Build a big wall and read more.", p.get(1).text());
		PageModel.Span bold = p.get(1).spans().get(1);
		assertEquals("big", bold.text());
		assertEquals(PageModel.BOLD, bold.style());
		PageModel.Span link = p.get(1).spans().stream().filter(s -> s.href() != null).findFirst().orElseThrow();
		assertEquals("https://example.com/walls.html", link.href());
		assertEquals("read more", link.text().strip());
	}

	@Test
	void listsGetBulletsAndNumbers() {
		PageModel m = parse("<ul><li>One</li><li>Two</li></ul><ol start=3><li>Three</li><li>Four</li></ol>");
		List<Para> p = paras(m);
		assertEquals(4, p.size());
		assertEquals("•", p.get(0).bullet());
		assertEquals(1, p.get(0).depth());
		assertEquals("3.", p.get(2).bullet());
		assertEquals("4.", p.get(3).bullet());
		assertEquals("Four", p.get(3).text());
	}

	@Test
	void keepsPreformattedText() {
		PageModel m = parse("<pre>a  b\n  c</pre>");
		Para p = paras(m).get(0);
		assertEquals(Kind.PRE, p.kind());
		assertEquals("a  b\n  c", p.text());
	}

	@Test
	void imagesBecomeBlocksOrAltText() {
		PageModel m = parse("<p>Hi</p><img src='pics/a.png' alt='A' width='100' height='50'>"
				+ "<p>x <img src='icon.svg' alt='star'> y</p><img src='spacer.gif' width=1 height=1>");
		PageModel.Image img = (PageModel.Image) m.blocks().stream().filter(b -> b instanceof PageModel.Image).findFirst().orElseThrow();
		assertEquals("https://example.com/dir/pics/a.png", img.src());
		assertEquals(100, img.width());
		assertEquals(1, m.blocks().stream().filter(b -> b instanceof PageModel.Image).count());
		assertTrue(paras(m).get(1).text().contains("[star]"));
	}

	@Test
	void skipsScriptOnlyPages() {
		PageModel m = parse("<body><div id=app></div><script>render()</script><noscript>Enable JS</noscript></body>");
		assertTrue(m.blocks().isEmpty());
	}

	@Test
	void ignoresJavascriptLinks() {
		Para p = paras(parse("<p><a href='javascript:void(0)'>click</a></p>")).get(0);
		assertNull(p.spans().get(0).href());
	}

	@Test
	void rulesAndQuotes() {
		PageModel m = parse("<p>a</p><hr><blockquote>quoted</blockquote>");
		assertInstanceOf(PageModel.Rule.class, m.blocks().get(1));
		Para q = (Para) m.blocks().get(2);
		assertEquals(Kind.QUOTE, q.kind());
		assertFalse(q.text().isBlank());
	}
}
