package com.artlyrics.screen;

import com.artlyrics.Artlyrics;
import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.config.LyricStyle;
import com.artlyrics.lyrics.LyricsManager;
import com.artlyrics.nowplaying.NowPlaying;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;

/** Artlyrics settings. Opened with the keybind (default K) or from Mod Menu. */
public final class ArtlyricsScreen extends Screen {
	private static final int W = 150;
	private static final int H = 20;
	private static final int GAP = 4;

	private final @Nullable Screen parent;
	private final Artlyrics mod;
	private final ArtlyricsConfig config;

	public ArtlyricsScreen(@Nullable Screen parent) {
		super(Component.translatable("artlyrics.screen.title"));
		this.parent = parent;
		this.mod = Artlyrics.get();
		this.config = mod.config();
	}

	@Override
	protected void init() {
		int left = width / 2 - W - GAP / 2;
		int right = width / 2 + GAP / 2;
		int y = Math.max(40, height / 2 - 100);
		int row = H + GAP;

		toggle(left, y, "artlyrics.option.enabled", config.enabled, v -> config.enabled = v);
		toggle(right, y, "artlyrics.option.song_card", config.songCard, v -> config.songCard = v);
		y += row;

		choice(left, y, "artlyrics.option.mode", ArtlyricsConfig.DisplayMode.values(), config.displayMode,
				v -> Component.translatable(v.translationKey()), v -> config.displayMode = v, v -> v.translationKey() + ".tooltip");
		choice(right, y, "artlyrics.option.card_corner", ArtlyricsConfig.CardCorner.values(), config.cardCorner,
				v -> Component.translatable(v.translationKey()), v -> config.cardCorner = v, null);
		y += row;

		choice(left, y, "artlyrics.option.animation", ArtlyricsConfig.Animation.values(), config.animation,
				v -> Component.translatable(v.translationKey()), v -> config.animation = v, v -> v.translationKey() + ".tooltip");
		choice(right, y, "artlyrics.option.effect", ArtlyricsConfig.TextEffect.values(), config.textEffect,
				v -> Component.translatable(v.translationKey()), v -> config.textEffect = v, null);
		y += row;

		choice(left, y, "artlyrics.option.palette", LyricStyle.values(), config.style,
				v -> Component.translatable(v.translationKey()).withColor(v.from), v -> config.style = v, null);
		addRenderableWidget(new Slider(right, y, "artlyrics.option.size", 0.5, 2.5, config.size,
				v -> Math.round(v * 100) + "%", v -> config.size = (float) v));
		y += row;

		choice(left, y, "artlyrics.option.font", ArtlyricsConfig.TextFont.values(), config.font,
				v -> Component.translatable(v.translationKey()), v -> config.font = v, null);
		addRenderableWidget(new Slider(right, y, "artlyrics.option.sky_height", 5, 60, config.skyHeight,
				v -> Math.round(v) + "°", v -> config.skyHeight = Math.round(v)));
		y += row;

		addRenderableWidget(new Slider(left, y, "artlyrics.option.offset", -3000, 3000, config.offsetMs,
				v -> (v > 0 ? "+" : "") + Math.round(v / 50.0) * 50 + " ms", v -> config.offsetMs = (int) (Math.round(v / 50.0) * 50)));
		EditBox player = new EditBox(font, right, y, W, H, Component.translatable("artlyrics.option.player"));
		player.setMaxLength(40);
		player.setValue(config.preferredPlayer);
		player.setHint(Component.translatable("artlyrics.option.player.hint"));
		player.setTooltip(Tooltip.create(Component.translatable("artlyrics.option.player.tooltip")));
		player.setResponder(v -> {
			config.preferredPlayer = v.strip();
			changed();
		});
		addRenderableWidget(player);
		y += row;

		toggle(left, y, "artlyrics.option.arc", config.arc, v -> config.arc = v);
		addRenderableWidget(CycleButton.onOffBuilder(mod.tracker().isDemoActive())
				.withTooltip(v -> Tooltip.create(Component.translatable("artlyrics.option.demo.tooltip")))
				.create(right, y, W, H, Component.translatable("artlyrics.option.demo"), (b, v) -> mod.tracker().setDemoActive(v)));
		y += row + GAP;

		addRenderableWidget(Button.builder(Component.translatable("artlyrics.option.refresh"), b -> mod.lyrics().refresh())
				.bounds(left, y, W, H)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(right, y, W, H)
				.build());
	}

	private void toggle(int x, int y, String key, boolean value, Consumer<Boolean> apply) {
		addRenderableWidget(CycleButton.onOffBuilder(value).create(x, y, W, H, Component.translatable(key), (b, v) -> {
			apply.accept(v);
			changed();
		}));
	}

	private <T> void choice(int x, int y, String key, T[] values, T value, Function<T, Component> label, Consumer<T> apply,
							@Nullable Function<T, String> tooltipKey) {
		CycleButton.Builder<T> builder = CycleButton.<T>builder(label, value).withValues(values);
		if (tooltipKey != null) {
			builder = builder.withTooltip(v -> Tooltip.create(Component.translatable(tooltipKey.apply(v))));
		}
		addRenderableWidget(builder.create(x, y, W, H, Component.translatable(key), (b, v) -> {
			apply.accept(v);
			changed();
		}));
	}

	private void changed() {
		mod.settingsChanged();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		int top = Math.max(40, height / 2 - 100);
		graphics.drawCenteredString(font, title, width / 2, top - 30, 0xFFFFFFFF);
		graphics.drawCenteredString(font, statusLine(), width / 2, top - 16, 0xFFB8B4C8);
	}

	private Component statusLine() {
		NowPlaying track = mod.tracker().track();
		String problem = mod.tracker().problem();
		if (track == null) {
			return problem != null
					? Component.literal(problem).withColor(0xFF8080)
					: Component.translatable("artlyrics.status.nothing_playing");
		}
		LyricsManager.State state = mod.lyrics().state();
		Component lyricsState = Component.translatable("artlyrics.status." + state.name().toLowerCase(Locale.ROOT));
		return Component.literal("♫ " + track.displayName() + "  ·  ").append(lyricsState);
	}

	@Override
	public void onClose() {
		config.save();
		minecraft.setScreen(parent);
	}

	/** A slider over [min, max] that shows its value next to the label. */
	private final class Slider extends AbstractSliderButton {
		private final String key;
		private final double min;
		private final double max;
		private final DoubleFunction<String> format;
		private final DoubleConsumer apply;

		Slider(int x, int y, String key, double min, double max, double value, DoubleFunction<String> format, DoubleConsumer apply) {
			super(x, y, W, H, Component.empty(), (Mth.clamp(value, min, max) - min) / (max - min));
			this.key = key;
			this.min = min;
			this.max = max;
			this.format = format;
			this.apply = apply;
			updateMessage();
		}

		private double actual() {
			return min + (max - min) * value;
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable(key).append(": " + format.apply(actual())));
		}

		@Override
		protected void applyValue() {
			apply.accept(actual());
			changed();
		}
	}
}
