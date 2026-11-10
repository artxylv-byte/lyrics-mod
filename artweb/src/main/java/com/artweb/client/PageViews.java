package com.artweb.client;

import com.artweb.client.page.PageCache;
import com.artweb.client.page.PageLayout;
import com.artweb.client.page.PageModel;
import com.artweb.client.page.Urls;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.HashMap;
import java.util.Map;

/**
 * Pages laid out for a given width, measured with Minecraft's font. Shared by the screen blocks and the browser
 * window and redone only when the page, the width or the loaded pictures change.
 */
public final class PageViews {
	/** Page pixels per block. */
	public static final int PX_PER_BLOCK = 64;

	public record View(PageCache.State state, PageLayout.Result layout, String title, String message) {
	}

	private record Key(String url, int width) {
	}

	private record Cached(PageModel page, int imageVersion, PageLayout.Result layout) {
	}

	private static final Map<Key, Cached> LAYOUTS = new HashMap<>();

	private PageViews() {
	}

	/** The page at {@code url} laid out {@code width} page pixels wide (loading it if needed). Render thread. */
	public static View get(String url, int width, Font font) {
		PageCache.Entry entry = PageCache.get(url);
		switch (entry.state()) {
			case LOADING -> {
				return new View(PageCache.State.LOADING, null, Urls.host(url), "Loading " + Urls.host(url) + "…");
			}
			case ERROR -> {
				return new View(PageCache.State.ERROR, null, Urls.host(url), entry.error());
			}
			default -> {
			}
		}
		PageModel page = entry.page();
		Key key = new Key(url, width);
		Cached c = LAYOUTS.get(key);
		int version = ImageCache.version();
		if (c == null || c.page != page || c.imageVersion != version) {
			if (LAYOUTS.size() > 48) {
				LAYOUTS.clear();
			}
			PageLayout.Result layout = PageLayout.layout(page, width, measure(font), ImageCache::size);
			c = new Cached(page, version, layout);
			LAYOUTS.put(key, c);
		}
		return new View(PageCache.State.READY, c.layout, page.title(), null);
	}

	public static PageLayout.Measure measure(Font font) {
		return (text, style) -> font.width(Component.literal(text).withStyle(style(style, null)));
	}

	public static Style style(int bits, String href) {
		return Style.EMPTY
				.withBold((bits & PageModel.BOLD) != 0)
				.withItalic((bits & PageModel.ITALIC) != 0)
				.withUnderlined((bits & PageModel.UNDERLINE) != 0 || href != null);
	}

	public static FormattedCharSequence text(PageLayout.Run run) {
		return Component.literal(run.text()).withStyle(style(run.style(), run.href())).getVisualOrderText();
	}
}
