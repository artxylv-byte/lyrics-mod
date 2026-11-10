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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

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
		int y = Math.max(44, height / 2 - 108);
		int row = H + GAP;

		addRenderableWidget(CycleButton.onOffBuilder(config.enabled)
				.create(left, y, W, H, Component.translatable("artlyrics.option.enabled"), (b, v) -> {
					config.enabled = v;
					changed();
				}));
		addRenderableWidget(CycleButton.onOffBuilder(config.topBar)
				.create(right, y, W, H, Component.translatable("artlyrics.option.top_bar"), (b, v) -> {
					config.topBar = v;
					changed();
				}));
		y += row;

		addRenderableWidget(CycleButton.<ArtlyricsConfig.DisplayMode>builder(v -> Component.translatable(v.translationKey()), config.displayMode)
				.withValues(ArtlyricsConfig.DisplayMode.values())
				.withTooltip(v -> net.minecraft.client.gui.components.Tooltip.create(Component.translatable(v.translationKey() + ".tooltip")))
				.create(left, y, W, H, Component.translatable("artlyrics.option.display"), (b, v) -> {
					config.displayMode = v;
					changed();
				}));
		addRenderableWidget(CycleButton.onOffBuilder(config.curved)
				.create(right, y, W, H, Component.translatable("artlyrics.option.curved"), (b, v) -> {
					config.curved = v;
					changed();
				}));
		y += row;

		addRenderableWidget(CycleButton.<ArtlyricsConfig.Animation>builder(v -> Component.translatable(v.translationKey()), config.animation)
				.withValues(ArtlyricsConfig.Animation.values())
				.create(left, y, W, H, Component.translatable("artlyrics.option.animation"), (b, v) -> {
					config.animation = v;
					changed();
				}));
		addRenderableWidget(CycleButton.onOffBuilder(config.shadow)
				.create(right, y, W, H, Component.translatable("artlyrics.option.shadow"), (b, v) -> {
					config.shadow = v;
					changed();
				}));
		y += row;

		addRenderableWidget(CycleButton.<LyricStyle>builder(v -> Component.translatable(v.translationKey()).withColor(v.from), config.style)
				.withValues(LyricStyle.values())
				.create(left, y, W, H, Component.translatable("artlyrics.option.style"), (b, v) -> {
					config.style = v;
					changed();
				}));
		addRenderableWidget(new Slider(right, y, "artlyrics.option.size", 0.5, 2.5, config.size,
				v -> Math.round(v * 100) + "%", v -> config.size = (float) v));
		y += row;

		addRenderableWidget(CycleButton.<ArtlyricsConfig.TextFont>builder(v -> Component.translatable(v.translationKey()), config.font)
				.withValues(ArtlyricsConfig.TextFont.values())
				.create(left, y, W, H, Component.translatable("artlyrics.option.font"), (b, v) -> {
					config.font = v;
					changed();
				}));
		addRenderableWidget(new Slider(right, y, "artlyrics.option.sky_height", 5, 60, config.skyHeight,
				v -> Math.round(v) + "°", v -> config.skyHeight = Math.round(v)));
		y += row;

		addRenderableWidget(new Slider(left, y, "artlyrics.option.offset", -3000, 3000, config.offsetMs,
				v -> (v > 0 ? "+" : "") + Math.round(v / 50.0) * 50 + " ms", v -> config.offsetMs = (int) (Math.round(v / 50.0) * 50)));
		EditBox player = new EditBox(font, right, y, W, H, Component.translatable("artlyrics.option.player"));
		player.setMaxLength(40);
		player.setValue(config.preferredPlayer);
		player.setHint(Component.translatable("artlyrics.option.player.hint"));
		player.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("artlyrics.option.player.tooltip")));
		player.setResponder(v -> {
			config.preferredPlayer = v.strip();
			changed();
		});
		addRenderableWidget(player);
		y += row;

		addRenderableWidget(CycleButton.onOffBuilder(mod.tracker().isDemoActive())
				.withTooltip(v -> net.minecraft.client.gui.components.Tooltip.create(Component.translatable("artlyrics.option.preview.tooltip")))
				.create(left, y, W, H, Component.translatable("artlyrics.option.preview"), (b, v) -> mod.tracker().setDemoActive(v)));
		addRenderableWidget(Button.builder(Component.translatable("artlyrics.option.refresh"), b -> mod.lyrics().refresh())
				.bounds(right, y, W, H)
				.build());
		y += row + GAP;

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(width / 2 - 100, y, 200, H)
				.build());
	}

	private void changed() {
		mod.settingsChanged();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(font, title, width / 2, Math.max(12, height / 2 - 140), 0xFFFFFFFF);
		graphics.drawCenteredString(font, statusLine(), width / 2, Math.max(26, height / 2 - 126), 0xFFB8B4C8);
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
		Component lyricsState = Component.translatable("artlyrics.status." + state.name().toLowerCase(java.util.Locale.ROOT));
		return Component.literal("♪ " + track.displayName() + "  ·  ").append(lyricsState);
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
