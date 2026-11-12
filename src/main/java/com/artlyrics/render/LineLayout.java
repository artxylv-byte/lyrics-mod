package com.artlyrics.render;

import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.lyrics.LyricLine;
import com.artlyrics.lyrics.LyricWord;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where each word (and each letter) of a line goes, in font pixels relative to the center of the text block.
 * Long lines wrap into several rows.
 */
final class LineLayout {
	static final float ROW_HEIGHT = 11f;

	/**
	 * One word's placement. {@code xCenter}/{@code y} are the word's center; {@code rowHalfWidth} is for the arc.
	 * {@code letters}/{@code letterX} are the word's letters and their offsets from the word's left edge.
	 */
	record WordBox(int index, String raw, Component text, float width, float xCenter, float y, float rowHalfWidth,
				   Component[] letters, float[] letterX) {
	}

	final List<WordBox> words;
	final Style style;
	final int revision;
	/** Number of rows after wrapping. */
	final int rows;

	private LineLayout(List<WordBox> words, Style style, int revision, int rows) {
		this.words = words;
		this.style = style;
		this.revision = revision;
		this.rows = rows;
	}

	/** Height of the text block in font pixels. */
	float height() {
		return Math.max(1, rows) * ROW_HEIGHT;
	}

	static String display(String word, ArtlyricsConfig config) {
		return config.uppercase ? word.toUpperCase(Locale.ROOT) : word;
	}

	static LineLayout build(LyricLine line, Font font, ArtlyricsConfig config, float maxRowWidth, int revision) {
		Style style = Fonts.lyricStyle(config, false);
		List<LyricWord> lineWords = line.words();
		int n = lineWords.size();

		String[] raws = new String[n];
		Component[] texts = new Component[n];
		float[] fullWidths = new float[n];
		float[] trimWidths = new float[n];
		for (int i = 0; i < n; i++) {
			LyricWord w = lineWords.get(i);
			raws[i] = display(w.trimmed(), config);
			texts[i] = Component.literal(raws[i]).withStyle(style);
			trimWidths[i] = width(font, texts[i]);
			fullWidths[i] = width(font, Component.literal(display(w.text(), config)).withStyle(style));
		}

		// Wrap greedily into rows.
		List<int[]> rows = new ArrayList<>(); // {firstIndex, lastIndex}
		List<float[]> rowOffsets = new ArrayList<>();
		int rowStart = 0;
		List<Float> offsets = new ArrayList<>();
		float x = 0;
		for (int i = 0; i < n; i++) {
			if (i > rowStart && x + trimWidths[i] > maxRowWidth) {
				rows.add(new int[] {rowStart, i - 1});
				rowOffsets.add(toArray(offsets));
				rowStart = i;
				offsets.clear();
				x = 0;
			}
			offsets.add(x);
			x += fullWidths[i];
		}
		if (n > 0) {
			rows.add(new int[] {rowStart, n - 1});
			rowOffsets.add(toArray(offsets));
		}

		List<WordBox> boxes = new ArrayList<>(n);
		float firstRowY = -(rows.size() - 1) / 2f * ROW_HEIGHT;
		for (int r = 0; r < rows.size(); r++) {
			int first = rows.get(r)[0];
			int last = rows.get(r)[1];
			float[] offs = rowOffsets.get(r);
			float rowWidth = offs[offs.length - 1] + trimWidths[last];
			float y = firstRowY + r * ROW_HEIGHT;
			for (int i = first; i <= last; i++) {
				float xCenter = offs[i - first] + trimWidths[i] / 2f - rowWidth / 2f;
				int[] cps = raws[i].codePoints().toArray();
				Component[] letters = new Component[cps.length];
				float[] letterX = new float[cps.length];
				StringBuilder prefix = new StringBuilder();
				for (int c = 0; c < cps.length; c++) {
					letterX[c] = width(font, Component.literal(prefix.toString()).withStyle(style));
					String letter = new String(cps, c, 1);
					letters[c] = Component.literal(letter).withStyle(style);
					prefix.append(letter);
				}
				boxes.add(new WordBox(i, raws[i], texts[i], trimWidths[i], xCenter, y, Math.max(1f, rowWidth / 2f), letters, letterX));
			}
		}
		return new LineLayout(boxes, style, revision, rows.size());
	}

	/** Exact width in font pixels (not rounded up), so letters drawn one by one land where the whole word would. */
	static float width(Font font, Component text) {
		return font.getSplitter().stringWidth(text);
	}

	private static float[] toArray(List<Float> list) {
		float[] a = new float[list.size()];
		for (int i = 0; i < a.length; i++) {
			a[i] = list.get(i);
		}
		return a;
	}
}
