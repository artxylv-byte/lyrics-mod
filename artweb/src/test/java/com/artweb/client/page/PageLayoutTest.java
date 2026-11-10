package com.artweb.client.page;

import com.artweb.client.page.PageModel.Kind;
import com.artweb.client.page.PageModel.Para;
import com.artweb.client.page.PageModel.Span;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageLayoutTest {
	/** Every character is 6 pixels wide, bold ones 7. */
	private static final PageLayout.Measure MONO = (text, style) -> text.length() * ((style & PageModel.BOLD) != 0 ? 7 : 6);

	private static PageModel page(PageModel.Block... blocks) {
		return new PageModel("https://example.com", "t", List.of(blocks));
	}

	private static Para p(String text) {
		return new Para(Kind.P, 0, null, List.of(new Span(text, 0, null)));
	}

	@Test
	void wrapsAtWordsWithinTheWidth() {
		// 100 px wide, 4 px padding each side: 92 px = 15 characters.
		PageLayout.Result r = PageLayout.layout(page(p("aaaa bbbb cccc dddd eeee")), 100, MONO, null);
		List<String> lines = r.runs().stream().map(PageLayout.Run::text).toList();
		assertEquals(List.of("aaaa bbbb cccc", "dddd eeee"), lines);
		for (PageLayout.Run run : r.runs()) {
			assertTrue(run.x() + MONO.width(run.text(), 0) <= 96.001f, "fits: " + run.text());
		}
		assertEquals(PageLayout.PAD + PageLayout.LINE, r.runs().get(1).y());
	}

	@Test
	void breaksVeryLongWords() {
		PageLayout.Result r = PageLayout.layout(page(p("x".repeat(40))), 100, MONO, null);
		assertEquals(3, r.runs().size());
		assertEquals(15, r.runs().get(0).text().length());
	}

	@Test
	void linksAreClickable() {
		Para para = new Para(Kind.P, 0, null, List.of(new Span("see ", 0, null), new Span("this page", 0, "https://example.com/x")));
		PageLayout.Result r = PageLayout.layout(page(para), 300, MONO, null);
		PageLayout.Run link = r.runs().stream().filter(x -> x.href() != null).findFirst().orElseThrow();
		assertEquals(PageLayout.LINK, link.color());
		assertEquals("https://example.com/x", r.linkAt(link.x() + 2, link.y() + 2));
		assertNull(r.linkAt(PageLayout.PAD + 1, link.y() + 2));
	}

	@Test
	void headingsAreBiggerAndBulletsSitLeft() {
		Para h = new Para(Kind.H1, 0, null, List.of(new Span("Title", 0, null)));
		Para li = new Para(Kind.LI, 1, "•", List.of(new Span("item", 0, null)));
		PageLayout.Result r = PageLayout.layout(page(h, li), 400, MONO, null);
		assertEquals(2f, r.runs().get(0).scale());
		// On a small screen (2 blocks wide) the title is only a little bigger than the text.
		assertEquals(1.25f, PageLayout.scaleOf(Kind.H1, 128), 0.001f);
		assertEquals(1f, PageLayout.scaleOf(Kind.P, 128), 0.001f);
		assertEquals(PageModel.BOLD, r.runs().get(0).style() & PageModel.BOLD);
		PageLayout.Run bullet = r.runs().get(1);
		PageLayout.Run item = r.runs().get(2);
		assertEquals("•", bullet.text());
		assertTrue(bullet.x() < item.x());
		assertEquals(PageLayout.PAD + PageLayout.INDENT, item.x());
	}

	@Test
	void imagesFitTheWidth() {
		PageLayout.Result r = PageLayout.layout(page(new PageModel.Image("https://example.com/a.png", "", 0, 0)), 100, MONO,
				src -> new int[] {400, 200});
		PageLayout.ImageBox box = r.images().get(0);
		assertEquals(92f, box.w(), 0.01f);
		assertEquals(46f, box.h(), 0.01f);
		assertNotNull(box.src());
		assertTrue(r.height() >= box.y() + box.h());
	}

	@Test
	void preformattedKeepsLines() {
		Para pre = new Para(Kind.PRE, 0, null, List.of(new Span("a b\n  c", 0, null)));
		PageLayout.Result r = PageLayout.layout(page(pre), 300, MONO, null);
		assertEquals(List.of("a b", "  c"), r.runs().stream().map(PageLayout.Run::text).toList());
		assertEquals(1, r.bars().size()); // code background
	}
}
