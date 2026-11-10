package com.artlyrics.render;

import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.LyricStyle;
import com.artlyrics.lyrics.LyricLine;
import com.artlyrics.lyrics.LyricWord;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each word of a line goes, in font pixels relative to the center of the text block.
 * Long lines wrap into several rows.
 */
final class LineLayout {
	static final float ROW_HEIGHT = 11f;
	private static final FontDescription SMOOTH_FONT = new FontDescription.Resource(Identifier.withDefaultNamespace("uniform"));

	/** One word's placement. {@code xCenter}/{@code y} are the word's center; {@code rowHalfWidth} is for the arc. */
	record WordBox(int index, Component text, float width, float xCenter, float y, float rowHalfWidth) {
	}

	final List<WordBox> words;
	final int revision;

	private LineLayout(List<WordBox> words, int revision) {
		this.words = words;
		this.revision = revision;
	}

	static Style textStyle(ArtlyricsConfig config) {
		LyricStyle look = config.style;
		Style style = Style.EMPTY.withBold(look.bold).withItalic(look.italic);
		if (config.font == ArtlyricsConfig.TextFont.SMOOTH) {
			style = style.withFont(SMOOTH_FONT);
		}
		return style;
	}

	static LineLayout build(LyricLine line, Font font, ArtlyricsConfig config, float maxRowWidth, int revision) {
		Style style = textStyle(config);
		List<LyricWord> lineWords = line.words();

		// Measure and wrap greedily.
		List<List<Integer>> rows = new ArrayList<>();
		List<float[]> rowOffsets = new ArrayList<>();
		float[] fullWidths = new float[lineWords.size()];
		float[] trimWidths = new float[lineWords.size()];
		Component[] texts = new Component[lineWords.size()];

		List<Integer> row = new ArrayList<>();
		List<Float> offsets = new ArrayList<>();
		float x = 0;
		for (int i = 0; i < lineWords.size(); i++) {
			LyricWord w = lineWords.get(i);
			Component trimmed = Component.literal(w.trimmed()).withStyle(style);
			texts[i] = trimmed;
			trimWidths[i] = font.width(trimmed);
			fullWidths[i] = font.width(Component.literal(w.text()).withStyle(style));
			if (!row.isEmpty() && x + trimWidths[i] > maxRowWidth) {
				rows.add(row);
				rowOffsets.add(toArray(offsets));
				row = new ArrayList<>();
				offsets = new ArrayList<>();
				x = 0;
			}
			row.add(i);
			offsets.add(x);
			x += fullWidths[i];
		}
		if (!row.isEmpty()) {
			rows.add(row);
			rowOffsets.add(toArray(offsets));
		}

		List<WordBox> boxes = new ArrayList<>(lineWords.size());
		float firstRowY = -(rows.size() - 1) / 2f * ROW_HEIGHT;
		for (int r = 0; r < rows.size(); r++) {
			List<Integer> indices = rows.get(r);
			float[] offs = rowOffsets.get(r);
			int last = indices.get(indices.size() - 1);
			float rowWidth = offs[offs.length - 1] + trimWidths[last];
			float y = firstRowY + r * ROW_HEIGHT;
			for (int j = 0; j < indices.size(); j++) {
				int i = indices.get(j);
				float xCenter = offs[j] + trimWidths[i] / 2f - rowWidth / 2f;
				boxes.add(new WordBox(i, texts[i], trimWidths[i], xCenter, y, Math.max(1f, rowWidth / 2f)));
			}
		}
		return new LineLayout(boxes, revision);
	}

	private static float[] toArray(List<Float> list) {
		float[] a = new float[list.size()];
		for (int i = 0; i < a.length; i++) {
			a[i] = list.get(i);
		}
		return a;
	}
}
